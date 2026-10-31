package app.passage.core.git.sync

import app.passage.core.git.model.ConflictStrategy
import app.passage.core.git.model.GitAuth
import app.passage.core.git.model.GitException
import app.passage.core.git.model.GitRepoStatus
import app.passage.core.git.model.SyncResult
import app.passage.core.git.ssh.CustomSshSessionFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.CheckoutCommand
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.MergeCommand
import org.eclipse.jgit.api.TransportConfigCallback
import org.eclipse.jgit.lib.BranchTrackingStatus
import org.eclipse.jgit.lib.ProgressMonitor
import org.eclipse.jgit.transport.CredentialsProvider
import org.eclipse.jgit.transport.SshTransport
import org.eclipse.jgit.transport.Transport
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import java.io.File

class GitSyncEngine {

    /**
     * Clones a remote git repository into [destinationDir].
     */
    suspend fun clone(
        remoteUrl: String,
        destinationDir: File,
        auth: GitAuth,
        onProgress: ((task: String, percent: Float) -> Unit)? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (destinationDir.exists() && destinationDir.list()?.isNotEmpty() == true) {
                throw GitException.OperationFailed("Destination directory is not empty: ${destinationDir.absolutePath}")
            }

            val cloneCommand = Git.cloneRepository()
                .setURI(remoteUrl)
                .setDirectory(destinationDir)
                .setProgressMonitor(createProgressMonitor(onProgress))

            configureAuthentication(cloneCommand, auth)
            val git = cloneCommand.call()
            git.close()
        }
    }

    /**
     * Performs a Pull (fetch + merge fast-forward) from the remote tracking branch.
     */
    suspend fun pull(
        repoDir: File,
        auth: GitAuth
    ): SyncResult = withContext(Dispatchers.IO) {
        try {
            Git.open(repoDir).use { git ->
                val pullCmd = git.pull()
                    .setFastForward(MergeCommand.FastForwardMode.FF)

                configureAuthentication(pullCmd, auth)
                val pullResult = pullCmd.call()

                if (pullResult.isSuccessful) {
                    val mergeResult = pullResult.mergeResult
                    val commitId = mergeResult?.newHead?.name
                    SyncResult.Success(commitId, "Repository updated successfully")
                } else {
                    val conflicts = pullResult.mergeResult?.conflicts?.keys?.toList() ?: emptyList()
                    if (conflicts.isNotEmpty()) {
                        SyncResult.HasConflicts(conflicts)
                    } else {
                        SyncResult.Failure(
                            GitException.OperationFailed(
                                "Pull failed with status: ${pullResult.mergeResult?.mergeStatus}"
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            SyncResult.Failure(mapException(e))
        }
    }

    /**
     * Stages changes, creates a commit, and pushes to remote repository.
     */
    suspend fun commitAndPush(
        repoDir: File,
        message: String,
        authorName: String = "Passage Android",
        authorEmail: String = "passage@local",
        auth: GitAuth,
        filesToStage: List<String>? = null
    ): SyncResult = withContext(Dispatchers.IO) {
        try {
            Git.open(repoDir).use { git ->
                // 1. Stage files
                val addCmd = git.add()
                if (filesToStage.isNullOrEmpty()) {
                    addCmd.addFilepattern(".")
                } else {
                    filesToStage.forEach { addCmd.addFilepattern(it) }
                }
                addCmd.call()

                // Also stage deleted files
                git.add().setUpdate(true).addFilepattern(".").call()

                // 2. Commit
                val commit = git.commit()
                    .setMessage(message)
                    .setAuthor(authorName, authorEmail)
                    .call()

                // 3. Push
                val pushCmd = git.push()
                configureAuthentication(pushCmd, auth)
                val pushResults = pushCmd.call()

                val pushSuccess = pushResults.all { result ->
                    result.remoteUpdates.all {
                        it.status == org.eclipse.jgit.transport.RemoteRefUpdate.Status.OK ||
                                it.status == org.eclipse.jgit.transport.RemoteRefUpdate.Status.UP_TO_DATE
                    }
                }

                if (pushSuccess) {
                    SyncResult.Success(commit.name, "Committed and pushed: ${commit.shortMessage}")
                } else {
                    val errDetails = pushResults.flatMap { it.remoteUpdates }
                        .map { "${it.remoteName}: ${it.status} (${it.message ?: "no message"})" }
                        .joinToString("; ")
                    SyncResult.Failure(GitException.OperationFailed("Push rejected: $errDetails"))
                }
            }
        } catch (e: Exception) {
            SyncResult.Failure(mapException(e))
        }
    }

    /**
     * Resolves a binary merge conflict on an encrypted .age secret file.
     */
    suspend fun resolveConflict(
        repoDir: File,
        relativePath: String,
        strategy: ConflictStrategy
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            Git.open(repoDir).use { git ->
                when (strategy) {
                    ConflictStrategy.THEIRS -> {
                        git.checkout()
                            .setStage(CheckoutCommand.Stage.THEIRS)
                            .addPath(relativePath)
                            .call()
                        git.add().addFilepattern(relativePath).call()
                    }

                    ConflictStrategy.OURS -> {
                        git.checkout()
                            .setStage(CheckoutCommand.Stage.OURS)
                            .addPath(relativePath)
                            .call()
                        git.add().addFilepattern(relativePath).call()
                    }

                    ConflictStrategy.KEEP_BOTH -> {
                        // 1. Extract ours to temporary conflict file
                        val conflictFile = File(
                            repoDir,
                            relativePath.replace(".age", ".conflict.age")
                        )
                        val originalFile = File(repoDir, relativePath)
                        originalFile.copyTo(conflictFile, overwrite = true)

                        // 2. Checkout theirs for the primary file
                        git.checkout()
                            .setStage(CheckoutCommand.Stage.THEIRS)
                            .addPath(relativePath)
                            .call()

                        // 3. Stage both files
                        git.add().addFilepattern(relativePath).call()
                        git.add().addFilepattern(conflictFile.relativeTo(repoDir).path).call()
                    }
                }
            }
            Unit
        }
    }

    /**
     * Retrieves the repository status (uncommitted files, ahead/behind counts).
     */
    suspend fun getStatus(repoDir: File): Result<GitRepoStatus> = withContext(Dispatchers.IO) {
        runCatching {
            Git.open(repoDir).use { git ->
                val repo = git.repository
                val status = git.status().call()
                val branch = repo.branch ?: "unknown"

                val trackingStatus = BranchTrackingStatus.of(repo, branch)
                val ahead = trackingStatus?.aheadCount ?: 0
                val behind = trackingStatus?.behindCount ?: 0

                val uncommitted = mutableListOf<String>()
                uncommitted.addAll(status.uncommittedChanges)
                uncommitted.addAll(status.untracked)

                GitRepoStatus(
                    isClean = status.isClean,
                    hasUncommittedChanges = status.hasUncommittedChanges(),
                    uncommittedFiles = uncommitted.distinct(),
                    aheadCount = ahead,
                    behindCount = behind,
                    currentBranch = branch
                )
            }
        }
    }

    // Helper: configure Transport and Credentials for any JGit command
    private fun <C : org.eclipse.jgit.api.TransportCommand<C, *>> configureAuthentication(
        command: C,
        auth: GitAuth
    ) {
        when (auth) {
            is GitAuth.SshKey -> {
                val sessionFactory = CustomSshSessionFactory(
                    privateKeyContent = auth.privateKeyContent,
                    passphrase = auth.passphrase
                )
                command.setTransportConfigCallback(TransportConfigCallback { transport ->
                    if (transport is SshTransport) {
                        transport.sshSessionFactory = sessionFactory
                    }
                })
            }

            is GitAuth.SshKeyFile -> {
                val content = auth.keyFile.readText(Charsets.UTF_8)
                val sessionFactory = CustomSshSessionFactory(
                    privateKeyContent = content,
                    passphrase = auth.passphrase
                )
                command.setTransportConfigCallback(TransportConfigCallback { transport ->
                    if (transport is SshTransport) {
                        transport.sshSessionFactory = sessionFactory
                    }
                })
            }

            is GitAuth.HttpToken -> {
                val credentialsProvider: CredentialsProvider =
                    UsernamePasswordCredentialsProvider(auth.username, auth.token)
                command.setCredentialsProvider(credentialsProvider)
            }

            is GitAuth.None -> {
                // Anonymous access
            }
        }
    }

    private fun createProgressMonitor(callback: ((String, Float) -> Unit)?): ProgressMonitor {
        return object : ProgressMonitor {
            private var currentTask = ""
            private var totalWork = 0
            private var completedWork = 0

            override fun start(totalTasks: Int) {}

            override fun beginTask(title: String, totalWork: Int) {
                this.currentTask = title
                this.totalWork = totalWork
                this.completedWork = 0
                callback?.invoke(currentTask, 0f)
            }

            override fun update(completed: Int) {
                this.completedWork += completed
                val progress = if (totalWork > 0) completedWork.toFloat() / totalWork else 0f
                callback?.invoke(currentTask, progress)
            }

            override fun endTask() {
                callback?.invoke(currentTask, 1f)
            }

            override fun isCancelled(): Boolean = false
            override fun showDuration(enabled: Boolean) {}
        }
    }

    private fun mapException(e: Exception): GitException {
        val msg = e.message ?: "Unknown git error"
        return when {
            msg.contains("Auth fail", ignoreCase = true) || msg.contains("not authorized", ignoreCase = true) ->
                GitException.AuthenticationFailed("Git authentication failed: check credentials/keys", e)

            msg.contains("Connection refused", ignoreCase = true) || msg.contains("UnknownHostException", ignoreCase = true) ->
                GitException.NetworkError("Network error: unable to connect to remote host", e)

            else -> GitException.OperationFailed(msg, e)
        }
    }
}
