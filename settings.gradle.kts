@file:Suppress("UnstableApiUsage")

pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "keswa"

include(
    ":core:common",
    ":core:ui",
    ":domain",
    ":data",
    ":reporting",
    ":export:api",
    ":export:xlsx",
    ":printing:api",
    ":sync:contract",
    ":feature:auth",
    ":feature:catalog",
    ":app:desktop",
)
