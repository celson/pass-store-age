pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = java.net.URI("https://repo.eclipse.org/content/groups/releases/") }
    }
}

rootProject.name = "Passage"

// Main Application
include(":app")

// Core Modules
include(":core:crypto")
include(":core:git")
include(":core:security")
include(":core:domain")
include(":core:database")

// Feature Modules
include(":feature:vault")
include(":feature:secret-detail")
include(":feature:secret-editor")
include(":feature:sync")
include(":feature:autofill")
