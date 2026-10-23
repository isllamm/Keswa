package com.alsoug.keswa.features.inventory.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.alsoug.keswa.core.designsystem.EmptyState
import com.alsoug.keswa.core.designsystem.KeswaTheme
import com.alsoug.keswa.core.designsystem.localisedName
import com.alsoug.keswa.core.domain.model.Category
import com.alsoug.keswa.core.domain.model.SellableItem
import com.alsoug.keswa.features.inventory.presentation.screens.receiving.CategoryCrumb

/**
 * Walks down the active category tree and lists the colours filed at the current level.
 *
 * Slot-based per ADR-011: [rowFields] lets the caller supply the entry fields for each colour row,
 * and [footer] supplies the action bar below the grid.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryPicker(
    crumbs: List<CategoryCrumb>,
    subCategories: List<Category>,
    items: List<SellableItem>,
    onGoUpTo: (Int) -> Unit,
    onOpenCategory: (String) -> Unit,
    modifier: Modifier = Modifier,
    footer: @Composable () -> Unit = {},
    rowFields: @Composable (SellableItem) -> Unit,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text = KeswaTheme.strings.pickFromCategories,
            style = MaterialTheme.typography.titleSmall,
        )

        Breadcrumbs(
            crumbs = crumbs,
            onGoUpTo = onGoUpTo,
            modifier = Modifier.padding(top = 4.dp),
        )

        if (subCategories.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                subCategories.forEach { category ->
                    AssistChip(
                        onClick = { onOpenCategory(category.id) },
                        label = {
                            Text("${localisedName(category.name, category.nameAr)} ›")
                        },
                    )
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        if (items.isEmpty() && subCategories.isEmpty()) {
            EmptyState(
                title = KeswaTheme.strings.categories,
                hint = KeswaTheme.strings.nothingFiledHere,
                modifier = Modifier.weight(1f),
            )
        } else if (items.isEmpty()) {
            Spacer(modifier = Modifier.weight(1f))
        } else {
            GridHeader()
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                items(items, key = { it.variantId }) { item ->
                    GridRow(item = item, rowFields = { rowFields(item) })
                    HorizontalDivider()
                }
            }
        }

        footer()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Breadcrumbs(
    crumbs: List<CategoryCrumb>,
    onGoUpTo: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.Center,
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text = KeswaTheme.strings.allCategories,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (crumbs.isEmpty()) FontWeight.SemiBold else FontWeight.Normal,
            color = if (crumbs.isEmpty()) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.primary
            },
            modifier = Modifier.clickable { onGoUpTo(0) }.padding(vertical = 2.dp),
        )
        crumbs.forEachIndexed { index, crumb ->
            val isLast = index == crumbs.lastIndex
            Text(
                text = "▸",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 2.dp),
            )
            Text(
                text = localisedName(crumb.name, crumb.nameAr),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isLast) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isLast) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.primary
                },
                modifier = Modifier.clickable { onGoUpTo(index + 1) }.padding(vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun GridHeader() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Text(
            text = KeswaTheme.strings.item,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1.2f),
        )
        Text(
            text = KeswaTheme.strings.colour,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.8f),
        )
        Text(
            text = KeswaTheme.strings.onHandLegend,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(56.dp),
        )
        Text(
            text = "${KeswaTheme.strings.quantity} / ${KeswaTheme.strings.cost} / ${KeswaTheme.strings.price}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(2.2f),
        )
    }
}

@Composable
private fun GridRow(
    item: SellableItem,
    rowFields: @Composable () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
    ) {
        Column(modifier = Modifier.weight(1.2f)) {
            Text(
                text = localisedName(item.name, item.nameAr).ifBlank { item.sku },
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = item.sku,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = localisedName(item.colourName, item.colourNameAr),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(0.8f),
        )
        Text(
            text = "${item.onHand}",
            style = KeswaTheme.figure,
            modifier = Modifier.width(56.dp),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.weight(2.2f),
        ) {
            rowFields()
        }
    }
}
