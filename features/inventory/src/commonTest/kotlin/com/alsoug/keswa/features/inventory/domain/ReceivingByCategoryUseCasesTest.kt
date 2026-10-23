package com.alsoug.keswa.features.inventory.domain

import com.alsoug.keswa.core.domain.model.Category
import com.alsoug.keswa.core.domain.model.Permission
import com.alsoug.keswa.core.domain.model.SellableItem
import com.alsoug.keswa.core.domain.model.Session
import com.alsoug.keswa.core.domain.model.User
import com.alsoug.keswa.core.domain.model.UserRole
import com.alsoug.keswa.core.domain.money.Money
import com.alsoug.keswa.core.error.Error
import com.alsoug.keswa.core.session.ISessionManager
import com.alsoug.keswa.features.inventory.domain.usecase.AddLinesResult
import com.alsoug.keswa.features.inventory.domain.usecase.AddReceiptLinesUseCase
import com.alsoug.keswa.features.inventory.domain.usecase.BrowseCategoryUseCase
import com.alsoug.keswa.features.inventory.domain.usecase.EnsureVariantBarcodeUseCase
import com.alsoug.keswa.features.inventory.domain.usecase.EntryProblem
import com.alsoug.keswa.features.inventory.domain.usecase.FindStockItemUseCase
import com.alsoug.keswa.features.inventory.domain.usecase.PickedEntry
import com.alsoug.keswa.features.inventory.domain.usecase.PostReceiptUseCase
import com.alsoug.keswa.features.inventory.fakes.FakeCategoryRepository
import com.alsoug.keswa.features.inventory.fakes.FakePriceRepository
import com.alsoug.keswa.features.inventory.fakes.FakeSellableRepository
import com.alsoug.keswa.features.inventory.fakes.FakeStockReceiptRepository
import com.alsoug.keswa.features.inventory.fakes.FakeVariantRepository
import com.alsoug.keswa.features.inventory.fakes.SequentialIds
import com.alsoug.keswa.features.inventory.fakes.fullPermissionSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest

class ReceivingByCategoryUseCasesTest {

    private val categories = FakeCategoryRepository()
        .clear()
        .withCategory(
            Category("cat-men", null, "Men", "رجالي", "/cat-men/", 0, 0, isActive = true),
        )
        .withCategory(
            Category("cat-women", null, "Women", "نسائي", "/cat-women/", 0, 1, isActive = true),
        )
        .withCategory(
            Category("cat-shirts", "cat-men", "Shirts", "قمصان", "/cat-men/cat-shirts/", 1, 0, isActive = true),
        )
        .withCategory(
            Category("cat-hidden", "cat-men", "Winter", "شتوي", "/cat-men/cat-hidden/", 1, 1, isActive = false),
        )

    private val navyTee = SellableItem(
        variantId = "var-navy",
        productId = "prod-tee",
        sku = "TEE-NV",
        name = "Plain T-Shirt",
        nameAr = "تيشيرت سادة",
        colourName = "Navy",
        colourNameAr = "كحلي",
        cost = Money.ofPounds(120),
        price = Money.ofPounds(200),
        onHand = 10,
    )

    private val whiteTee = SellableItem(
        variantId = "var-white",
        productId = "prod-tee",
        sku = "TEE-WH",
        name = "Plain T-Shirt",
        nameAr = "تيشيرت سادة",
        colourName = "White",
        colourNameAr = "أبيض",
        cost = Money.ofPounds(120),
        price = Money.ofPounds(200),
        onHand = 5,
    )

    private val unpricedItem = SellableItem(
        variantId = "var-unpriced",
        productId = "prod-new",
        sku = "NEW-BLK",
        name = "Linen Shirt",
        nameAr = "قميص كتان",
        colourName = "Black",
        colourNameAr = "أسود",
        cost = Money.ofPounds(150),
        price = null,
        onHand = 0,
    )

    private val sellables = FakeSellableRepository()
        .withItem(navyTee, categoryId = "cat-men")
        .withItem(whiteTee, categoryId = "cat-men")
        .withItem(unpricedItem, categoryId = "cat-shirts")

    private val prices = FakePriceRepository()
    private val find = FindStockItemUseCase(sellables, prices) { 1_000_000L }
    private val browse = BrowseCategoryUseCase(categories, find)

    private fun sessionWith(vararg perms: Permission): ISessionManager {
        val user = User("u-1", "receiver", "Receiver", "مستلم", UserRole.SELLER)
        return object : ISessionManager {
            override val current: StateFlow<Session?> =
                MutableStateFlow(Session(user, 1_000L, perms.toSet()))
            override val isLocked: StateFlow<Boolean> = MutableStateFlow(false)
            override fun signIn(user: User, atMillis: Long) = Unit
            override fun lock() = Unit
            override fun unlock() = Unit
            override fun signOut() = Unit
        }
    }

