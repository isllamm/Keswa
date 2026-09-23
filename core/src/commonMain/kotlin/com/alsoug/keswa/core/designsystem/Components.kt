package com.alsoug.keswa.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * The top of a screen: what it is, what it is for, and a way out when there is one.
 *
 * Every screen had grown its own version of this — a `TextButton` reading "← Back" beside a
 * `titleMedium`, at a different padding each time — so the app's title sat at four different
 * heights depending on where you were.
 *
 * [onBack] is null for anything the sidebar reaches directly. Back on a destination means "go to
 * the till", which is a destination and not a return, and offering it is how somebody learns not
 * to trust the navigation.
 */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            Text(
                "←",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clickable(onClick = onBack)
                    .padding(end = 12.dp),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = KeswaTheme.semantics.muted,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { actions() }
    }
}

/**
 * A bordered card with a quiet label.
 *
 * Border and no elevation, throughout. A dense screen full of shadows reads as a pile of loose
 * paper; the hairline says "these belong together" without the page appearing to lift.
 */
@Composable
fun Panel(
    title: String,
    modifier: Modifier = Modifier,
    maxWidth: androidx.compose.ui.unit.Dp = 820.dp,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth().widthIn(max = maxWidth)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(7.dp))
                .border(1.dp, KeswaTheme.semantics.grid, RoundedCornerShape(7.dp))
                .padding(16.dp),
        ) {
            Text(
                title.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = KeswaTheme.semantics.muted,
            )
            Column(modifier = Modifier.padding(top = 10.dp)) { content() }
        }
    }
}

/**
 * A figure and what it is.
 *
 * The value is tabular so a row of tiles reads as a row rather than as unrelated numbers, and
 * [accent] is a mark beside the *label* — never the colour of the figure, which fails contrast at
 * this size and reads as a state the number is in.
 */
@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    accent: Color? = null,
) {
    Column(
        modifier = modifier
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
        Text(value, style = KeswaTheme.figureLarge)
    }
}

/**
 * Nothing here yet — and what to do about it.
 *
 * Every screen had a single grey line of prose centred in the space. A person who has just arrived
 * at "Nothing received yet" has learned the state and nothing about the next step, which on a
 * first install is every screen they open.
 */
@Composable
fun EmptyState(
    title: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (hint != null) {
                Text(
                    hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = KeswaTheme.semantics.muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

/** A right-aligned figure in a fixed column, so a table of them reads down the decimal point. */
@Composable
fun Figure(
    value: String,
    modifier: Modifier = Modifier,
    large: Boolean = false,
    color: Color = Color.Unspecified,
) {
    Text(
        value,
        style = if (large) KeswaTheme.figureLarge else KeswaTheme.figure,
        textAlign = TextAlign.End,
        color = color,
        modifier = modifier,
    )
}
