plugins {
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

