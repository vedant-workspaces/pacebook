val coreOnly = System.getenv("PACELOG_CORE_ONLY") != null

pluginManagement {
    repositories {
        if (System.getenv("PACELOG_CORE_ONLY") == null) google()
        mavenCentral()
        gradlePluginPortal()
    }
    // Versions are declared here so each module only loads what it applies.
    plugins {
        id("com.android.application") version "8.7.3"
        id("org.jetbrains.kotlin.android") version "2.0.21"
        id("org.jetbrains.kotlin.jvm") version "2.0.21"
        id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
        id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21"
        id("com.google.devtools.ksp") version "2.0.21-1.0.28"
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        if (!coreOnly) google()
        mavenCentral()
    }
}

rootProject.name = "Pacelog"
include(":core")
// The Android app needs the Android SDK; set PACELOG_CORE_ONLY=1 to build and
// test just the pure-Kotlin domain logic without it.
if (!coreOnly) {
    include(":app")
}
