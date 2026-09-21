pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
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

include(":composeApp")
include(":core")
include(":features:catalog")
include(":features:settings")
include(":features:auth")
include(":features:sell")
include(":features:inventory")
