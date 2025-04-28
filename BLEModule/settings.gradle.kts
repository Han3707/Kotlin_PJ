// settings.gradle.kts

pluginManagement {
    repositories {
        gradlePluginPortal()
        google() // content 필터 제거됨
        mavenCentral()

    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") } // JitPack 유지 (필요하다면)
    }
}

rootProject.name = "BLETest"
include(":app")