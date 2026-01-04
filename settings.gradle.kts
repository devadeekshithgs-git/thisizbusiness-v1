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
        // Supabase repository
        maven { url = uri("https://repo1.maven.org/maven2/") }
    }
}

rootProject.name = "KiranaFlow"
include(":app")
