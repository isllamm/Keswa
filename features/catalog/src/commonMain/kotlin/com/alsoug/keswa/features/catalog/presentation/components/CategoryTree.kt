package com.alsoug.keswa.features.catalog.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.alsoug.keswa.features.catalog.presentation.model.CategoryNodeUiModel

/**
 * The admin's category tree, flattened and indented by depth.
 *
 * Slot-based per ADR-011: [trailing] lets each caller put what it needs against a row — a product
 * count here, a quantity field in Phase 6's receiving screen — without this component learning
 * about either.
 */
@Composable
fun CategoryTree(
    nodes: List<CategoryNodeUiModel>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    trailing: @Composable (CategoryNodeUiModel) -> Unit = {},
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        items(nodes, key = { it.id }) { node ->
            CategoryRow(
                node = node,
                isSelected = node.id == selectedId,
                onClick = { onSelect(node.id) },
                trailing = { trailing(node) },
            )
        }
    }
}

@Composable
private fun CategoryRow(
    node: CategoryNodeUiModel,
    isSelected: Boolean,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit,
) {
    Surface(
        color = if (isSelected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        shape = RoundedCornerShape(4.dp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).clickable(onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                // Indent follows depth, so a materialised path reads as a tree without nesting.
                .padding(start = (8 + node.depth * 16).dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        ) {
            Text(
                text = if (node.hasChildren) "▾" else "·",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = node.label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                modifier = Modifier.weight(1f),
            )
            trailing()
        }
    }
}
