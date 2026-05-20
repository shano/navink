pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

// Mudita MMD e-ink component library — consumed as composite build from local clone.
// Clone the MMD repo to ../MMD (sibling of this project) before building.
// Repo: ask Shane for access or check CalmCast references.
includeBuild("../MMD") {
    dependencySubstitution {
        substitute(module("com.mudita:MMD")).using(project(":mmd-core"))
    }
}

rootProject.name = "navink"
include(":app")
