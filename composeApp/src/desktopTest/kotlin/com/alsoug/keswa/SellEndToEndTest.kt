package com.alsoug.keswa

import androidx.room.Room
import com.alsoug.keswa.core.coroutines.DispatcherProvider
import com.alsoug.keswa.core.database.KeswaDatabase
import com.alsoug.keswa.core.database.entities.StockMovementEntity
import com.alsoug.keswa.core.database.getKeswaDatabase
import com.alsoug.keswa.core.domain.model.BarcodeSource
import com.alsoug.keswa.core.domain.model.MovementReason
import com.alsoug.keswa.core.domain.model.SecretKind
import com.alsoug.keswa.core.domain.model.TenderMethod
import com.alsoug.keswa.core.domain.model.UserRole
import com.alsoug.keswa.core.domain.money.Money
import com.alsoug.keswa.core.domain.repository.ICategoryRepository
import com.alsoug.keswa.core.domain.repository.ILocationRepository
import com.alsoug.keswa.core.domain.repository.IPriceRepository
import com.alsoug.keswa.core.domain.repository.IProductRepository
import com.alsoug.keswa.core.domain.repository.ISaleRepository
import com.alsoug.keswa.core.domain.repository.IUserRepository
import com.alsoug.keswa.core.domain.repository.IVariantRepository
import com.alsoug.keswa.core.session.ISessionManager
import com.alsoug.keswa.di.initKoin
import com.alsoug.keswa.features.catalog.domain.usecase.SeedShopUseCase
import com.alsoug.keswa.features.sell.domain.model.Basket
import com.alsoug.keswa.features.sell.domain.usecase.CompleteSaleUseCase
import com.alsoug.keswa.features.sell.domain.usecase.FindSellableUseCase
import com.alsoug.keswa.features.sell.domain.usecase.LookupResult
import com.alsoug.keswa.features.sell.domain.usecase.ResolveTillContextUseCase
import com.alsoug.keswa.features.sell.domain.usecase.SaleResult
import com.alsoug.keswa.features.sell.domain.usecase.Tender
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.koin.core.Koin
import org.koin.core.context.stopKoin
import org.koin.dsl.module

/**
 * A fresh install, from empty database to a sale in the ledger, through the **real composition
 * root**.
 *
 * The unit tests each prove one rule with the rest faked out. This proves the thing a shop
 * actually does, with nothing faked but the database file — and it is the only test that would
 * catch a phase's worth of correct pieces wired together wrongly.
 */
class SellEndToEndTest {

    private val koin: Koin = initKoin().koin.also { koin ->
        koin.loadModules(
            listOf(
                module {
                    single<KeswaDatabase> {
                        getKeswaDatabase(
                            Room.inMemoryDatabaseBuilder<KeswaDatabase>(),
                            get<DispatcherProvider>(),
                        )
                    }
                },
            ),
        )
    }

    @AfterTest
    fun tearDown() {
        koin.get<KeswaDatabase>().close()
        stopKoin()
    }

    private companion object {
        const val BARCODE = "2000000000015"
    }

    /** What startup does on a fresh install: colours, a root category, a location, a price list. */
    private suspend fun seedFreshInstall() {
        koin.get<SeedShopUseCase>().invoke().getOrThrow()
    }

    private suspend fun signInSeller() {
        val users = koin.get<IUserRepository>()
        val seller = users.create(
            id = "usr-seller",
            username = "sara",
            displayName = "Sara",
            displayNameAr = "سارة",
            role = UserRole.SELLER,
            secretKind = SecretKind.PIN,
            secretHash = "unused-here",
            secretSalt = "00000000",
        ).getOrThrow()
        koin.get<ISessionManager>().signIn(seller, atMillis = 1_757_000_000_000)
    }

