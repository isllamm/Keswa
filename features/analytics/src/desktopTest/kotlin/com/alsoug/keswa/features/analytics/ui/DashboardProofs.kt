package com.alsoug.keswa.features.analytics.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import com.alsoug.keswa.core.designsystem.KeswaTheme
import com.alsoug.keswa.core.domain.model.BusyHours
import com.alsoug.keswa.core.domain.model.ColourBucket
import com.alsoug.keswa.core.domain.model.DailyPoint
import com.alsoug.keswa.core.domain.model.HeadlineKpis
import com.alsoug.keswa.core.domain.model.HourBucket
import com.alsoug.keswa.core.domain.model.Mover
import com.alsoug.keswa.core.domain.model.SellThroughRowModel
import com.alsoug.keswa.core.domain.money.Money
import com.alsoug.keswa.features.analytics.presentation.screens.dashboard.DashboardContent
import com.alsoug.keswa.features.analytics.presentation.screens.dashboard.DashboardUiState
import java.io.File
import kotlin.test.Test
import org.jetbrains.skia.EncodedImageFormat

/** Renders the dashboard to `features/analytics/build/ui-proofs/`. */
class DashboardProofs {

    private val busy = DashboardUiState(
        kpis = HeadlineKpis(
            revenue = Money.ofPounds(48_250),
            refunded = Money.ofPounds(1_260),
            transactions = 134,
            units = 291,
            returnedUnits = 7,
            cogs = Money.ofPounds(29_400),
        ),
        trend = listOf(
            DailyPoint("2026-09-14", Money.ofPounds(4_200), 12),
            DailyPoint("2026-09-15", Money.ofPounds(5_100), 15),
            DailyPoint("2026-09-16", Money.ofPounds(3_900), 11),
            DailyPoint("2026-09-17", Money.ofPounds(8_400), 24),
            DailyPoint("2026-09-18", Money.ofPounds(9_800), 29),
            DailyPoint("2026-09-19", Money.ofPounds(11_200), 31),
            DailyPoint("2026-09-20", Money.ofPounds(5_650), 12),
        ),
        colours = listOf(
            ColourBucket("c1", "Navy", "كحلي", "#20304f", sold = 84, onHand = 6),
            ColourBucket("c2", "White", "أبيض", "#f2f2ef", sold = 61, onHand = 22),
            ColourBucket("c3", "Beige", "بيج", "#cfbfa4", sold = 12, onHand = 78),
            ColourBucket("c4", "Green", "أخضر", "#3f7a53", sold = 9, onHand = 64),
        ),
        sellThrough = listOf(
            SellThroughRowModel("cat-1", "T-shirts", "تيشيرتات", sold = 157, received = 190),
            SellThroughRowModel("cat-2", "Shirts", "قمصان", sold = 48, received = 160),
        ),
        busyHours = BusyHours(
            (0..6).flatMap { day ->
                (9..22).map { hour ->
                    HourBucket(day, hour, transactions = ((day * 7 + hour * 3) % 11), amount = Money.ZERO)
                }
            },
        ),
        movers = listOf(
            Mover("v1", "KSW-TSH-022-NV", "Round-neck t-shirt", "Navy", 84, Money.ofPounds(15_120), 6, 90, Money.ofPounds(10_080)),
            Mover("v2", "KSW-SHT-004-WH", "Oxford shirt", "White", 31, Money.ofPounds(10_540), 22, 53, Money.ofPounds(7_440)),
        ),
        showsCost = true,
    )

    @Test
    fun `the dashboard, light and dark`() {
        render("dashboard-light", height = 1500) {
            Page(dark = false) { DashboardContent(state = busy, onEvent = {}) }
        }
        render("dashboard-dark", height = 1500) {
            Page(dark = true) { DashboardContent(state = busy, onEvent = {}) }
        }
        // Every new install sees this first, so it is the state most worth eyeballing.
        render("dashboard-day-one", height = 900) {
            Page(dark = false) {
                DashboardContent(
                    state = DashboardUiState(
                        kpis = HeadlineKpis(Money.ZERO, Money.ZERO, 0, 0, 0, Money.ZERO),
                    ),
                    onEvent = {},
                )
            }
        }
    }
}

/** Theme, then page — a `Surface` outside the theme reads the baseline scheme. */
@Composable
private fun Page(dark: Boolean, content: @Composable () -> Unit) {
    KeswaTheme(dark = dark) {
        Surface(color = MaterialTheme.colorScheme.background) { content() }
    }
}

private val directory = File("build/ui-proofs").also { it.mkdirs() }

private fun render(name: String, width: Int = 900, height: Int = 1200, content: @Composable () -> Unit) {
    val density = 2f
    val scene = ImageComposeScene(
        width = (width * density).toInt(),
        height = (height * density).toInt(),
        density = Density(density),
        content = content,
    )
    try {
        val bytes = scene.render().encodeToData(EncodedImageFormat.PNG)?.bytes
            ?: error("Skia declined to encode $name")
        directory.resolve("$name.png").writeBytes(bytes)
    } finally {
        scene.close()
    }
}
