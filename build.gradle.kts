plugins {
    alias(libs.plugins.kotlinMultiplatform).apply(false)
    // Phase 9 — :server is a plain JVM module. Declared here so it resolves the same Kotlin
    // version as everything else rather than being refused for having an unknown one.
    alias(libs.plugins.kotlinJvm).apply(false)
    alias(libs.plugins.composeMultiplatform).apply(false)
    alias(libs.plugins.composeCompiler).apply(false)
    alias(libs.plugins.kotlinxSerialization).apply(false)
    // Declared here so every module resolves the same AGP, which is what the plugin itself needs
    // in order to report its own version.
    alias(libs.plugins.androidApplication).apply(false)
    alias(libs.plugins.androidLibrary).apply(false)
}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}
