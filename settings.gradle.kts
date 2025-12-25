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
        // Needed for dependencies published under com.github.* (e.g., uCrop).
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "KiranaFlow"
include(":app")
