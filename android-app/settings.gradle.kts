pluginManagement {
    repositories {
        google()
        // Google-hosted Maven Central mirror avoids intermittent Maven Central 429 throttling.
        maven { url = uri("https://maven-central.storage-download.googleapis.com/maven2/") }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositories {
        google()
        // Keep mirror first; Maven Central remains as fallback.
        maven { url = uri("https://maven-central.storage-download.googleapis.com/maven2/") }
        mavenCentral()
    }
}
rootProject.name = "Drishyam"
include(":app")
