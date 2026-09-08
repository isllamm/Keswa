import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.compiler)
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:ui"))
    implementation(project(":domain"))
    implementation(project(":data"))
    implementation(project(":reporting"))
    implementation(project(":export:api"))
    implementation(project(":export:xlsx"))
    implementation(project(":printing:api"))
    implementation(project(":sync:contract"))
    implementation(project(":feature:auth"))

    implementation(compose.desktop.currentOs)
    implementation(compose.runtime)
    implementation(compose.foundation)
    implementation(compose.material3)
    implementation(libs.koin.core)
    implementation(libs.koin.compose)
    implementation(libs.koin.compose.viewmodel)
    implementation(libs.bouncycastle.provider)
    implementation(libs.sqldelight.jvm.driver)
    // Provides Dispatchers.Main (Swing EDT-backed) — viewModelScope needs one at runtime; on a
    // bare JVM (no Android) there otherwise isn't one. Scoped to :app:desktop only.
    implementation(libs.kotlinx.coroutines.swing)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.junit.jupiter.engine)
}

tasks.withType<Test> { useJUnitPlatform() }

compose.desktop {
    application {
        mainClass = "keswa.app.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Dmg, TargetFormat.Deb)
            packageName = "Keswa"
            packageVersion = "0.1.0"
        }
    }
}
