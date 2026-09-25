package com.alsoug.keswa.features.inventory.presentation

import com.alsoug.keswa.core.domain.model.SellableItem
import com.alsoug.keswa.core.domain.money.Money
import com.alsoug.keswa.features.inventory.domain.usecase.CountVariantUseCase
import com.alsoug.keswa.features.inventory.domain.usecase.CurrentCountUseCase
import com.alsoug.keswa.features.inventory.domain.usecase.DiscardCountUseCase
import com.alsoug.keswa.features.inventory.domain.usecase.EnrichCountLinesUseCase
import com.alsoug.keswa.features.inventory.domain.usecase.FindStockItemUseCase
import com.alsoug.keswa.features.inventory.domain.usecase.PostCountUseCase
import com.alsoug.keswa.features.inventory.domain.usecase.RecentCountsUseCase
import com.alsoug.keswa.features.inventory.domain.usecase.ResolveStockLocationUseCase
import com.alsoug.keswa.features.inventory.domain.usecase.StartCountUseCase
import com.alsoug.keswa.features.inventory.domain.usecase.StockCountUseCases
import com.alsoug.keswa.features.inventory.fakes.FakeLocationRepository
import com.alsoug.keswa.features.inventory.fakes.FakePriceRepository
import com.alsoug.keswa.features.inventory.fakes.FakeSellableRepository
import com.alsoug.keswa.features.inventory.fakes.FakeStockCountRepository
import com.alsoug.keswa.features.inventory.fakes.SequentialIds
import com.alsoug.keswa.features.inventory.fakes.fullPermissionSession
import com.alsoug.keswa.features.inventory.fakes.testDispatchers
import com.alsoug.keswa.features.inventory.presentation.screens.count.CountNavigation
import com.alsoug.keswa.features.inventory.presentation.screens.count.CountUiEvent
import com.alsoug.keswa.features.inventory.presentation.screens.count.CountViewModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class CountViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val dispatchers = testDispatchers(testDispatcher)
    private val sessions = fullPermissionSession()
    private val ids = SequentialIds()
    private val now = { 1_000_000L }

    private val locations = FakeLocationRepository()
    private val prices = FakePriceRepository()
    private val countsRepo = FakeStockCountRepository()
    private val sellablesRepo = FakeSellableRepository().withItem(
        SellableItem(
            variantId = "var-tee",
            productId = "prod-tee",
            sku = "TEE-BLK-M",
            name = "Black T-Shirt M",
            nameAr = "تيشيرت أسود وسط",
            colourName = "Black",
            colourNameAr = "أسود",
            cost = Money.ofPounds(140),
            price = Money.ofPounds(250),
            onHand = 10,
        ),
        barcode = "6221113334445",
    )

    private val resolveLocation = ResolveStockLocationUseCase(locations)
    private val find = FindStockItemUseCase(sellablesRepo, prices, now)
    private val counts = StockCountUseCases(
        start = StartCountUseCase(countsRepo, sessions, ids, now),
        countVariant = CountVariantUseCase(countsRepo, sessions, ids),
        post = PostCountUseCase(countsRepo, sessions, now),
        discard = DiscardCountUseCase(countsRepo, sessions),
        current = CurrentCountUseCase(countsRepo),
        recent = RecentCountsUseCase(countsRepo),
    )
    private val enrichLines = EnrichCountLinesUseCase(find)

    private fun createViewModel() = CountViewModel(
        resolveLocation = resolveLocation,
        find = find,
        counts = counts,
        enrichLines = enrichLines,
        dispatchers = dispatchers,
    )

    @Test
    fun `blind count maintains hidden expected quantities until posted`() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.onEvent(CountUiEvent.Load)
        advanceUntilIdle()

        // 1. Start blind count
        vm.onEvent(CountUiEvent.Start)
        advanceUntilIdle()

        assertTrue(vm.state.value.isOpen)
        assertNotNull(vm.state.value.count)

        // 2. Scan barcode
        vm.onEvent(CountUiEvent.Scanned("6221113334445"))
        advanceUntilIdle()

        assertEquals("TEE-BLK-M", vm.state.value.pendingSku)
        assertEquals("var-tee", vm.state.value.pendingVariantId)

        // 3. Confirm counted quantity (e.g. counter found 8 on shelf)
        vm.onEvent(CountUiEvent.CountedChanged("8"))
        vm.onEvent(CountUiEvent.ConfirmLine)
        advanceUntilIdle()

        val line = vm.state.value.lines.single()
        assertEquals(8, line.counted)
        assertNull(line.expected, "expected quantity MUST remain null during open blind count")
        assertNull(line.variance, "variance MUST remain null during open blind count")

        // 4. Post count
        vm.onEvent(CountUiEvent.NoteChanged("Quarterly audit"))
        vm.onEvent(CountUiEvent.Post)
        advanceUntilIdle()

        assertFalse(vm.state.value.isOpen)
        val postedLine = vm.state.value.lines.single()
        assertEquals(10, postedLine.expected)
        assertEquals(-2, postedLine.variance)
    }

    @Test
    fun `discarding open count removes it and navigates Done`() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.onEvent(CountUiEvent.Load)
        advanceUntilIdle()

        vm.onEvent(CountUiEvent.Start)
        advanceUntilIdle()
        assertNotNull(vm.state.value.count)

        val nav = async { vm.navigation.first() }
        vm.onEvent(CountUiEvent.Discard)
        advanceUntilIdle()

        assertNull(vm.state.value.count)
        assertEquals(CountNavigation.Done, nav.await())
    }
}
