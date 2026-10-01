pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

rootProject.name = "ipv-gestion-costos"

include("core:domain")
project(":core:domain").projectDir = file("core/domain")

include("server:app")
project(":server:app").projectDir = file("server/app")

include("tools:seed")
project(":tools:seed").projectDir = file("tools/seed")
