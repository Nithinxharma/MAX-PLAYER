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
        maven { url = java.net.URI.create("https://jitpack.io") }
        maven { url = java.net.URI.create("https://maven.pkg.jetbrains.space/public/p/compose/dev") }
    }
}

rootProject.name = "MAX STREAM"
include(":app")
