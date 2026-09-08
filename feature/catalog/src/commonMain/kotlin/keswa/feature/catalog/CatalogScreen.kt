package keswa.feature.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import keswa.core.common.ErrorKey
import keswa.domain.catalog.ProductWithVariants
import keswa.feature.catalog.CatalogContract.Intent
import keswa.feature.catalog.CatalogContract.State
import keswa.feature.catalog.CatalogContract.VariantRow
import keswa.feature.catalog.generated.resources.Res
import keswa.feature.catalog.generated.resources.add_product
import keswa.feature.catalog.generated.resources.cancel
import keswa.feature.catalog.generated.resources.catalog_title
import keswa.feature.catalog.generated.resources.color_values_hint
import keswa.feature.catalog.generated.resources.error_catalog_duplicate_barcode
import keswa.feature.catalog.generated.resources.error_catalog_duplicate_code
import keswa.feature.catalog.generated.resources.error_catalog_duplicate_sku
import keswa.feature.catalog.generated.resources.error_catalog_name_required
import keswa.feature.catalog.generated.resources.error_catalog_no_variants
import keswa.feature.catalog.generated.resources.error_catalog_too_many_variants
import keswa.feature.catalog.generated.resources.error_common_unexpected
import keswa.feature.catalog.generated.resources.generate_variants
import keswa.feature.catalog.generated.resources.no_products_yet
import keswa.feature.catalog.generated.resources.product_name_ar
import keswa.feature.catalog.generated.resources.product_name_en
import keswa.feature.catalog.generated.resources.save
import keswa.feature.catalog.generated.resources.search_hint
import keswa.feature.catalog.generated.resources.size_values_hint
import keswa.feature.catalog.generated.resources.variant_barcode
import keswa.feature.catalog.generated.resources.variant_cost
import keswa.feature.catalog.generated.resources.variant_qty
import org.jetbrains.compose.resources.stringResource

@Composable
fun CatalogScreen(state: State, onIntent: (Intent) -> Unit) {
    Surface(Modifier.fillMaxSize()) {
        if (state.isCreating) ProductForm(state, onIntent) else ProductList(state, onIntent)
    }
}

@Composable
private fun ProductList(state: State, onIntent: (Intent) -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(Res.string.catalog_title), style = MaterialTheme.typography.headlineSmall)
            Button(onClick = { onIntent(Intent.AddProductClicked) }) { Text(stringResource(Res.string.add_product)) }
        }
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = { onIntent(Intent.SearchChanged(it)) },
            label = { Text(stringResource(Res.string.search_hint)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        when {
            state.isLoadingList -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            state.products.isEmpty() -> Text(stringResource(Res.string.no_products_yet))
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.products, key = { it.product.id }) { ProductRow(it) }
            }
        }
    }
}

@Composable
private fun ProductRow(item: ProductWithVariants) {
    Surface(tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text("${item.product.nameAr}  (${item.product.code})", style = MaterialTheme.typography.titleMedium)
            Text(
                item.variants.joinToString("  ·  ") { "${it.sku}${it.nameSuffix?.let { s -> " — $s" } ?: ""}" },
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun ProductForm(state: State, onIntent: (Intent) -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        OutlinedTextField(
            value = state.nameAr, onValueChange = { onIntent(Intent.NameArChanged(it)) },
            label = { Text(stringResource(Res.string.product_name_ar)) }, modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = state.nameEn, onValueChange = { onIntent(Intent.NameEnChanged(it)) },
            label = { Text(stringResource(Res.string.product_name_en)) }, modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = state.sizeValues, onValueChange = { onIntent(Intent.SizeValuesChanged(it)) },
            label = { Text(stringResource(Res.string.size_values_hint)) }, modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = state.colorValues, onValueChange = { onIntent(Intent.ColorValuesChanged(it)) },
            label = { Text(stringResource(Res.string.color_values_hint)) }, modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Button(onClick = { onIntent(Intent.GenerateVariantsClicked) }) { Text(stringResource(Res.string.generate_variants)) }

        state.error?.let { key ->
            Spacer(Modifier.height(8.dp))
            Text(errorMessage(key), color = MaterialTheme.colorScheme.error)
        }

        if (state.variantRows.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.variantRows.size) { index -> VariantRowEditor(index, state.variantRows[index], onIntent) }
            }
        } else {
            Spacer(Modifier.weight(1f))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.isSaving) {
                CircularProgressIndicator()
            } else {
                Button(onClick = { onIntent(Intent.SaveClicked) }, enabled = state.variantRows.isNotEmpty()) {
                    Text(stringResource(Res.string.save))
                }
            }
            TextButton(onClick = { onIntent(Intent.CancelCreateClicked) }) { Text(stringResource(Res.string.cancel)) }
        }
    }
}

@Composable
private fun VariantRowEditor(index: Int, row: VariantRow, onIntent: (Intent) -> Unit) {
    Surface(tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(row.nameSuffix.ifEmpty { "—" }, style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = row.costText, onValueChange = { onIntent(Intent.VariantCostChanged(index, it)) },
                    label = { Text(stringResource(Res.string.variant_cost)) }, modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = row.qtyText, onValueChange = { onIntent(Intent.VariantQtyChanged(index, it)) },
                    label = { Text(stringResource(Res.string.variant_qty)) }, modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = row.barcodeText, onValueChange = { onIntent(Intent.VariantBarcodeChanged(index, it)) },
                    label = { Text(stringResource(Res.string.variant_barcode)) }, modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun errorMessage(key: ErrorKey): String = when (key.value) {
    "error.catalog.too_many_variants" -> stringResource(Res.string.error_catalog_too_many_variants)
    "error.catalog.duplicate_code" -> stringResource(Res.string.error_catalog_duplicate_code)
    "error.catalog.duplicate_sku" -> stringResource(Res.string.error_catalog_duplicate_sku)
    "error.catalog.duplicate_barcode" -> stringResource(Res.string.error_catalog_duplicate_barcode)
    "error.catalog.name_required" -> stringResource(Res.string.error_catalog_name_required)
    "error.catalog.no_variants" -> stringResource(Res.string.error_catalog_no_variants)
    else -> stringResource(Res.string.error_common_unexpected)
}
