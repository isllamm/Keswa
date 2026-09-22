package com.alsoug.keswa

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.alsoug.keswa.core.designsystem.KeswaTheme

/**
 * Where the app can go, and what each one is for.
 *
 * The subtitle is not decoration. Seven destinations is more than a person holds in their head on
 * their first week, and "Stockroom" does not say that receiving lives there.
 */
internal data class Destination(
    val route: Route,
    val label: String,
    val hint: String,
)

internal val DESTINATIONS = listOf(
    Destination(Route.Till, "Till", "Scan, charge, print"),
    Destination(Route.Returns, "Returns", "Refunds and exchanges"),
    Destination(Route.Stockroom, "Stockroom", "Receiving, counts, adjustments"),
    Destination(Route.Catalogue, "Catalogue", "Products, colours, prices"),
    Destination(Route.Customers, "Customers", "Accounts and what is owed"),
    Destination(Route.ShiftClose, "Shift", "Float, takings, close"),
    Destination(Route.Dashboard, "Numbers", "What the shop is doing"),
)

/**
 * The navigation, as a column down the side.
 *
 * It replaces seven `TextButton`s in the top bar's `actions` slot, which had three problems and
 * one of them mattered: they were unlabelled as a group, they overflowed on a handheld, and
 * **nothing showed which screen you were on**. On a shared till where someone else left the app
 * open, that last one is the difference between glancing and reading.
 */
@Composable
internal fun NavigationSidebar(
    current: Route,
    operator: String,
    role: String,
    onNavigate: (Route) -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val semantics = KeswaTheme.semantics
    Row(modifier = modifier.fillMaxHeight()) {
        Column(
            modifier = Modifier
                .width(SIDEBAR_WIDTH)
                .fillMaxHeight()
                .background(semantics.sunk)
                .padding(vertical = 14.dp),
        ) {
            Text(
                "Keswa",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 16.dp, bottom = 12.dp),
            )

            DESTINATIONS.forEach { destination ->
                SidebarItem(
                    destination = destination,
                    selected = current.belongsTo(destination.route),
                    onClick = { onNavigate(destination.route) },
                )
            }

            Spacer(Modifier.weight(1f))
            HorizontalDivider(color = semantics.hair)

            // Who is operating the till stays visible: every sale is attributed to them, and on a
            // machine several people share that is worth not having to remember.
            Column(Modifier.padding(start = 16.dp, top = 10.dp, end = 12.dp)) {
                Text(operator, style = MaterialTheme.typography.titleSmall)
                Text(
                    role.lowercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = semantics.muted,
                )
                // A link rather than a `TextButton`: the button's own 12dp inset would step the
                // label out of line with the name above it, and this is a footer, not an action
                // anybody should be able to hit by accident.
                Text(
                    "Sign out",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .clickable(onClick = onSignOut),
                )
            }
        }
        VerticalDivider(color = semantics.grid)
    }
}

@Composable
private fun SidebarItem(
    destination: Destination,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val semantics = KeswaTheme.semantics
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // A bar rather than a filled pill: it marks the row without recolouring the label, so the
        // selected item is still the same weight of text as the rest of the list.
        Box(
            Modifier
                .width(3.dp)
                .fillMaxHeight()
                .padding(vertical = 8.dp)
                .background(
                    if (selected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent,
                    RoundedCornerShape(2.dp),
                ),
        )
        Column(Modifier.padding(start = 13.dp, end = 12.dp)) {
            Text(
                destination.label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Text(
                destination.hint,
                style = MaterialTheme.typography.labelSmall,
                color = semantics.muted,
            )
        }
    }
}

/**
 * The same destinations on a narrow window, as a scrolling strip.
 *
 * A bottom bar is the usual answer and it is wrong here: Material allows three to five items and
 * there are seven, and cutting the list would mean deciding that a handheld cannot reach the
 * catalogue. A strip keeps all of them and admits that it scrolls.
 */
@Composable
internal fun NavigationStrip(
    current: Route,
    onNavigate: (Route) -> Unit,
    modifier: Modifier = Modifier,
) {
    val semantics = KeswaTheme.semantics
    Column(modifier = modifier.fillMaxWidth().background(semantics.sunk)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            DESTINATIONS.forEach { destination ->
                val selected = current.belongsTo(destination.route)
                Text(
                    destination.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier
                        .background(
                            if (selected) {
                                MaterialTheme.colorScheme.surface
                            } else {
                                androidx.compose.ui.graphics.Color.Transparent
                            },
                            RoundedCornerShape(5.dp),
                        )
                        .clickable { onNavigate(destination.route) }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                )
            }
        }
        HorizontalDivider(color = semantics.grid)
    }
}

/**
 * Whether a destination should look selected while a screen reached *through* it is open.
 *
 * Opening a product from the catalogue, or receiving from the stockroom, must not blank the
 * navigation — the reader has not left that part of the app.
 */
private fun Route.belongsTo(destination: Route): Boolean = when {
    this == destination -> true
    destination == Route.Catalogue -> this is Route.ProductEditor
    destination == Route.Stockroom ->
        this == Route.Receiving || this == Route.StockCount ||
            this == Route.Adjust || this == Route.Import || this == Route.Settings
    else -> false
}

internal val SIDEBAR_WIDTH = 188.dp

/** Below this the sidebar costs more width than it earns, and the strip takes over. */
internal val COMPACT_WIDTH = 720.dp

@Composable
internal fun AppSurface(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { content() }
}
