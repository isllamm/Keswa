plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinxSerialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
    alias(libs.plugins.androidLibrary)
}

kotlin {
    jvm("desktop")
    // Added in Phase 6 (KD-004). `commonMain` has been forbidden `java.*` since Phase 0 precisely
    // so that this is a build-configuration exercise rather than an archaeology one.
    androidTarget()

    jvmToolchain(17)

    // Room KMP's generated @ConstructedBy object is an expect/actual object, which is still
    // flagged Beta. The pattern is Room's own, so silence it rather than carry a permanent warning.
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    // Both targets are JVM by KD-004, and a handful of platform bridges are genuinely identical
    // on each — the credential hasher above all. One implementation, so it cannot drift: a raised
    // iteration count on one target and not the other means nobody can sign in on the handheld.
    applyDefaultHierarchyTemplate()

    sourceSets {
        val jvmCommonMain by creating { dependsOn(commonMain.get()) }
        val jvmCommonTest by creating { dependsOn(commonTest.get()) }

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
            dependsOn(jvmCommonMain)
            dependencies {
                // QR for the receipt's return-lookup code. JVM-only, which is fine: rendering is
                // a platform concern anyway (see IReceiptRenderer).
                implementation(libs.zxing.core)
            }
        }

        val androidMain by getting {
            dependsOn(jvmCommonMain)
            dependencies {
                implementation(libs.zxing.core)
            }
        }

        val desktopTest by getting { dependsOn(jvmCommonTest) }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

android {
    namespace = "com.alsoug.keswa.core"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // Per-target KSP, matching kmp_cashimobile. The configuration name follows the *target*
    // name, so jvm("desktop") gives kspDesktop — not kspJvm.
    add("kspDesktop", libs.androidx.room.compiler)
    add("kspAndroid", libs.androidx.room.compiler)
}

room {
    schemaDirectory("$projectDir/schemas")
}
