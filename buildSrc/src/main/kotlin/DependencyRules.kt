/**
 * Single source of truth for which module may declare a `project(...)` dependency on which.
 *
 * Mirrors the "Allowed" table in docs/architecture.md §2 and ADR-005. A module absent from
 * [ALLOWED] may declare no project dependencies at all (fail closed) — except [COMPOSITION_ROOT],
 * which is exempt because its entire job is wiring every other module together.
 */
object DependencyRules {

    const val COMPOSITION_ROOT = ":app:desktop"

    val ALLOWED: Map<String, Set<String>> = mapOf(
        ":core:common" to emptySet(),
        ":core:ui" to setOf(":core:common"),
        ":domain" to setOf(":core:common"),
        ":data" to setOf(":domain", ":reporting", ":sync:contract", ":core:common"),
        ":reporting" to setOf(":core:common"),
        ":export:api" to setOf(":core:common", ":reporting"),
        ":export:xlsx" to setOf(":export:api", ":reporting", ":core:common"),
        ":printing:api" to setOf(":core:common"),
        ":sync:contract" to setOf(":core:common"),
        ":feature:auth" to setOf(":domain", ":core:ui", ":core:common"),
    )

    /**
     * Returns the subset of [declaredProjectDependencies] that [projectPath] is not allowed to
     * declare. Empty means the module is clean. Pure and side-effect free so it can be unit
     * tested without spinning up a Gradle build.
     */
    fun findViolations(projectPath: String, declaredProjectDependencies: Set<String>): Set<String> {
        if (projectPath == COMPOSITION_ROOT) return emptySet()
        val allowed = ALLOWED[projectPath] ?: emptySet()
        return declaredProjectDependencies - allowed - projectPath
    }
}
