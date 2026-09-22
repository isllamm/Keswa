package com.alsoug.keswa.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.alsoug.keswa.NavigationSidebar
import com.alsoug.keswa.NavigationStrip
import com.alsoug.keswa.Route
import com.alsoug.keswa.core.designsystem.FigureLargeStyle
import com.alsoug.keswa.core.designsystem.FigureStyle
import com.alsoug.keswa.core.designsystem.KeswaTheme
import com.alsoug.keswa.core.domain.money.Money
import kotlin.test.Test

/**
 * The app shell — what replaced seven `TextButton`s in a top bar.
 */
class ShellProofs {

    @Test
    fun `the shell, wide and compact`() {
        UiProof.render("shell-wide", width = 1280, height = 720) {
            KeswaTheme(dark = false) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Row(Modifier.fillMaxSize()) {
                        NavigationSidebar(
                            current = Route.Till,
                            operator = "Islam",
                            role = "admin",
                            onNavigate = {},
                            onSignOut = {},
                        )
                        StandInTill(Modifier.weight(1f))
                    }
                }
            }
        }

        UiProof.render("shell-wide-dark", width = 1280, height = 720) {
            KeswaTheme(dark = true) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Row(Modifier.fillMaxSize()) {
                        NavigationSidebar(
                            current = Route.Dashboard,
                            operator = "Islam",
                            role = "admin",
                            onNavigate = {},
                            onSignOut = {},
                        )
                        StandInTill(Modifier.weight(1f))
                    }
                }
            }
        }

        // The Phase 6 handheld: the sidebar would eat half the screen, so it becomes a strip.
        UiProof.render("shell-compact", width = 430, height = 760, density = 2f) {
            KeswaTheme(dark = false) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.fillMaxSize()) {
                        NavigationStrip(current = Route.Stockroom, onNavigate = {})
                        StandInTill(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/**
 * Stands in for the till.
 *
 * The real screen needs a ViewModel and the Koin graph; this proof is about the shell around it,
 * so it shows the shape a screen occupies rather than pretending to be one.
 */
@Composable
private fun StandInTill(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Till", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Scan a barcode, or search",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        listOf(
            "Round-neck t-shirt — Navy" to Money.ofPiastres(36_000),
            "Chino — Beige" to Money.ofPiastres(74_900),
        ).forEach { (name, amount) ->
            Row(
                Modifier.fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(amount.format(), style = FigureStyle)
            }
        }
        Row(
            Modifier.fillMaxWidth()
                .background(KeswaTheme.semantics.sunk)
                .padding(horizontal = 12.dp, vertical = 12.dp),
        ) {
            Text("Total", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(Money.ofPiastres(110_900).format(), style = FigureLargeStyle)
        }
    }
}
