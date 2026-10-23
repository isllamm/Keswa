package com.alsoug.keswa.features.inventory.presentation

import com.alsoug.keswa.core.domain.model.Category
import com.alsoug.keswa.core.domain.model.SellableItem
import com.alsoug.keswa.core.domain.money.Money
import com.alsoug.keswa.features.inventory.domain.usecase.AddReceiptLineUseCase
import com.alsoug.keswa.features.inventory.domain.usecase.AddReceiptLinesUseCase
import com.alsoug.keswa.features.inventory.domain.usecase.BrowseCategoryUseCase
import com.alsoug.keswa.features.inventory.domain.usecase.DiscardReceiptUseCase
import com.alsoug.keswa.features.inventory.domain.usecase.EnrichReceiptLinesUseCase
import com.alsoug.keswa.features.inventory.domain.usecase.EnsureVariantBarcodeUseCase
import com.alsoug.keswa.features.inventory.domain.usecase.EntryProblem
import com.alsoug.keswa.features.inventory.domain.usecase.FindStockItemUseCase
import com.alsoug.keswa.features.inventory.domain.usecase.GetReceiptUseCase
import com.alsoug.keswa.features.inventory.domain.usecase.InventoryLabelUseCases
import com.alsoug.keswa.features.inventory.domain.usecase.ObserveDraftReceiptsUseCase
import com.alsoug.keswa.features.inventory.domain.usecase.PostReceiptUseCase
import com.alsoug.keswa.features.inventory.domain.usecase.PrintHangTagsUseCase
import com.alsoug.keswa.features.inventory.domain.usecase.PrintReceiptLineLabelsUseCase
import com.alsoug.keswa.features.inventory.domain.usecase.PrintSingleVariantLabelUseCase
import com.alsoug.keswa.features.inventory.domain.usecase.RecentReceiptsUseCase
import com.alsoug.keswa.features.inventory.domain.usecase.ReceivingUseCases
import com.alsoug.keswa.features.inventory.domain.usecase.RemoveReceiptLineUseCase
import com.alsoug.keswa.features.inventory.domain.usecase.ResolveStockLocationUseCase
import com.alsoug.keswa.features.inventory.domain.usecase.StartReceiptUseCase
import com.alsoug.keswa.features.inventory.fakes.FakeCategoryRepository
import com.alsoug.keswa.features.inventory.fakes.FakeLocationRepository
import com.alsoug.keswa.features.inventory.fakes.FakePriceRepository
import com.alsoug.keswa.features.inventory.fakes.FakePrinterTransport
import com.alsoug.keswa.features.inventory.fakes.FakeSellableRepository
import com.alsoug.keswa.features.inventory.fakes.FakeSettingsRepository
import com.alsoug.keswa.features.inventory.fakes.FakeStockReceiptRepository
import com.alsoug.keswa.features.inventory.fakes.FakeTransportFactory
import com.alsoug.keswa.features.inventory.fakes.FakeVariantRepository
import com.alsoug.keswa.features.inventory.fakes.SequentialIds
import com.alsoug.keswa.features.inventory.fakes.fullPermissionSession
import com.alsoug.keswa.features.inventory.fakes.testDispatchers
import com.alsoug.keswa.features.inventory.presentation.screens.receiving.ReceivingNavigation
import com.alsoug.keswa.features.inventory.presentation.screens.receiving.ReceivingUiEvent
import com.alsoug.keswa.features.inventory.presentation.screens.receiving.ReceivingViewModel
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
class ReceivingViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val dispatchers = testDispatchers(testDispatcher)
    private val sessions = fullPermissionSession()
    private val ids = SequentialIds()
    private val now = { 1_000_000L }

    private val locations = FakeLocationRepository()
    private val categoriesRepo = FakeCategoryRepository()
    private val prices = FakePriceRepository()
    private val receiptsRepo = FakeStockReceiptRepository()
    private val variantsRepo = FakeVariantRepository()
        .withBarcode("var-1", "6221112223334")
        .withBarcode("var-2", "6221112223335")

    private val itemNavy = SellableItem(
        variantId = "var-1",
        productId = "prod-1",
        sku = "TEE-NAVY",
        name = "Navy T-Shirt",
        nameAr = "تيشيرت كحلي",
        colourName = "Navy",
        colourNameAr = "كحلي",
        cost = Money.ofPounds(120),
        price = Money.ofPounds(200),
        onHand = 15,
    )
    private val itemWhite = SellableItem(
        variantId = "var-2",
        productId = "prod-1",
        sku = "TEE-WHITE",
        name = "White T-Shirt",
        nameAr = "تيشيرت أبيض",
        colourName = "White",
        colourNameAr = "أبيض",
        cost = Money.ofPounds(110),
        price = Money.ofPounds(190),
        onHand = 8,
    )

    private val sellablesRepo = FakeSellableRepository()
        .withItem(itemNavy, barcode = "6221112223334", categoryId = "cat-shirts")
        .withItem(itemWhite, barcode = "6221112223335", categoryId = "cat-shirts")

    private val settingsRepo = FakeSettingsRepository()
    private val printerTransport = FakePrinterTransport()
    private val transports = FakeTransportFactory(printerTransport)

    private val resolveLocation = ResolveStockLocationUseCase(locations)
    private val find = FindStockItemUseCase(sellablesRepo, prices, now)
    private val ensureBarcode = EnsureVariantBarcodeUseCase(variantsRepo)
    private val printSingleLabel = PrintSingleVariantLabelUseCase(
        variantsRepo,
        prices,
        settingsRepo,
        sessions,
        transports,
        ensureBarcode,
        now,
    )
    private val printHangTags = PrintHangTagsUseCase(
        receiptsRepo,
        variantsRepo,
        prices,
        settingsRepo,
        sessions,
        transports,
        now,
    )
    private val printLineLabels = PrintReceiptLineLabelsUseCase(
        receiptsRepo,
        find,
        ensureBarcode,
        settingsRepo,
        sessions,
        transports,
    )
    private val labels = InventoryLabelUseCases(
        ensureBarcode,
        printSingleLabel,
        printHangTags,
        printLineLabels,
    )

    private val receiving = ReceivingUseCases(
        start = StartReceiptUseCase(receiptsRepo, sessions, ids, now),
        addLine = AddReceiptLineUseCase(receiptsRepo, sessions, ids, ensureBarcode),
        addLines = AddReceiptLinesUseCase(receiptsRepo, sessions, ensureBarcode, ids),
        browse = BrowseCategoryUseCase(categoriesRepo, find),
        removeLine = RemoveReceiptLineUseCase(receiptsRepo, sessions),
        post = PostReceiptUseCase(receiptsRepo, sessions, now),
        discard = DiscardReceiptUseCase(receiptsRepo, sessions),
        getById = GetReceiptUseCase(receiptsRepo),
        recent = RecentReceiptsUseCase(receiptsRepo),
        observeDrafts = ObserveDraftReceiptsUseCase(receiptsRepo),
    )
    private val enrichLines = EnrichReceiptLinesUseCase(find, variantsRepo)

    private fun seedCategoryTree() {
        categoriesRepo.clear()
            .withCategory(
                Category(
                    id = "cat-men",
                    name = "Men",
                    nameAr = "رجالي",
                    parentId = null,
                    path = "/cat-men/",
                    depth = 0,
                    sortOrder = 0,
                    isActive = true,
                ),
            )
            .withCategory(
                Category(
                    id = "cat-women",
                    name = "Women",
                    nameAr = "حريمي",
                    parentId = null,
                    path = "/cat-women/",
                    depth = 0,
                    sortOrder = 1,
                    isActive = true,
                ),
            )
            .withCategory(
                Category(
                    id = "cat-shirts",
                    name = "Shirts",
                    nameAr = "قمصان",
                    parentId = "cat-men",
                    path = "/cat-men/cat-shirts/",
                    depth = 1,
                    sortOrder = 0,
                    isActive = true,
                ),
            )
    }

    private fun createViewModel() = ReceivingViewModel(
        resolveLocation = resolveLocation,
        find = find,
        receiving = receiving,
        enrichLines = enrichLines,
        labels = labels,
        dispatchers = dispatchers,
    )

    @Test
    fun `Load resolves location and populates state`() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.onEvent(ReceivingUiEvent.Load)
        advanceUntilIdle()

        assertFalse(vm.state.value.isLoading)
        assertNotNull(vm.state.value.recent)
    }

    @Test
    fun `full delivery flow from start to scan, confirm and post`() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.onEvent(ReceivingUiEvent.Load)
        advanceUntilIdle()

        // 1. Start draft delivery
        vm.onEvent(ReceivingUiEvent.ReferenceChanged("INV-990"))
        vm.onEvent(ReceivingUiEvent.SupplierChanged("Al-Nasr Textiles"))
        vm.onEvent(ReceivingUiEvent.StartReceipt)
        advanceUntilIdle()

        val receipt = vm.state.value.receipt
        assertNotNull(receipt)
        assertEquals("INV-990", receipt.reference)
        assertEquals("Al-Nasr Textiles", receipt.supplierName)
        assertTrue(vm.state.value.isDraft)

        // 2. Scan barcode
        vm.onEvent(ReceivingUiEvent.Scanned("6221112223334"))
        advanceUntilIdle()

        val pending = vm.state.value.pendingItem
        assertNotNull(pending)
        assertEquals("TEE-NAVY", pending.sku)
        assertEquals("1", vm.state.value.quantityEntry)
        assertEquals("120.00", vm.state.value.costEntry)

        // 3. Confirm line
        vm.onEvent(ReceivingUiEvent.QuantityChanged("10"))
        vm.onEvent(ReceivingUiEvent.CostChanged("115.00"))
        vm.onEvent(ReceivingUiEvent.ConfirmLine)
        advanceUntilIdle()

        assertNull(vm.state.value.pendingItem)
        assertEquals(1, vm.state.value.lines.size)
        assertEquals(10, vm.state.value.pieceCount)
        assertEquals(Money.ofPiastres(11_500), vm.state.value.lines.single().unitCost)

        // 4. Post receipt
        vm.onEvent(ReceivingUiEvent.Post)
        advanceUntilIdle()

        assertFalse(vm.state.value.isDraft)
        assertFalse(vm.state.value.isPosting)
        assertEquals(1, vm.state.value.costChanges.size)
    }

    @Test
    fun `discarding a draft clears receipt and navigates Done`() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.onEvent(ReceivingUiEvent.Load)
        advanceUntilIdle()

        vm.onEvent(ReceivingUiEvent.StartReceipt)
        advanceUntilIdle()
        assertNotNull(vm.state.value.receipt)

        val nav = async { vm.navigation.first() }
        vm.onEvent(ReceivingUiEvent.Discard)
        advanceUntilIdle()

        assertNull(vm.state.value.receipt)
        assertEquals(ReceivingNavigation.Done, nav.await())
    }

    @Test
    fun `Starting a delivery loads the top level`() = runTest(testDispatcher) {
        seedCategoryTree()
        val vm = createViewModel()
        vm.onEvent(ReceivingUiEvent.Load)
        advanceUntilIdle()

        vm.onEvent(ReceivingUiEvent.StartReceipt)
        advanceUntilIdle()

        assertTrue(vm.state.value.crumbs.isEmpty())
        assertEquals(listOf("cat-men", "cat-women"), vm.state.value.subCategories.map { it.id })
        assertTrue(vm.state.value.levelItems.isEmpty())
    }

    @Test
    fun `Opening a category shows its sub-categories and items and going up returns to the parent`() =
        runTest(testDispatcher) {
            seedCategoryTree()
            val vm = createViewModel()
            vm.onEvent(ReceivingUiEvent.Load)
            advanceUntilIdle()
            vm.onEvent(ReceivingUiEvent.StartReceipt)
            advanceUntilIdle()

            // Open Men -> shows Shirts sub-category
            vm.onEvent(ReceivingUiEvent.OpenCategory("cat-men"))
            advanceUntilIdle()
            assertEquals(listOf("cat-men"), vm.state.value.crumbs.map { it.id })
            assertEquals(listOf("cat-shirts"), vm.state.value.subCategories.map { it.id })
            assertTrue(vm.state.value.levelItems.isEmpty())

            // Open Shirts -> shows the two colours
            vm.onEvent(ReceivingUiEvent.OpenCategory("cat-shirts"))
            advanceUntilIdle()
            assertEquals(listOf("cat-men", "cat-shirts"), vm.state.value.crumbs.map { it.id })
            assertTrue(vm.state.value.subCategories.isEmpty())
            assertEquals(listOf("var-1", "var-2"), vm.state.value.levelItems.map { it.variantId })

            // Go up to Men (depth 1)
            vm.onEvent(ReceivingUiEvent.GoUpTo(1))
            advanceUntilIdle()
            assertEquals(listOf("cat-men"), vm.state.value.crumbs.map { it.id })
            assertEquals(listOf("cat-shirts"), vm.state.value.subCategories.map { it.id })

            // Go up to top level (depth 0)
            vm.onEvent(ReceivingUiEvent.GoUpTo(0))
            advanceUntilIdle()
            assertTrue(vm.state.value.crumbs.isEmpty())
            assertEquals(listOf("cat-men", "cat-women"), vm.state.value.subCategories.map { it.id })
        }

    @Test
    fun `Typed values survive moving to another category and back`() = runTest(testDispatcher) {
        seedCategoryTree()
        val vm = createViewModel()
        vm.onEvent(ReceivingUiEvent.Load)
        advanceUntilIdle()
        vm.onEvent(ReceivingUiEvent.StartReceipt)
        advanceUntilIdle()

        vm.onEvent(ReceivingUiEvent.OpenCategory("cat-men"))
        advanceUntilIdle()
        vm.onEvent(ReceivingUiEvent.OpenCategory("cat-shirts"))
        advanceUntilIdle()

        vm.onEvent(ReceivingUiEvent.PickQuantityChanged("var-1", "12"))
        vm.onEvent(ReceivingUiEvent.PickCostChanged("var-1", "125.00"))
        vm.onEvent(ReceivingUiEvent.PickPriceChanged("var-1", "240.00"))

        // Navigate up to top level and into Women, then back into Men > Shirts
        vm.onEvent(ReceivingUiEvent.GoUpTo(0))
        advanceUntilIdle()
        vm.onEvent(ReceivingUiEvent.OpenCategory("cat-women"))
        advanceUntilIdle()
        vm.onEvent(ReceivingUiEvent.GoUpTo(0))
        advanceUntilIdle()
        vm.onEvent(ReceivingUiEvent.OpenCategory("cat-men"))
        advanceUntilIdle()
        vm.onEvent(ReceivingUiEvent.OpenCategory("cat-shirts"))
        advanceUntilIdle()

        val entry = vm.state.value.typed["var-1"]
        assertNotNull(entry)
        assertEquals("12", entry.quantity)
        assertEquals("125.00", entry.cost)
        assertEquals("240.00", entry.price)
    }

    @Test
    fun `A row already on the delivery is filled in from its line`() = runTest(testDispatcher) {
        seedCategoryTree()
        val vm = createViewModel()
        vm.onEvent(ReceivingUiEvent.Load)
        advanceUntilIdle()
        vm.onEvent(ReceivingUiEvent.StartReceipt)
        advanceUntilIdle()

        // First add var-1 via scan with custom quantity=10, cost=115.00, price=245.00
        vm.onEvent(ReceivingUiEvent.Scanned("6221112223334"))
        advanceUntilIdle()
        vm.onEvent(ReceivingUiEvent.QuantityChanged("10"))
        vm.onEvent(ReceivingUiEvent.CostChanged("115.00"))
        vm.onEvent(ReceivingUiEvent.PriceChanged("245.00"))
        vm.onEvent(ReceivingUiEvent.ConfirmLine)
        advanceUntilIdle()

        // Now browse to Shirts and change only quantity on var-1
        vm.onEvent(ReceivingUiEvent.OpenCategory("cat-men"))
        advanceUntilIdle()
        vm.onEvent(ReceivingUiEvent.OpenCategory("cat-shirts"))
        advanceUntilIdle()

        vm.onEvent(ReceivingUiEvent.PickQuantityChanged("var-1", "25"))

        val seeded = vm.state.value.typed["var-1"]
        assertNotNull(seeded)
        assertEquals("25", seeded.quantity)
        assertEquals("115.00", seeded.cost)
        assertEquals("245.00", seeded.price)
    }

    @Test
    fun `Add with good rows adds the lines and clears what was typed`() = runTest(testDispatcher) {
        seedCategoryTree()
        val vm = createViewModel()
        vm.onEvent(ReceivingUiEvent.Load)
        advanceUntilIdle()
        vm.onEvent(ReceivingUiEvent.StartReceipt)
        advanceUntilIdle()
        vm.onEvent(ReceivingUiEvent.OpenCategory("cat-men"))
        advanceUntilIdle()
        vm.onEvent(ReceivingUiEvent.OpenCategory("cat-shirts"))
        advanceUntilIdle()

        vm.onEvent(ReceivingUiEvent.PickQuantityChanged("var-1", "10"))
        vm.onEvent(ReceivingUiEvent.PickPriceChanged("var-1", "250.00"))
        vm.onEvent(ReceivingUiEvent.PickQuantityChanged("var-2", "5"))

        vm.onEvent(ReceivingUiEvent.AddPicked)
        advanceUntilIdle()

        assertTrue(vm.state.value.typed.isEmpty())
        assertTrue(vm.state.value.problems.isEmpty())
        assertEquals(2, vm.state.value.lines.size)
        val line1 = vm.state.value.lines.first { it.variantId == "var-1" }
        assertEquals(10, line1.quantity)
        assertEquals(Money.ofPounds(250), line1.sellPrice)
    }

    @Test
    fun `Add with a bad row adds nothing and marks that row`() = runTest(testDispatcher) {
        seedCategoryTree()
        val vm = createViewModel()
        vm.onEvent(ReceivingUiEvent.Load)
        advanceUntilIdle()
        vm.onEvent(ReceivingUiEvent.StartReceipt)
        advanceUntilIdle()
        vm.onEvent(ReceivingUiEvent.OpenCategory("cat-men"))
        advanceUntilIdle()
        vm.onEvent(ReceivingUiEvent.OpenCategory("cat-shirts"))
        advanceUntilIdle()

        vm.onEvent(ReceivingUiEvent.PickQuantityChanged("var-1", "10"))
        vm.onEvent(ReceivingUiEvent.PickQuantityChanged("var-2", "abc"))

        vm.onEvent(ReceivingUiEvent.AddPicked)
        advanceUntilIdle()

        assertTrue(vm.state.value.lines.isEmpty())
        assertEquals(mapOf("var-2" to EntryProblem.NOT_A_QUANTITY), vm.state.value.problems)
        assertEquals(2, vm.state.value.typed.size)
    }

    @Test
    fun `Changing a marked row clears its mark`() = runTest(testDispatcher) {
        seedCategoryTree()
        val vm = createViewModel()
        vm.onEvent(ReceivingUiEvent.Load)
        advanceUntilIdle()
        vm.onEvent(ReceivingUiEvent.StartReceipt)
        advanceUntilIdle()
        vm.onEvent(ReceivingUiEvent.OpenCategory("cat-men"))
        advanceUntilIdle()
        vm.onEvent(ReceivingUiEvent.OpenCategory("cat-shirts"))
        advanceUntilIdle()

        vm.onEvent(ReceivingUiEvent.PickQuantityChanged("var-2", "0"))
        vm.onEvent(ReceivingUiEvent.AddPicked)
        advanceUntilIdle()
        assertEquals(EntryProblem.NOT_A_QUANTITY, vm.state.value.problems["var-2"])

        vm.onEvent(ReceivingUiEvent.PickQuantityChanged("var-2", "4"))
        assertTrue(vm.state.value.problems.isEmpty())
    }

    @Test
    fun `The scan dialog price reaches the line`() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.onEvent(ReceivingUiEvent.Load)
        advanceUntilIdle()
        vm.onEvent(ReceivingUiEvent.StartReceipt)
        advanceUntilIdle()

        vm.onEvent(ReceivingUiEvent.Scanned("6221112223334"))
        advanceUntilIdle()
        assertEquals("200.00", vm.state.value.priceEntry)

        vm.onEvent(ReceivingUiEvent.QuantityChanged("6"))
        vm.onEvent(ReceivingUiEvent.CostChanged("120.00"))
        vm.onEvent(ReceivingUiEvent.PriceChanged("260.00"))
        vm.onEvent(ReceivingUiEvent.ConfirmLine)
        advanceUntilIdle()

        val line = vm.state.value.lines.single()
        assertEquals(Money.ofPounds(260), line.sellPrice)
        assertEquals(Money.ofPounds(200), line.livePrice)
    }

    @Test
    fun `Print labels on a line sends that line copies`() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.onEvent(ReceivingUiEvent.Load)
        advanceUntilIdle()
        vm.onEvent(ReceivingUiEvent.StartReceipt)
        advanceUntilIdle()

        vm.onEvent(ReceivingUiEvent.Scanned("6221112223334"))
        advanceUntilIdle()
        vm.onEvent(ReceivingUiEvent.QuantityChanged("8"))
        vm.onEvent(ReceivingUiEvent.ConfirmLine)
        advanceUntilIdle()

        val lineId = vm.state.value.lines.single().lineId
        vm.onEvent(ReceivingUiEvent.PrintLineLabels(lineId = lineId, copies = 5))
        advanceUntilIdle()

        assertEquals(1, printerTransport.sentPayloads.size)
        val tspl = printerTransport.sentPayloads.single().decodeToString()
        assertTrue(tspl.contains("PRINT 5,1"))
    }
}
