plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    jvm("desktop")
    // androidTarget() added in Phase 6 — see KD-004

    jvmToolchain(17)

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core"))
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.lifecycle.viewmodel.compose)
            implementation(libs.lifecycle.runtime.compose)
            // ADR-027 — every component ships previews for loading, error and success
            implementation(compose.components.uiToolingPreview)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
