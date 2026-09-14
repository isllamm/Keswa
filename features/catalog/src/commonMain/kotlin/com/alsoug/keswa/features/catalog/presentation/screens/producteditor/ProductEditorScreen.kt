package com.alsoug.keswa.features.catalog.presentation.screens.producteditor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.alsoug.keswa.core.domain.model.Colour
import com.alsoug.keswa.features.catalog.presentation.components.ColourList
import com.alsoug.keswa.features.catalog.presentation.components.ColourSwatch
import com.alsoug.keswa.features.catalog.presentation.model.ColourRowUiModel
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
fun ProductEditorScreen(
    productId: String,
    viewModel: ProductEditorViewModel,
    onBack: () -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(productId) { viewModel.onEvent(ProductEditorUiEvent.Load(productId)) }

    LaunchedEffect(Unit) {
        viewModel.navigation.collect { navigation ->
            when (navigation) {
                ProductEditorNavigation.Back -> onBack()
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is ProductEditorUiEffect.ShowError -> onMessage(effect.message)
                is ProductEditorUiEffect.ShowMessage -> onMessage(effect.message)
                is ProductEditorUiEffect.BlockedByStock ->
                    onMessage("${effect.onHand} still in stock — sell or adjust them first")
            }
        }
    }

    ProductEditorContent(state = state, onEvent = viewModel::onEvent, modifier = modifier)
}

@Composable
internal fun ProductEditorContent(
    state: ProductEditorUiState,
    onEvent: (ProductEditorUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        if (state.isLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { onEvent(ProductEditorUiEvent.Back) }) { Text("← Back") }
            Text(state.productLabel, style = MaterialTheme.typography.titleMedium)
        }
        Text(
            "Each colour is its own SKU, with its own barcode and stock.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp),
        )

        ColourList(rows = state.colours) { row ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${row.onHand}", style = MaterialTheme.typography.labelMedium)
                TextButton(onClick = { onEvent(ProductEditorUiEvent.RemoveColour(row.variantId)) }) {
                    Text("Retire")
                }
            }
        }

        Text(
            "On hand: ${state.totalOnHand}",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 12.dp),
        )

        if (state.addableColours.isNotEmpty()) {
            Text(
                "Add a colour",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 16.dp, bottom = 6.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                state.addableColours.take(6).forEach { colour ->
                    AssistChip(
                        onClick = { onEvent(ProductEditorUiEvent.AddColour(colour.id)) },
                        label = { Text(colour.name) },
                        leadingIcon = { ColourSwatch(colour.hex, size = 12) },
                    )
                }
            }
        }

        SupplierBarcodeRow(
            enabled = state.colours.isNotEmpty(),
            variantId = state.colours.firstOrNull()?.variantId,
            onEvent = onEvent,
        )
    }
}

@Composable
private fun SupplierBarcodeRow(
    enabled: Boolean,
    variantId: String?,
    onEvent: (ProductEditorUiEvent) -> Unit,
) {
    var barcode by remember { mutableStateOf("") }
    Column(modifier = Modifier.padding(top = 20.dp)) {
        Text("Supplier barcode", style = MaterialTheme.typography.titleSmall)
        Text(
            "Scan the code already on the garment — it will resolve to the same SKU as ours.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 6.dp),
        ) {
            OutlinedTextField(
                value = barcode,
                onValueChange = { barcode = it.filter(Char::isDigit).take(13) },
                label = { Text("EAN-13") },
                singleLine = true,
                modifier = Modifier.widthIn(max = 240.dp),
            )
            Button(
                enabled = enabled && barcode.length == 13 && variantId != null,
                onClick = {
                    variantId?.let {
                        onEvent(ProductEditorUiEvent.AssignSupplierBarcode(it, barcode))
                    }
                    barcode = ""
                },
            ) { Text("Attach") }
        }
    }
}

private val previewRows = listOf(
    ColourRowUiModel("v1", "c1", "Navy", "#20304f", "OXF-NAV", "2000000000015", 12),
    ColourRowUiModel("v2", "c2", "White", "#f2f2ef", "OXF-WHI", "2000000000022", 0),
)

@Preview
@Composable
private fun ProductEditorSuccessPreview() {
    MaterialTheme {
        ProductEditorContent(
            state = ProductEditorUiState(
                productLabel = "Oxford shirt",
                colours = previewRows,
                availableColours = listOf(Colour("c3", "Red", "أحمر", "#b3322f", 2, true)),
            ),
            onEvent = {},
        )
    }
}

@Preview
@Composable
private fun ProductEditorLoadingPreview() {
    MaterialTheme {
        ProductEditorContent(state = ProductEditorUiState(isLoading = true), onEvent = {})
    }
}

@Preview
@Composable
private fun ProductEditorEmptyPreview() {
    MaterialTheme {
        ProductEditorContent(
            state = ProductEditorUiState(
                productLabel = "New product",
                availableColours = listOf(Colour("c1", "Navy", "كحلي", "#20304f", 0, true)),
            ),
            onEvent = {},
        )
    }
}
