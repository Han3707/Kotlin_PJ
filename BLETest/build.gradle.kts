// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    // Define core Android and Kotlin plugins (applied in module-level build files)
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false // Should reference Kotlin 1.9.24 from libs.versions.toml

    // Define Hilt plugin
    alias(libs.plugins.hilt.android) apply false // Should reference Hilt 2.51.1 or similar stable

    // Define KSP plugin (replaces KAPT for Hilt and other annotation processors)
    alias(libs.plugins.kotlin.ksp) apply false // Make sure this alias exists in libs.versions.toml and points to the correct KSP version (e.g., 1.9.24-1.0.19)

    // KAPT plugin is removed as we are migrating to KSP for Hilt
    // alias(libs.plugins.kotlin.kapt) apply false
}

// Set Java compatibility for the entire project
ext {
    set("sourceCompatibility", JavaVersion.VERSION_17)
    set("targetCompatibility", JavaVersion.VERSION_17)
}

// The allprojects block for forcing Kotlin versions is removed.
// Relying on the Kotlin Gradle Plugin version (1.9.24 specified in libs.versions.toml)
// and Gradle's dependency resolution is generally preferred and less likely to cause
// unexpected conflicts than aggressively forcing versions.
/*
allprojects {
    configurations.all {
        resolutionStrategy {
            // This aggressive forcing is generally not recommended.
            // Remove this block unless you encounter specific, hard-to-resolve conflicts.
            // If needed, target 1.9.24, not 1.9.0.
            /*
            eachDependency {
                if (requested.group == "org.jetbrains.kotlin") {
                    useVersion(libs.versions.kotlin.get()) // Reference the version from catalog if forcing needed
                }
            }
            force("org.jetbrains.kotlin:kotlin-stdlib:${libs.versions.kotlin.get()}")
            force("org.jetbrains.kotlin:kotlin-stdlib-jdk8:${libs.versions.kotlin.get()}")
            force("org.jetbrains.kotlin:kotlin-stdlib-jdk7:${libs.versions.kotlin.get()}")
            force("org.jetbrains.kotlin:kotlin-reflect:${libs.versions.kotlin.get()}")
            */
        }
    }
}
*/