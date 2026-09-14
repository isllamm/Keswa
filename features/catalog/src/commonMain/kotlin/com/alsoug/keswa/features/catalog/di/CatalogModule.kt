package com.alsoug.keswa.features.catalog.di

import com.alsoug.keswa.features.catalog.domain.usecase.AddColourToProductUseCase
import com.alsoug.keswa.features.catalog.domain.usecase.AssignSupplierBarcodeUseCase
import com.alsoug.keswa.features.catalog.domain.usecase.CreateCategoryUseCase
import com.alsoug.keswa.features.catalog.domain.usecase.CreateColourUseCase
import com.alsoug.keswa.features.catalog.domain.usecase.CreateProductUseCase
import com.alsoug.keswa.features.catalog.domain.usecase.GenerateInternalBarcodeUseCase
import com.alsoug.keswa.features.catalog.domain.usecase.GetCategoryTreeUseCase
import com.alsoug.keswa.features.catalog.domain.usecase.GetProductsInCategoryUseCase
import com.alsoug.keswa.features.catalog.domain.usecase.MoveCategoryUseCase
import com.alsoug.keswa.features.catalog.domain.usecase.RemoveColourFromProductUseCase
import com.alsoug.keswa.features.catalog.domain.usecase.SearchCatalogUseCase
import com.alsoug.keswa.features.catalog.domain.usecase.SeedCatalogUseCase
import com.alsoug.keswa.features.catalog.presentation.screens.catalogbrowser.CatalogBrowserViewModel
import com.alsoug.keswa.features.catalog.presentation.screens.producteditor.ProductEditorViewModel
import org.koin.dsl.module

val catalogModule = module {
    // Domain Layer — use cases are stateless, so factory
    factory { CreateCategoryUseCase(get(), get()) }
    factory { MoveCategoryUseCase(get()) }
    factory { GetCategoryTreeUseCase(get()) }
    factory { CreateProductUseCase(get(), get()) }
    factory { GetProductsInCategoryUseCase(get(), get()) }
    factory { SearchCatalogUseCase(get()) }
    factory { CreateColourUseCase(get(), get()) }
    factory { SeedCatalogUseCase(get(), get()) }
    factory { GenerateInternalBarcodeUseCase(get()) }
    factory { AddColourToProductUseCase(get(), get(), get(), get(), get()) }
    factory { RemoveColourFromProductUseCase(get()) }
    factory { AssignSupplierBarcodeUseCase(get()) }

    // Presentation Layer
    factory { CatalogBrowserViewModel(get(), get(), get(), get(), get(), get(), get()) }
    factory { ProductEditorViewModel(get(), get(), get(), get(), get(), get(), get()) }
}