    /** One priced, barcoded shirt with ten on the shelf. */
    private suspend fun stockOneShirt(): String {
        val categoryId = koin.get<ICategoryRepository>().getTree().getOrThrow().first().id
        val product = koin.get<IProductRepository>()
            .create("prod-1", "Round-neck t-shirt", "تيشيرت رقبة دائرية", categoryId)
            .getOrThrow()

        val variants = koin.get<IVariantRepository>()
        val variant = variants
            .addColour("var-1", product.id, "colour-navy", "KSW-TSH-001-NV", Money.ofPounds(120))
            .getOrThrow()
        variants.attachBarcode(BARCODE, variant.id, BarcodeSource.SUPPLIER, isPrimary = true)
            .getOrThrow()

        val priceList = koin.get<IPriceRepository>().defaultList().getOrThrow()!!
        koin.get<IPriceRepository>()
            .setPrice("price-1", variant.id, priceList.id, Money.ofPounds(180), from = 0)
            .getOrThrow()

        val locationId = koin.get<ILocationRepository>().default().getOrThrow()!!.id
        koin.get<KeswaDatabase>().stockLedgerDao().record(
            StockMovementEntity(
                id = "mov-1",
                variantId = variant.id,
                locationId = locationId,
                quantity = 10,
                reason = MovementReason.RECEIPT,
                refType = null,
                refId = null,
                occurredAt = 1_756_000_000_000,
                userId = "usr-seller",
            ),
        )
        return variant.id
    }

    @Test
    fun `a fresh install can scan, tender and record a sale`(): Unit = runBlocking {
        seedFreshInstall()
        signInSeller()
        val variantId = stockOneShirt()

        // The three things a fresh install must already have, or nothing below works.
        val till = koin.get<ResolveTillContextUseCase>().invoke().getOrThrow()
        assertTrue(till.locationId.isNotBlank())
        assertTrue(till.priceListId.isNotBlank())

        // Scan
        val found = koin.get<FindSellableUseCase>().byBarcode(BARCODE, till).getOrThrow()
        val item = assertIs<LookupResult.Found>(found).item
        assertEquals(Money.ofPounds(180), item.price)
        assertEquals(10, item.onHand)

        // Cart
        val basket = Basket().add(item, quantity = 2)

        // Tender — 400 handed over against 360
        val result = koin.get<CompleteSaleUseCase>().invoke(
            basket = basket,
            tenders = listOf(
                Tender(TenderMethod.CASH, Money.ofPounds(360), Money.ofPounds(400)),
            ),
            context = till,
            shiftId = null,
            vatBasisPoints = 0,
        ).getOrThrow()

        val sale = assertIs<SaleResult.Completed>(result).sale
        assertEquals(1L, sale.receiptNumber)
        assertEquals(Money.ofPounds(360), sale.total)
        assertEquals(Money.ofPounds(40), sale.change)
        assertEquals("usr-seller", sale.userId)

        // Ledger
        val database = koin.get<KeswaDatabase>()
        assertEquals(8, database.stockLedgerDao().getOnHand(variantId, till.locationId)?.quantity)

        // And it can be read back whole, which is what a reprint and a Phase 8 return will do.
        val reread = koin.get<ISaleRepository>().getById(sale.id).getOrThrow()!!
        assertEquals(1, reread.lines.size)
        assertEquals(1, reread.payments.size)
        assertEquals(2, reread.itemCount)
    }

    @Test
    fun `an unpriced variant is refused rather than rung up as nothing`(): Unit = runBlocking {
        seedFreshInstall()
        signInSeller()

        val categoryId = koin.get<ICategoryRepository>().getTree().getOrThrow().first().id
        val product = koin.get<IProductRepository>()
            .create("prod-2", "Unpriced shirt", "قميص", categoryId).getOrThrow()
        val variants = koin.get<IVariantRepository>()
        variants.addColour("var-2", product.id, "colour-white", "KSW-SHT-002-WH", Money.ofPounds(90))
            .getOrThrow()
        variants.attachBarcode("2000000000022", "var-2", BarcodeSource.SUPPLIER, isPrimary = true)
            .getOrThrow()

        val till = koin.get<ResolveTillContextUseCase>().invoke().getOrThrow()
        val found = koin.get<FindSellableUseCase>().byBarcode("2000000000022", till).getOrThrow()

        assertIs<LookupResult.NotPriced>(found)
    }

    @Test
    fun `an unknown barcode is simply not found`(): Unit = runBlocking {
        seedFreshInstall()
        signInSeller()

        val till = koin.get<ResolveTillContextUseCase>().invoke().getOrThrow()

        assertIs<LookupResult.NotFound>(
            koin.get<FindSellableUseCase>().byBarcode("9999999999999", till).getOrThrow(),
        )
    }
}
