plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinxSerialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
}

kotlin {
    jvm("desktop")
    // androidTarget() added in Phase 6 — see KD-004.
    // commonMain must therefore avoid java.* as well as android.*.

    jvmToolchain(17)

    // Room KMP's generated @ConstructedBy object is an expect/actual object, which is still
    // flagged Beta. The pattern is Room's own, so silence it rather than carry a permanent warning.
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    sourceSets {
        commonMain.dependencies {
            api(compose.runtime)
            api(compose.foundation)
            api(compose.material3)
            api(compose.ui)

            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.serialization.json)
            api(libs.kotlinx.datetime)
            api(libs.koin.core)

            // Phase 3 — drives network printers over raw TCP :9100
            api(libs.ktor.network)

            api(libs.androidx.room.runtime)
            api(libs.androidx.sqlite.bundled)
        }

        val desktopMain by getting {
            dependencies {
                // QR for the receipt's return-lookup code. JVM-only, which is fine: rendering is
                // a platform concern anyway (see IReceiptRenderer).
                implementation(libs.zxing.core)
            }
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

dependencies {
    // Per-target KSP, matching kmp_cashimobile. The configuration name follows the *target*
    // name, so jvm("desktop") gives kspDesktop — not kspJvm.
    add("kspDesktop", libs.androidx.room.compiler)
    // add("kspAndroid", libs.androidx.room.compiler)  // Phase 6
}

room {
    schemaDirectory("$projectDir/schemas")
}
