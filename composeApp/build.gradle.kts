import org.jetbrains.compose.desktop.application.dsl.TargetFormat

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
            implementation(libs.lifecycle.runtime.compose)
        }

        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
            }
        }

        val desktopTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.koin.test)
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.alsoug.keswa.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Keswa"
            packageVersion = "1.0.0"
        }
    }
}
