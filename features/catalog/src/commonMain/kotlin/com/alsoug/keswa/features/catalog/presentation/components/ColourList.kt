package com.alsoug.keswa.features.catalog.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.alsoug.keswa.features.catalog.presentation.model.ColourRowUiModel

/**
 * A row per colour, with whatever the caller needs against it.
 *
 * Built standalone and slot-based from the start because Phases 3, 6 and 8 all need the same
 * shape — receiving quantities, blind counts, reporting — and extracting it later is how it ends
 * up coupled to catalogue state.
 */
@Composable
fun ColourList(
    rows: List<ColourRowUiModel>,
    modifier: Modifier = Modifier,
    trailing: @Composable (ColourRowUiModel) -> Unit = {},
) {
    Column(modifier = modifier.fillMaxWidth()) {
        rows.forEachIndexed { index, row ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            ) {
                ColourSwatch(row.hex)
                Column(modifier = Modifier.weight(1f)) {
                    Text(row.label, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = listOfNotNull(row.sku, row.barcode).joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                trailing(row)
            }
            if (index != rows.lastIndex) HorizontalDivider()
        }
    }
}

/**
 * The swatch carries recognition, never identity — the name beside it is what the row means.
 * Two shades can share a hex, and a colour-blind reader gets nothing from the dot alone.
 */
@Composable
fun ColourSwatch(hex: String, size: Int = 16) {
    Row(
        modifier = Modifier
            .size(size.dp)
            .background(parseHex(hex), CircleShape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
    ) {}
}

private fun parseHex(hex: String): Color {
    val cleaned = hex.removePrefix("#")
    val value = cleaned.toLongOrNull(radix = 16) ?: return Color.Gray
    return when (cleaned.length) {
        6 -> Color(0xFF000000 or value)
        8 -> Color(value)
        else -> Color.Gray
    }
}
