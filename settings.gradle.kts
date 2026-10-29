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

rootProject.name = "Nido"

include(":app")
include(":core:database")
include(":core:network")
include(":core:common")
include(":feature:music")
include(":feature:discover")
include(":feature:clipshare")
include(":feature:settings")
