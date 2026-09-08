plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.sqldelight)
}

kotlin {
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(project(":domain"))
            implementation(project(":reporting"))
            implementation(project(":sync:contract"))
            implementation(project(":core:common"))
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines.ext)
            implementation(libs.kotlinx.coroutines.core)
        }
        jvmMain.dependencies {
            implementation(libs.sqldelight.jvm.driver)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlin.test.junit5)
            implementation(libs.kotlinx.coroutines.test)
        }
        jvmTest.dependencies {
            implementation(libs.junit.jupiter.engine)
        }
    }
}

tasks.withType<Test> { useJUnitPlatform() }

sqldelight {
    databases {
        create("KeswaDatabase") {
            packageName.set("keswa.data.db")
            // Schema lives only in migrations/*.sqm (docs/data-model.md §15); .sq files are
            // queries only. See ADR-004.
            deriveSchemaFromMigrations.set(true)
            verifyMigrations.set(true)
        }
    }
}
