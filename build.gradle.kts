import org.gradle.api.artifacts.ProjectDependency

plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.sqldelight) apply false
}

// Populated per-subproject in `subprojects { afterEvaluate { ... } }` below — entirely at
// configuration time, so the task itself never needs to touch a Project at execution time.
val declaredProjectDependencies = mutableMapOf<String, Set<String>>()

val verifyDependencyRules = tasks.register<VerifyDependencyRulesTask>("verifyDependencyRules") {
    subprojectDependencies.set(provider { declaredProjectDependencies.toMap() })
}

subprojects {
    tasks.matching { it.name == "check" }.configureEach {
        dependsOn(verifyDependencyRules)
    }

    afterEvaluate {
        val declared = mutableSetOf<String>()
        configurations.forEach { configuration ->
            configuration.dependencies.forEach { dependency ->
                if (dependency is ProjectDependency) declared += dependency.path
            }
        }
        declaredProjectDependencies[path] = declared
    }

    plugins.withId("org.jetbrains.kotlin.jvm") {
        extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension> {
            jvmToolchain(17)
        }
    }
    plugins.withId("org.jetbrains.kotlin.multiplatform") {
        extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension> {
            jvmToolchain(17)
        }
    }
}

tasks.register("verifyAll") {
    group = "verification"
    description = "Runs check across every module — the command CI runs."
    dependsOn(subprojects.map { "${it.path}:check" })
}
