package com.alsoug.keswa.core.designsystem

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The compiler already guarantees Arabic implements every property — that is why [Strings] is an
 * interface rather than a map. What it cannot guarantee is that somebody satisfied one by pasting
 * the English in, which is how a second language quietly dies.
 */
class StringsTest {

    private val english = EnglishStrings
    private val arabic = ArabicStrings

    @Test
    fun `Arabic is translated, not the English pasted across`() {
        // The one string that is legitimately identical: an abbreviation Egyptian receipts already
        // print in Arabic letters on both sides of the till.
        val shared = setOf(english.vat)
        val untranslated = STRING_READERS
            .map { read -> read(english) to read(arabic) }
            .filter { (en, ar) -> en == ar && en !in shared }
            .map { it.first }

        assertEquals(emptyList(), untranslated, "still in English on the Arabic side")
    }

    @Test
    fun `every Arabic string is written in Arabic letters`() {
        val letters = '؀'..'ۿ'
        val suspicious = STRING_READERS
            .map { it(arabic) }
            .filter { value -> value.none { character -> character in letters } }

        assertEquals(emptyList(), suspicious)
    }

    @Test
    fun `Arabic reads right to left and an unknown code still has words`() {
        assertTrue(KeswaLanguage.ARABIC.layoutDirection != KeswaLanguage.ENGLISH.layoutDirection)
        assertEquals(KeswaLanguage.ARABIC, KeswaLanguage.ofCode("ar"))
        assertEquals(KeswaLanguage.ENGLISH, KeswaLanguage.ofCode(null))
        assertEquals(KeswaLanguage.ENGLISH, KeswaLanguage.ofCode("fr"))
    }

    @Test
    fun `a week has seven days in both languages`() {
        assertEquals(7, english.weekdays.size)
        assertEquals(7, arabic.weekdays.size)
    }
}

/**
 * Every string, as readers.
 *
 * Written out rather than reflected over: Kotlin has no common-source reflection, and this test
 * has to keep working if `:core` ever gains a target that is not the JVM.
 */
private val STRING_READERS: List<(Strings) -> String> = listOf(
    { it.appName }, { it.till }, { it.tillHint }, { it.returns }, { it.returnsHint },
    { it.stockroom }, { it.stockroomHint }, { it.catalogue }, { it.catalogueHint },
    { it.customers }, { it.customersHint }, { it.shift }, { it.shiftHint },
    { it.numbers }, { it.numbersHint }, { it.signOut }, { it.language },
    { it.search }, { it.clear }, { it.apply }, { it.cancel }, { it.back },
    { it.scanOrSearch }, { it.scanToStart }, { it.scanToStartHint }, { it.takePayment },
    { it.discountOrHold }, { it.fewerOptions }, { it.orderDiscount }, { it.holdAs }, { it.hold },
    { it.subtotal }, { it.discount }, { it.vat }, { it.total }, { it.pieces }, { it.noPrice },
    { it.reprint },
    { it.returnsSubtitle }, { it.findTheReceipt }, { it.findTheReceiptHint },
    { it.returnDone }, { it.returnDoneHint },
    { it.stockroomSubtitle }, { it.receiving }, { it.receivingHint }, { it.stockCount },
    { it.stockCountHint }, { it.adjust }, { it.adjustHint }, { it.importCatalogue },
    { it.importCatalogueHint }, { it.printersAndScanner }, { it.startADelivery },
    { it.startADeliveryHint }, { it.scanFirstCarton }, { it.scanFirstCartonHint },
    { it.nothingCounted }, { it.nothingCountedHint },
    { it.catalogueSubtitle }, { it.nothingInCategory }, { it.nothingInCategoryHint },
    { it.eachColourItsOwnSku },
    { it.customersSubtitle }, { it.pickACustomer }, { it.pickACustomerHint },
    { it.shiftSubtitle }, { it.noShiftOpen }, { it.noShiftOpenHint },
    { it.settings }, { it.settingsSubtitle },
    { it.numbersSubtitle }, { it.forTheOwner }, { it.forTheOwnerHint },
    { it.today }, { it.sevenDays }, { it.thirtyDays }, { it.ninetyDays }, { it.netOf },
    { it.sales }, { it.basket }, { it.units }, { it.returnRate }, { it.margin },
    { it.revenue }, { it.colourPerformance }, { it.sellThrough }, { it.busyHours },
    { it.topMovers }, { it.soldLegend }, { it.onHandLegend },
    { it.colourPerformanceNote }, { it.sellThroughNote },
    { it.soldSuffix }, { it.throughSuffix }, { it.leftSuffix }, { it.marginPrefix },
    { it.nothingSoldPeriod }, { it.noStockToCompare }, { it.noTradingHours },
    { it.nothingReceived }, { it.nothingHasSold },
)
