package com.alsoug.keswa.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.alsoug.keswa.core.designsystem.KeswaLanguage
import com.alsoug.keswa.core.designsystem.KeswaTheme
import com.alsoug.keswa.core.domain.money.Money
import kotlin.test.Test

/**
 * Renders the design system to `composeApp/build/ui-proofs/`.
 *
 * Not a test in the assertion sense — it produces artefacts to look at, the way Phase 3's print
 * proofs do for receipts. It is here rather than in a script because it needs the Compose runtime,
 * and `./gradlew :composeApp:desktopTest` already has it.
 */
class DesignSystemProofs {

    @Test
    fun `design system, light and dark`() {
        UiProof.render("design-system-light", width = 1180, height = 860) {
            KeswaTheme(dark = false) { Gallery("Light") }
        }
        UiProof.render("design-system-dark", width = 1180, height = 860) {
            KeswaTheme(dark = true) { Gallery("Dark") }
        }
        UiProof.render("design-system-arabic", width = 1180, height = 860) {
            KeswaTheme(dark = false, language = KeswaLanguage.ARABIC) { Gallery("عربي") }
        }
    }
}

@Composable
private fun Gallery(label: String) {
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.padding(28.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Column {
                Text(KeswaTheme.strings.appName, style = MaterialTheme.typography.headlineMedium)
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                TypeScale(Modifier.weight(1f))
                Swatches(Modifier.weight(1f))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Totals(Modifier.weight(1f))
                Tiles(Modifier.weight(1f))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = {}) { Text("Take payment") }
                FilledTonalButton(onClick = {}) { Text("Hold") }
                OutlinedButton(onClick = {}) { Text("Discount") }
                TextButton(onClick = {}) { Text("Clear") }
            }
        }
    }
}

@Composable
private fun Panel(title: String, modifier: Modifier = Modifier, body: @Composable () -> Unit) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, KeswaTheme.semantics.grid),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                title.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = KeswaTheme.semantics.muted,
            )
            Spacer(Modifier.height(10.dp))
            body()
        }
    }
}

@Composable
private fun TypeScale(modifier: Modifier) = Panel("Type", modifier) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Round-neck t-shirt", style = MaterialTheme.typography.titleLarge)
        Text("Navy · KSW-TSH-022-NV", style = MaterialTheme.typography.bodyMedium)
        Text(
            "Denim, cotton, 14 left in Downtown",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        // The point of the whole type file: 1s and 7s the same width, so a column lines up.
        Text("1,117.70", style = KeswaTheme.figureLarge)
        Text("1,117.70", style = KeswaTheme.figure, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Swatches(modifier: Modifier) = Panel("Colour", modifier) {
    val semantics = KeswaTheme.semantics
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(
                "sold" to semantics.sold,
                "on hand" to semantics.onHand,
                "good" to semantics.good,
                "warn" to semantics.warning,
                "crit" to semantics.critical,
            ).forEach { (name, colour) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(38.dp)
                            .background(colour, RoundedCornerShape(5.dp)),
                    )
                    Text(name, style = MaterialTheme.typography.labelSmall, color = semantics.muted)
                }
            }
        }
        Row {
            semantics.ramp.forEach { step ->
                Box(Modifier.weight(1f).height(22.dp).background(step))
            }
        }
        Text(
            "Series 1 is what sold, series 2 what is on hand — on every panel.",
            style = MaterialTheme.typography.bodySmall,
            color = semantics.muted,
        )
    }
}

@Composable
private fun Totals(modifier: Modifier) = Panel("Basket", modifier) {
    Column {
        listOf(
            "Round-neck t-shirt — Navy" to Money.ofPiastres(36_000),
            "Chino — Beige" to Money.ofPiastres(74_900),
            "Socks, three pack" to Money.ofPiastres(9_950),
        ).forEach { (name, amount) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(amount.format(), style = KeswaTheme.figure, textAlign = TextAlign.End)
            }
            HorizontalDivider(color = KeswaTheme.semantics.hair)
        }
        Row(
            Modifier.fillMaxWidth()
                .background(KeswaTheme.semantics.sunk, RoundedCornerShape(5.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Total", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(Money.ofPiastres(120_850).format(), style = KeswaTheme.figureLarge)
        }
    }
}

@Composable
private fun Tiles(modifier: Modifier) = Panel("Today", modifier) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(
            Triple("Takings", "4,318.00", KeswaTheme.semantics.sold),
            Triple("Sales", "37", null),
            Triple("Return rate", "4.1%", KeswaTheme.semantics.warning),
        ).forEach { (label, value, accent) ->
            Column(
                Modifier.weight(1f)
                    .background(KeswaTheme.semantics.sunk, RoundedCornerShape(6.dp))
                    .border(1.dp, KeswaTheme.semantics.grid, RoundedCornerShape(6.dp))
                    .padding(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (accent != null) {
                        Box(Modifier.size(7.dp).background(accent, RoundedCornerShape(2.dp)))
                        Spacer(Modifier.width(5.dp))
                    }
                    Text(
                        label.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = KeswaTheme.semantics.muted,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(value, style = KeswaTheme.figureLarge, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}
