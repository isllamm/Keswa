import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DependencyRulesTest {

    @Test
    fun `data may depend on domain`() {
        val violations = DependencyRules.findViolations(":data", setOf(":domain"))
        assertTrue(violations.isEmpty(), "expected no violations, got $violations")
    }

    @Test
    fun `data may depend on reporting, sync-contract and core-common`() {
        val violations = DependencyRules.findViolations(
            ":data",
            setOf(":domain", ":reporting", ":sync:contract", ":core:common"),
        )
        assertTrue(violations.isEmpty(), "expected no violations, got $violations")
    }

    @Test
    fun `feature auth depending on data is forbidden`() {
        val violations = DependencyRules.findViolations(":feature:auth", setOf(":domain", ":data"))
        assertEquals(setOf(":data"), violations)
    }

    @Test
    fun `feature auth depending on export-xlsx is forbidden`() {
        val violations = DependencyRules.findViolations(":feature:auth", setOf(":domain", ":export:xlsx"))
        assertEquals(setOf(":export:xlsx"), violations)
    }

    @Test
    fun `reporting depending on data is forbidden`() {
        val violations = DependencyRules.findViolations(":reporting", setOf(":data"))
        assertEquals(setOf(":data"), violations)
    }

    @Test
    fun `domain depending on anything but core-common is forbidden`() {
        val violations = DependencyRules.findViolations(":domain", setOf(":core:common", ":data"))
        assertEquals(setOf(":data"), violations)
    }

    @Test
    fun `export xlsx depending on domain is forbidden`() {
        val violations = DependencyRules.findViolations(":export:xlsx", setOf(":domain", ":export:api"))
        assertEquals(setOf(":domain"), violations)
    }

    @Test
    fun `an unregistered module may declare no project dependencies`() {
        val violations = DependencyRules.findViolations(":feature:pos", setOf(":domain"))
        assertEquals(setOf(":domain"), violations)
    }

    @Test
    fun `app desktop composition root is exempt from every rule`() {
        val violations = DependencyRules.findViolations(
            ":app:desktop",
            setOf(":domain", ":data", ":export:xlsx", ":printing:api", ":reporting", ":core:ui"),
        )
        assertTrue(violations.isEmpty(), "expected no violations, got $violations")
    }
}
