import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

/**
 * Fails the build if any module declares a `project(...)` dependency forbidden by
 * [DependencyRules]. Wired into `check` from the root build script so `./gradlew check`
 * (and CI) catch a layering violation the same way a failing test would.
 *
 * [subprojectDependencies] is populated entirely at configuration time (see root
 * build.gradle.kts) — this task never touches a `Project` at execution time, so it stays
 * configuration-cache-friendly instead of relying on the deprecated `Task.project` access.
 */
abstract class VerifyDependencyRulesTask : DefaultTask() {

    @get:Input
    abstract val subprojectDependencies: MapProperty<String, Set<String>>

    init {
        group = "verification"
        description = "Fails if any module declares a project dependency forbidden by ADR-005."
    }

    @TaskAction
    fun verify() {
        val declaredByPath = subprojectDependencies.get().toSortedMap()
        val violations = mutableListOf<String>()

        for ((path, declared) in declaredByPath) {
            val bad = DependencyRules.findViolations(path, declared)
            if (bad.isNotEmpty()) {
                val allowed = DependencyRules.ALLOWED[path]
                    ?.ifEmpty { setOf("(none)") }
                    ?: setOf("(module not registered in DependencyRules.ALLOWED — no project deps permitted)")
                violations += "  $path -> ${bad.joinToString(", ")}  [allowed: ${allowed.joinToString(", ")}]"
            }
        }

        if (violations.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("Dependency rule violation(s) — see docs/adr/ADR-005-module-graph-and-dependency-rules.md:")
                    violations.forEach(::appendLine)
                }.trimEnd()
            )
        }
        logger.lifecycle("Dependency rules OK: ${declaredByPath.size} modules checked.")
    }
}
