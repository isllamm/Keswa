plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinxSerialization)
    application
}

application {
    mainClass.set("com.alsoug.keswa.server.ServerMainKt")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // The server is a till that never sells (9c): same entities, same migrations, same repository
    // code, so there is one definition of what a sale is rather than two that drift.
    implementation(project(":core"))

    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.serialization.json)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.server.test.host)
}

tasks.withType<Test> { useJUnitPlatform() }
