plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    implementation(project(":export:api"))
    implementation(project(":reporting"))
    implementation(project(":core:common"))
    implementation(libs.apache.poi)
    implementation(libs.apache.poi.ooxml)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.junit.jupiter.engine)
}

tasks.withType<Test> { useJUnitPlatform() }
