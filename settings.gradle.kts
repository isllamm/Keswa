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
// Phase 9 — the sync server. Depends on :core and nothing else; it has no UI and no features.
include(":server")
include(":features:catalog")
include(":features:settings")
include(":features:auth")
include(":features:sell")
include(":features:inventory")
include(":features:returns")
include(":features:analytics")
include(":features:wholesale")
