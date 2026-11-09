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
        // USB serial library (reads OBD cables): published through JitPack, only this one group is taken from it
        maven {
            url = uri("https://jitpack.io")
            content { includeGroup("com.github.mik3y") }
        }
    }
}
rootProject.name = "Obdy"
include(":app")
