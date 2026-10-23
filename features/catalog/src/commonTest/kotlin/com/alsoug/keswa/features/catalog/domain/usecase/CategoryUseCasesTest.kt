package com.alsoug.keswa.features.catalog.domain.usecase

import com.alsoug.keswa.core.error.Error
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class CategoryUseCasesTest {

    private val categories = FakeCategoryRepository()
    private val ids = SequentialIds("cat")

    @Test
    fun `blank name is refused and blank Arabic name falls back to English`() = runTest {
        val admin = adminSession()
        val create = CreateCategoryUseCase(categories, ids, admin)
        val rename = RenameCategoryUseCase(categories, admin)

        assertTrue(create(null, "   ", "قمصان").isFailure)
        assertEquals(0, categories.writes)

        val created = create(null, "  Shirts  ", "   ").getOrThrow()
        assertEquals("Shirts", created.name)
        assertEquals("Shirts", created.nameAr)

        assertTrue(rename(created.id, "   ", "قمصان").isFailure)

        val renamedWithFallback = rename(created.id, "  Oxford  ", "   ").getOrThrow()
        assertEquals("Oxford", renamedWithFallback.name)
        assertEquals("Oxford", renamedWithFallback.nameAr)

        val renamedBoth = rename(created.id, "Oxford", "أكسفورد").getOrThrow()
        assertEquals("Oxford", renamedBoth.name)
        assertEquals("أكسفورد", renamedBoth.nameAr)
    }

    @Test
    fun `without MANAGE_CATALOGUE create, rename, hide and move are refused before the repository is touched`() =
        runTest {
            val seller = sellerSession()
            val create = CreateCategoryUseCase(categories, ids, seller)
            val rename = RenameCategoryUseCase(categories, seller)
            val setActive = SetCategoryActiveUseCase(categories, seller)
            val move = MoveCategoryUseCase(categories, seller)

            assertIs<Error.ForbiddenAccess>(create(null, "Shirts", "قمصان").exceptionOrNull())
            assertIs<Error.ForbiddenAccess>(rename("cat-1", "Tees", "تيشيرتات").exceptionOrNull())
            assertIs<Error.ForbiddenAccess>(setActive("cat-1", false).exceptionOrNull())
            assertIs<Error.ForbiddenAccess>(move("cat-1", null).exceptionOrNull())

            assertEquals(0, categories.writes)
        }
}
