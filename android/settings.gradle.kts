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

rootProject.name = "ipv-gc-android"

includeBuild("../core") {
    dependencySubstitution {
        substitute(module("cu.ipvgc:domain")).using(project(":domain"))
    }
}

include(":app")
include(":core:common")
include(":core:network")
include(":core:data")
include(":core:security")
include(":core:designsystem")
include(":feature:auth")
include(":feature:home")
include(":feature:catalog")
include(":feature:ipv")
include(":feature:costing")
include(":feature:control")
include(":feature:inventory")
include(":feature:rates")
include(":feature:license")
include(":feature:sync")
include(":feature:settings")
