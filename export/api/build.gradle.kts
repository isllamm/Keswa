plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:common"))
            implementation(project(":reporting"))
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlin.test.junit5)
        }
        jvmTest.dependencies {
            implementation(libs.junit.jupiter.engine)
        }
    }
}

tasks.withType<Test> { useJUnitPlatform() }
