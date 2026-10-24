package com.alsoug.keswa.features.analytics.domain

import com.alsoug.keswa.core.domain.model.AnalyticsPeriod
import com.alsoug.keswa.core.domain.model.BusyHours
import com.alsoug.keswa.core.domain.model.ColourBucket
import com.alsoug.keswa.core.domain.model.DailyPoint
import com.alsoug.keswa.core.domain.model.HeadlineKpis
import com.alsoug.keswa.core.domain.model.Mover
import com.alsoug.keswa.core.domain.model.SellThroughRowModel
import com.alsoug.keswa.core.domain.model.User
import com.alsoug.keswa.core.domain.model.UserRole
import com.alsoug.keswa.core.domain.money.Money
import com.alsoug.keswa.core.domain.repository.IAnalyticsRepository
import com.alsoug.keswa.core.error.Error
import com.alsoug.keswa.core.session.ISessionManager
import com.alsoug.keswa.core.session.InMemorySessionManager
import com.alsoug.keswa.features.analytics.domain.usecase.GetHeadlineKpisUseCase
import com.alsoug.keswa.features.analytics.domain.usecase.GetTopMoversUseCase
import com.alsoug.keswa.features.analytics.domain.usecase.windowEndingAt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * The plan's check 8, which it also called the one most likely to be skipped.
 *
 * Two separate permissions, and the second is the commercially interesting one: a seller may see
 * units and revenue without learning what the shop paid. In a trade where staff move between shops
 * on the same street, that is the leak an owner actually cares about.
 */
class AnalyticsPermissionsTest {

    /** Records whether cost was even *asked for* — hiding it in the UI would not be a control. */
    private class RecordingAnalytics : IAnalyticsRepository {
        var costRequested: Boolean? = null
        var reads = 0

        override suspend fun headline(
            from: Long,
            to: Long,
            withCost: Boolean,
        ): Result<HeadlineKpis> {
            costRequested = withCost
            reads++
            return Result.success(
                HeadlineKpis(
                    revenue = Money.ofPounds(1_000),
                    refunded = Money.ZERO,
                    transactions = 4,
                    units = 9,
                    returnedUnits = 1,
                    cogs = if (withCost) Money.ofPounds(600) else null,
                ),
            )
        }

        override suspend fun revenueByDay(from: Long, to: Long): Result<List<DailyPoint>> {
            reads++
            return Result.success(emptyList())
        }

        override suspend fun colourPerformance(from: Long, to: Long): Result<List<ColourBucket>> {
            reads++
            return Result.success(emptyList())
        }

        override suspend fun sellThrough(from: Long, to: Long): Result<List<SellThroughRowModel>> {
            reads++
            return Result.success(emptyList())
        }

        override suspend fun busyHours(from: Long, to: Long): Result<BusyHours> {
            reads++
            return Result.success(BusyHours(emptyList()))
        }

        override suspend fun topMovers(
            from: Long,
            to: Long,
            limit: Int,
            withCost: Boolean,
        ): Result<List<Mover>> {
            costRequested = withCost
            reads++
            return Result.success(
                listOf(
                    Mover(
                        variantId = "v1",
                        sku = "KSW-TSH-022-NV",
                        productName = "Round-neck t-shirt",
                        colourName = "Navy",
                        sold = 9,
                        revenue = Money.ofPounds(1_620),
                        onHand = 3,
                        received = 12,
                        cogs = if (withCost) Money.ofPounds(1_080) else null,
                    ),
                ),
            )
        }
    }

    private val analytics = RecordingAnalytics()
    private val now = 1_757_000_000_000L

    private fun session(role: UserRole?): ISessionManager = InMemorySessionManager().apply {
        role?.let { signIn(User("usr-1", "sara", "Sara", "سارة", it), atMillis = now) }
    }

    @Test
    fun `a seller cannot open the dashboard at all`() = runTest {
        val headline = GetHeadlineKpisUseCase(analytics, session(UserRole.SELLER)) { now }

        assertIs<Error.ForbiddenAccess>(headline(AnalyticsPeriod.WEEK).exceptionOrNull())
        assertEquals(0, analytics.reads, "the check must come before anything is read")
    }

    @Test
    fun `nobody opens it without a session`() = runTest {
        val headline = GetHeadlineKpisUseCase(analytics, session(null)) { now }

        assertIs<Error.ForbiddenAccess>(headline(AnalyticsPeriod.WEEK).exceptionOrNull())
        assertEquals(0, analytics.reads)
    }

    @Test
    fun `an admin sees the figures, cost included`() = runTest {
        val headline = GetHeadlineKpisUseCase(analytics, session(UserRole.ADMIN)) { now }

        val kpis = headline(AnalyticsPeriod.WEEK).getOrThrow()

        assertEquals(true, analytics.costRequested)
        assertEquals(Money.ofPounds(600), kpis.cogs)
        assertEquals(Money.ofPounds(400), kpis.margin)
    }

    @Test
    fun `top movers carry margin only for somebody allowed to see cost`() = runTest {
        val movers = GetTopMoversUseCase(analytics, session(UserRole.ADMIN)) { now }

        assertEquals(Money.ofPounds(540), movers(AnalyticsPeriod.WEEK).getOrThrow().single().margin)
        assertEquals(true, analytics.costRequested)
    }

    @Test
    fun `the window ends now and reaches back by the period`() {
        val week = AnalyticsPeriod.WEEK.windowEndingAt(now)

        assertEquals(now, week.to)
        assertEquals(now - 7 * 24L * 60 * 60 * 1000, week.from)
        assertTrue(AnalyticsPeriod.QUARTER.windowEndingAt(now).from < week.from)
    }

    @Test
    fun `a shop on day one reports nothing rather than dividing by zero`() {
        val empty = HeadlineKpis(Money.ZERO, Money.ZERO, 0, 0, 0, Money.ZERO)

        // Null, not zero: there is no average basket, which is a different fact from one of zero.
        assertNull(empty.averageBasket)
        assertNull(empty.returnRateBasisPoints)
        assertNull(empty.marginBasisPoints)
    }
}
