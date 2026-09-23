package com.alsoug.keswa.features.catalog.presentation.screens.catalogbrowser

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alsoug.keswa.core.designsystem.EmptyState
import com.alsoug.keswa.core.designsystem.KeswaTheme
import com.alsoug.keswa.core.designsystem.ScreenHeader
import com.alsoug.keswa.core.designsystem.resolve
import com.alsoug.keswa.features.catalog.presentation.components.CategoryTree
import com.alsoug.keswa.features.catalog.presentation.model.CategoryNodeUiModel
import com.alsoug.keswa.features.catalog.presentation.model.ProductUiModel
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * ADR-012 — a humble view: renders state, forwards events, owns no business logic.
 */
@Composable
fun CatalogBrowserScreen(
    viewModel: CatalogBrowserViewModel,
    onOpenProduct: (String) -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val strings = KeswaTheme.strings

    LaunchedEffect(Unit) { viewModel.onEvent(CatalogBrowserUiEvent.Load) }

    LaunchedEffect(Unit) {
        viewModel.navigation.collect { navigation ->
            when (navigation) {
                is CatalogBrowserNavigation.ToProductEditor -> onOpenProduct(navigation.productId)
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is CatalogBrowserUiEffect.ShowError -> onMessage(effect.message.resolve(strings))
                is CatalogBrowserUiEffect.ShowMessage -> onMessage(effect.message.resolve(strings))
            }
        }
    }

    CatalogBrowserContent(
        state = state,
        onEvent = viewModel::onEvent,
        modifier = modifier,
    )
}

@Composable
internal fun CatalogBrowserContent(
    state: CatalogBrowserUiState,
    onEvent: (CatalogBrowserUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        if (state.isLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

        ScreenHeader(
            title = KeswaTheme.strings.catalogue,
            subtitle = KeswaTheme.strings.catalogueSubtitle,
        )

        Row(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.width(280.dp).padding(12.dp)) {
                Text(KeswaTheme.strings.categories, style = MaterialTheme.typography.titleSmall)
                Text(
                    KeswaTheme.strings.youDefineThisTree,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                CategoryTree(
                    nodes = state.categories,
                    selectedId = state.selectedCategoryId,
                    onSelect = { onEvent(CatalogBrowserUiEvent.CategorySelected(it)) },
                    modifier = Modifier.weight(1f),
                )
                AddCategoryRow(canAddSub = state.canAddSubCategory, onEvent = onEvent)
            }

            VerticalDivider()

            Column(modifier = Modifier.weight(1f).padding(12.dp)) {
                OutlinedTextField(
                    value = state.search,
                    onValueChange = { onEvent(CatalogBrowserUiEvent.SearchChanged(it)) },
                    label = { Text(KeswaTheme.strings.searchProducts) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (state.isEmpty) {
                    EmptyState(
                        title = KeswaTheme.strings.nothingInCategory,
                        hint = KeswaTheme.strings.nothingInCategoryHint,
                    )
                } else {
                    ProductList(
                        products = state.products,
                        onSelect = { onEvent(CatalogBrowserUiEvent.ProductSelected(it)) },
                        modifier = Modifier.weight(1f),
                    )
                }
                AddProductRow(enabled = state.selectedCategoryId != null, onEvent = onEvent)
            }
        }
    }
}

@Composable
private fun ProductList(
    products: List<ProductUiModel>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier.padding(top = 8.dp)) {
        items(products, key = { it.id }) { product ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(product.id) }
                    .padding(vertical = 10.dp, horizontal = 4.dp),
            ) {
                Text(product.label, modifier = Modifier.weight(1f))
                Text(
                    "${product.colourCount} colours",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider()
        }
    }
}

@Composable
private fun AddCategoryRow(canAddSub: Boolean, onEvent: (CatalogBrowserUiEvent) -> Unit) {
    var name by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(KeswaTheme.strings.newCategory) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    onEvent(CatalogBrowserUiEvent.CreateCategory(name, name, underSelected = false))
                    name = ""
                },
            ) { Text(KeswaTheme.strings.addTopLevel) }
            TextButton(
                enabled = name.isNotBlank() && canAddSub,
                onClick = {
                    onEvent(CatalogBrowserUiEvent.CreateCategory(name, name, underSelected = true))
                    name = ""
                },
            ) { Text(KeswaTheme.strings.addUnderSelected) }
        }
    }
}

@Composable
private fun AddProductRow(enabled: Boolean, onEvent: (CatalogBrowserUiEvent) -> Unit) {
    var name by remember { mutableStateOf("") }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(KeswaTheme.strings.newProduct) },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        Button(
            enabled = enabled && name.isNotBlank(),
            onClick = {
                onEvent(CatalogBrowserUiEvent.CreateProduct(name, name))
                name = ""
            },
        ) { Text(KeswaTheme.strings.create) }
    }
}

/*
 * ADR-027 asks for Loading, Error and Success previews. Error is absent by design, not omission:
 * ADR-030 keeps failures out of UiState entirely — they are one-shot effects — so there is no
 * error state to render here. Empty stands in as the third case worth eyeballing, since it is
 * what every new install shows first.
 */

private val previewCategories = listOf(
    CategoryNodeUiModel("1", "T-shirts", depth = 0, hasChildren = true),
    CategoryNodeUiModel("2", "Round neck", depth = 1, hasChildren = false),
    CategoryNodeUiModel("3", "Shirts", depth = 0, hasChildren = false),
)

@Preview
@Composable
private fun CatalogBrowserSuccessPreview() {
    KeswaTheme {
        CatalogBrowserContent(
            state = CatalogBrowserUiState(
                categories = previewCategories,
                selectedCategoryId = "2",
                products = listOf(
                    ProductUiModel("p1", "Round-neck t-shirt", colourCount = 5),
                    ProductUiModel("p2", "V-neck t-shirt", colourCount = 3),
                ),
            ),
            onEvent = {},
        )
    }
}

@Preview
@Composable
private fun CatalogBrowserLoadingPreview() {
    KeswaTheme {
        CatalogBrowserContent(state = CatalogBrowserUiState(isLoading = true), onEvent = {})
    }
}

@Preview
@Composable
private fun CatalogBrowserEmptyPreview() {
    KeswaTheme {
        CatalogBrowserContent(
            state = CatalogBrowserUiState(
                categories = previewCategories,
                selectedCategoryId = "3",
            ),
            onEvent = {},
        )
    }
}