    @Test
    fun `the top level lists the top categories and no items`() = runTest {
        val level = browse(categoryId = null, locationId = "loc-1").getOrThrow()

        assertEquals(listOf("cat-men", "cat-women"), level.children.map { it.id })
        assertTrue(level.items.isEmpty())
    }

    @Test
    fun `a category lists its active children and the colours filed directly in it`() = runTest {
        val level = browse(categoryId = "cat-men", locationId = "loc-1").getOrThrow()

        assertEquals(listOf("cat-shirts"), level.children.map { it.id })
        assertEquals(listOf("var-navy", "var-white"), level.items.map { it.variantId })
    }

    @Test
    fun `a blank quantity is skipped while 0, abc and 1_5 are NOT_A_QUANTITY`() = runTest {
        val receipts = FakeStockReceiptRepository()
        val variants = FakeVariantRepository()
        val addLines = AddReceiptLinesUseCase(
            receipts = receipts,
            sessions = fullPermissionSession(),
            ensureBarcode = EnsureVariantBarcodeUseCase(variants),
            ids = SequentialIds(),
        )
        receipts.createDraft("rec-1", "INV-1", "Supplier", "loc-1", "u-1", 1_000L)

        // Blank row skipped while valid row is added
        val added = addLines(
            "rec-1",
            listOf(
                PickedEntry(navyTee, quantity = "  ", cost = "120", price = "200"),
                PickedEntry(whiteTee, quantity = "12", cost = "120", price = "200"),
            ),
        ).getOrThrow()
        assertIs<AddLinesResult.Added>(added)
        assertEquals(1, added.lines)
        assertEquals(listOf("var-white"), added.receipt.lines.map { it.variantId })

        // 0, abc, 1.5 are rejected as NOT_A_QUANTITY
        for (badQty in listOf("0", "abc", "1.5", "-3")) {
            val rejected = addLines(
                "rec-1",
                listOf(PickedEntry(navyTee, quantity = badQty, cost = "120", price = "200")),
            ).getOrThrow()
            assertIs<AddLinesResult.Rejected>(rejected)
            assertEquals(mapOf("var-navy" to EntryProblem.NOT_A_QUANTITY), rejected.problems)
        }
    }

    @Test
    fun `a bad cost or price is NOT_AN_AMOUNT and nothing is added`() = runTest {
        val receipts = FakeStockReceiptRepository()
        val variants = FakeVariantRepository()
        val addLines = AddReceiptLinesUseCase(
            receipts = receipts,
            sessions = fullPermissionSession(),
            ensureBarcode = EnsureVariantBarcodeUseCase(variants),
            ids = SequentialIds(),
        )
        receipts.createDraft("rec-1", "INV-1", "Supplier", "loc-1", "u-1", 1_000L)

        val result = addLines(
            "rec-1",
            listOf(
                PickedEntry(navyTee, quantity = "10", cost = "", price = "200"),
                PickedEntry(whiteTee, quantity = "10", cost = "120", price = "-5"),
                PickedEntry(unpricedItem, quantity = "5", cost = "150", price = "250"),
            ),
        ).getOrThrow()

        assertIs<AddLinesResult.Rejected>(result)
        assertEquals(
            mapOf(
                "var-navy" to EntryProblem.NOT_AN_AMOUNT,
                "var-white" to EntryProblem.NOT_AN_AMOUNT,
            ),
            result.problems,
        )
        assertTrue(receipts.getById("rec-1").getOrThrow()!!.lines.isEmpty())
    }

    @Test
    fun `a blank price on an item with no price is PRICE_REQUIRED`() = runTest {
        val receipts = FakeStockReceiptRepository()
        val variants = FakeVariantRepository()
        val addLines = AddReceiptLinesUseCase(
            receipts = receipts,
            sessions = fullPermissionSession(),
            ensureBarcode = EnsureVariantBarcodeUseCase(variants),
            ids = SequentialIds(),
        )
        receipts.createDraft("rec-1", "INV-1", "Supplier", "loc-1", "u-1", 1_000L)

        val result = addLines(
            "rec-1",
            listOf(PickedEntry(unpricedItem, quantity = "8", cost = "150", price = "")),
        ).getOrThrow()

        assertIs<AddLinesResult.Rejected>(result)
        assertEquals(mapOf("var-unpriced" to EntryProblem.PRICE_REQUIRED), result.problems)
        assertTrue(receipts.getById("rec-1").getOrThrow()!!.lines.isEmpty())
    }

