package com.alsoug.keswa.core.data

import com.alsoug.keswa.core.data.repository.CategoryRepositoryImpl
import com.alsoug.keswa.core.database.CAT_TSHIRTS
import com.alsoug.keswa.core.database.createTestDatabase
import com.alsoug.keswa.core.database.seedBaseData
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class CategoryTreeTest {

    private val db = createTestDatabase()
    private val repo = CategoryRepositoryImpl(db.categoryDao()) { 0L }

    @AfterTest
    fun tearDown() = db.close()

    private suspend fun buildTree() {
        // T-shirts > Round neck > Long sleeve   (three levels deep)
        repo.create("tshirts", null, "T-shirts", "تيشيرتات").getOrThrow()
        repo.create("roundneck", "tshirts", "Round neck", "رقبة دائرية").getOrThrow()
        repo.create("longsleeve", "roundneck", "Long sleeve", "كم طويل").getOrThrow()
        repo.create("shirts", null, "Shirts", "قمصان").getOrThrow()
    }

    @Test
    fun `nesting three deep builds correct paths and depths`() = runTest {
        buildTree()

        val tree = repo.getTree().getOrThrow().associateBy { it.id }

        assertEquals("/tshirts/", tree.getValue("tshirts").path)
        assertEquals("/tshirts/roundneck/", tree.getValue("roundneck").path)
        assertEquals("/tshirts/roundneck/longsleeve/", tree.getValue("longsleeve").path)

        assertEquals(0, tree.getValue("tshirts").depth)
        assertEquals(1, tree.getValue("roundneck").depth)
        assertEquals(2, tree.getValue("longsleeve").depth)
        assertTrue(tree.getValue("tshirts").isRoot)
    }

    @Test
    fun `moving a subtree rewrites every descendant path and depth`() = runTest {
        buildTree()

        // When "Round neck" (and its child) is moved under "Shirts"
        repo.move("roundneck", "shirts").getOrThrow()

        val tree = repo.getTree().getOrThrow().associateBy { it.id }

        // Then the whole subtree moved with it — a path is only useful if it stays true
        assertEquals("/shirts/roundneck/", tree.getValue("roundneck").path)
        assertEquals("/shirts/roundneck/longsleeve/", tree.getValue("longsleeve").path)
        assertEquals(1, tree.getValue("roundneck").depth)
        assertEquals(2, tree.getValue("longsleeve").depth)
        assertEquals("shirts", tree.getValue("roundneck").parentId)
        // And the node that did not move is untouched
        assertEquals("/tshirts/", tree.getValue("tshirts").path)
    }

    @Test
    fun `promoting a subtree to the root works too`() = runTest {
        buildTree()

        repo.move("roundneck", null).getOrThrow()

        val tree = repo.getTree().getOrThrow().associateBy { it.id }
        assertEquals("/roundneck/", tree.getValue("roundneck").path)
        assertEquals("/roundneck/longsleeve/", tree.getValue("longsleeve").path)
        assertEquals(0, tree.getValue("roundneck").depth)
        assertTrue(tree.getValue("roundneck").isRoot)
    }

    @Test
    fun `a category cannot be moved beneath its own descendant`() = runTest {
        buildTree()

        // When the cycle is attempted
        val cycle = repo.move("tshirts", "longsleeve")
        val self = repo.move("tshirts", "tshirts")

        // Then both are refused — a cyclic tree hangs every recursive read
        assertTrue(cycle.isFailure)
        assertTrue(self.isFailure)
        assertEquals("/tshirts/", repo.getById("tshirts").getOrThrow()?.path)
    }

    @Test
    fun `moving a category that holds products does not disturb them`() = runTest {
        // Given a category with a product filed under it — the case REPLACE would have destroyed
        db.seedBaseData()
        repo.create("newparent", null, "Apparel", "ملابس").getOrThrow()

        // When it is re-parented
        repo.move(CAT_TSHIRTS, "newparent").getOrThrow()

        // Then the product is still there, still pointing at the same category
        assertEquals(1, db.productDao().countInCategory(CAT_TSHIRTS))
        assertEquals("/newparent/$CAT_TSHIRTS/", repo.getById(CAT_TSHIRTS).getOrThrow()?.path)
    }

    @Test
    fun `selecting a parent category returns products filed under its descendants`() = runTest {
        // Given a product three levels down
        buildTree()
        db.colourDao().upsert(
            com.alsoug.keswa.core.database.entities.ColourEntity("col-navy", "Navy", "كحلي", "#20304f", 0, true),
        )
        db.productDao().upsert(
            com.alsoug.keswa.core.database.entities.ProductEntity(
                "p1", "Long-sleeve tee", "تيشيرت كم طويل", "longsleeve", null, null, null, true, 0, 0,
            ),
        )

        // When the top-level category is selected
        val underTshirts = db.productDao().getInCategoryTree("/tshirts/")
        val underShirts = db.productDao().getInCategoryTree("/shirts/")

        // Then the rollup finds it — the whole reason the path column exists
        assertEquals(listOf("p1"), underTshirts.map { it.id })
        assertTrue(underShirts.isEmpty())
    }
}
