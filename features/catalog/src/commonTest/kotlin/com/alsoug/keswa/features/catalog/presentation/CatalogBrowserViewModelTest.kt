package com.alsoug.keswa.features.catalog.presentation

import com.alsoug.keswa.core.coroutines.DispatcherProvider
import com.alsoug.keswa.core.designsystem.EnglishStrings
import com.alsoug.keswa.core.designsystem.resolve
import com.alsoug.keswa.core.domain.model.Category
import com.alsoug.keswa.features.catalog.domain.usecase.CreateCategoryUseCase
import com.alsoug.keswa.features.catalog.domain.usecase.CreateProductUseCase
import com.alsoug.keswa.features.catalog.domain.usecase.FakeCategoryRepository
import com.alsoug.keswa.features.catalog.domain.usecase.FakeProductRepository
import com.alsoug.keswa.features.catalog.domain.usecase.GetCategoryTreeUseCase
import com.alsoug.keswa.features.catalog.domain.usecase.GetProductsInCategoryUseCase
import com.alsoug.keswa.features.catalog.domain.usecase.MoveCategoryUseCase
import com.alsoug.keswa.features.catalog.domain.usecase.RenameCategoryUseCase
import com.alsoug.keswa.features.catalog.domain.usecase.SearchCatalogUseCase
import com.alsoug.keswa.features.catalog.domain.usecase.SequentialIds
import com.alsoug.keswa.features.catalog.domain.usecase.SetCategoryActiveUseCase
import com.alsoug.keswa.features.catalog.domain.usecase.adminSession
import com.alsoug.keswa.features.catalog.presentation.screens.catalogbrowser.CatalogBrowserUiEffect
import com.alsoug.keswa.features.catalog.presentation.screens.catalogbrowser.CatalogBrowserUiEvent
import com.alsoug.keswa.features.catalog.presentation.screens.catalogbrowser.CatalogBrowserViewModel
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class CatalogBrowserViewModelTest {

    private val scheduler = StandardTestDispatcher()

    private val dispatchers = object : DispatcherProvider {
        override val io: CoroutineDispatcher = scheduler
        override val main: CoroutineDispatcher = scheduler
        override val default: CoroutineDispatcher = scheduler
    }

    private val categories = FakeCategoryRepository().given(
        Category("cat-1", null, "Men", "رجالي", "/cat-1/", 0, 0, true),
    )
    private val products = FakeProductRepository()
    private val session = adminSession()

    private fun viewModel() = CatalogBrowserViewModel(
        getTree = GetCategoryTreeUseCase(categories),
        getProducts = GetProductsInCategoryUseCase(categories, products),
        createCategory = CreateCategoryUseCase(categories, SequentialIds("cat"), session),
        renameCategory = RenameCategoryUseCase(categories, session),
        setCategoryActive = SetCategoryActiveUseCase(categories, session),
        moveCategory = MoveCategoryUseCase(categories, session),
        createProduct = CreateProductUseCase(products, SequentialIds("prod")),
        search = SearchCatalogUseCase(products),
        dispatchers = dispatchers,
    )

    @BeforeTest
    fun setUp() = Dispatchers.setMain(scheduler)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `rename and hide update the tree and show a message`() = runTest(scheduler) {
        val viewModel = viewModel()
        val effects = mutableListOf<CatalogBrowserUiEffect>()
        backgroundScope.launch { viewModel.effect.collect { effects += it } }

        viewModel.onEvent(CatalogBrowserUiEvent.Load)
        advanceUntilIdle()

        viewModel.onEvent(CatalogBrowserUiEvent.RenameCategory("cat-1", "Menswear", "ملابس رجالي"))
        advanceUntilIdle()

        val renamedNode = viewModel.state.value.categories.single()
        assertEquals("Menswear", renamedNode.name)
        assertEquals("ملابس رجالي", renamedNode.nameAr)
        val renameMessage = assertIs<CatalogBrowserUiEffect.ShowMessage>(effects.last())
        assertEquals(EnglishStrings.categoryRenamed, renameMessage.message.resolve(EnglishStrings))

        viewModel.onEvent(CatalogBrowserUiEvent.SetCategoryActive("cat-1", false))
        advanceUntilIdle()

        assertFalse(viewModel.state.value.categories.single().isActive)
        val hideMessage = assertIs<CatalogBrowserUiEffect.ShowMessage>(effects.last())
        assertEquals(EnglishStrings.categoryHidden, hideMessage.message.resolve(EnglishStrings))

        viewModel.onEvent(CatalogBrowserUiEvent.SetCategoryActive("cat-1", true))
        advanceUntilIdle()

        assertTrue(viewModel.state.value.categories.single().isActive)
        val showMessage = assertIs<CatalogBrowserUiEffect.ShowMessage>(effects.last())
        assertEquals(EnglishStrings.categoryShown, showMessage.message.resolve(EnglishStrings))
    }
}