    @Test
    fun `a blank price on a priced item keeps the price with no sellPrice stored`() = runTest {
        val receipts = FakeStockReceiptRepository()
        val variants = FakeVariantRepository()
        val addLines = AddReceiptLinesUseCase(
            receipts = receipts,
            sessions = fullPermissionSession(),
            ensureBarcode = EnsureVariantBarcodeUseCase(variants),
            ids = SequentialIds(),
        )
        receipts.createDraft("rec-1", "INV-1", "Supplier", "loc-1", "u-1", 1_000L)

        val result = addLines(
            "rec-1",
            listOf(PickedEntry(navyTee, quantity = "8", cost = "120", price = "")),
        ).getOrThrow()

        assertIs<AddLinesResult.Added>(result)
        assertNull(result.receipt.lines.single().sellPrice)
    }

    @Test
    fun `the same price as today stores no sellPrice`() = runTest {
        val receipts = FakeStockReceiptRepository()
        val variants = FakeVariantRepository()
        val addLines = AddReceiptLinesUseCase(
            receipts = receipts,
            sessions = fullPermissionSession(),
            ensureBarcode = EnsureVariantBarcodeUseCase(variants),
            ids = SequentialIds(),
        )
        receipts.createDraft("rec-1", "INV-1", "Supplier", "loc-1", "u-1", 1_000L)

        val result = addLines(
            "rec-1",
            listOf(PickedEntry(navyTee, quantity = "8", cost = "120", price = "200.00")),
        ).getOrThrow()

        assertIs<AddLinesResult.Added>(result)
        assertNull(result.receipt.lines.single().sellPrice)
    }

    @Test
    fun `a changed price without MANAGE_CATALOGUE is refused before the repository is touched`() = runTest {
        val receipts = FakeStockReceiptRepository()
        val variants = FakeVariantRepository()
        val receiverOnly = sessionWith(Permission.RECEIVE_STOCK)
        val addLines = AddReceiptLinesUseCase(
            receipts = receipts,
            sessions = receiverOnly,
            ensureBarcode = EnsureVariantBarcodeUseCase(variants),
            ids = SequentialIds(),
        )
        receipts.createDraft("rec-1", "INV-1", "Supplier", "loc-1", "u-1", 1_000L)

        val result = addLines(
            "rec-1",
            listOf(PickedEntry(navyTee, quantity = "8", cost = "120", price = "240")),
        )

        assertIs<Error.ForbiddenAccess>(result.exceptionOrNull())
        assertTrue(receipts.getById("rec-1").getOrThrow()!!.lines.isEmpty())
    }

    @Test
    fun `every added item has a barcode afterwards`() = runTest {
        val receipts = FakeStockReceiptRepository()
        val variants = FakeVariantRepository()
        val addLines = AddReceiptLinesUseCase(
            receipts = receipts,
            sessions = fullPermissionSession(),
            ensureBarcode = EnsureVariantBarcodeUseCase(variants),
            ids = SequentialIds(),
        )
        receipts.createDraft("rec-1", "INV-1", "Supplier", "loc-1", "u-1", 1_000L)

        val result = addLines(
            "rec-1",
            listOf(
                PickedEntry(navyTee, quantity = "8", cost = "120", price = "200"),
                PickedEntry(unpricedItem, quantity = "5", cost = "150", price = "260"),
            ),
        ).getOrThrow()

        assertIs<AddLinesResult.Added>(result)
        assertNotNull(variants.barcodesFor("var-navy").getOrThrow().firstOrNull())
        assertNotNull(variants.barcodesFor("var-unpriced").getOrThrow().firstOrNull())
        assertEquals(Money.ofPounds(260), result.receipt.lines.first { it.variantId == "var-unpriced" }.sellPrice)
    }

    @Test
    fun `PostReceiptUseCase refuses a priced receipt without MANAGE_CATALOGUE`() = runTest {
        val receipts = FakeStockReceiptRepository()
        receipts.createDraft("rec-1", "INV-1", "Supplier", "loc-1", "u-1", 1_000L)
        receipts.putLine("line-1", "rec-1", "var-navy", 10, Money.ofPounds(120), sellPrice = Money.ofPounds(240))

        val post = PostReceiptUseCase(
            receipts = receipts,
            sessions = sessionWith(Permission.RECEIVE_STOCK),
            now = { 1_000L },
        )

        val result = post("rec-1")

        assertIs<Error.ForbiddenAccess>(result.exceptionOrNull())
        assertTrue(receipts.getById("rec-1").getOrThrow()!!.isDraft)
    }
}
