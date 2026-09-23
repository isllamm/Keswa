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
    fun `the labels that carry a value are translated too`() {
        // Methods are not in STRING_READERS, so they get their own check — the point of the whole
        // file is that nothing is left in English by omission.
        assertTrue(arabic.importRows(42) != english.importRows(42))
        assertTrue(arabic.takeAmount("1.00") != english.takeAmount("1.00"))
        assertTrue(arabic.refundAmount("1.00") != english.refundAmount("1.00"))
        assertTrue(arabic.newSecretAtLeast(8) != english.newSecretAtLeast(8))
        assertTrue(arabic.wasPrice("1.00") != english.wasPrice("1.00"))
        assertTrue(arabic.onlyInStock(1) != english.onlyInStock(1))
        assertTrue(arabic.chooseNewSecret(isPin = true) != english.chooseNewSecret(isPin = true))
        assertTrue(arabic.chooseNewSecret(isPin = false) != english.chooseNewSecret(isPin = false))

        assertTrue(arabic.notInCatalogue("X") != english.notInCatalogue("X"))
        assertTrue(arabic.shortBy("1.00") != english.shortBy("1.00"))
        assertTrue(arabic.salePrinted(1) != english.salePrinted(1))
        assertTrue(arabic.receivedPieces(3) != english.receivedPieces(3))
        assertTrue(arabic.printerDidNotAnswer("x") != english.printerDidNotAnswer("x"))
        assertTrue(arabic.paymentReceived("1.00") != english.paymentReceived("1.00"))
        assertTrue(arabic.soldBelowStock("S", 1, 0) != english.soldBelowStock("S", 1, 0))

        // And the value itself still appears, in both.
        assertTrue("42" in arabic.importRows(42))
        assertTrue("42" in english.importRows(42))
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
    { it.scanTheGarment }, { it.changeNegativeToWriteOff }, { it.reasonRequired }, { it.thisItemsLedger },
    { it.categories }, { it.searchProducts }, { it.newCategory }, { it.addTopLevel },
    { it.addUnderSelected }, { it.newProduct }, { it.create }, { it.blindByDesign },
    { it.startACount }, { it.scanThenTypeShelf }, { it.noteOptional }, { it.postCount },
    { it.discard }, { it.counted }, { it.howManyOnShelf }, { it.record },
    { it.nameOrPhone }, { it.newShort }, { it.amount }, { it.receivePayment },
    { it.howOverdue }, { it.newCustomer }, { it.name }, { it.phone },
    { it.creditLimitBlankForCash }, { it.paymentTermsDays }, { it.rows }, { it.check },
    { it.imported }, { it.importAnother }, { it.retire }, { it.price },
    { it.save }, { it.supplierBarcode }, { it.eanThirteen }, { it.attach },
    { it.referenceInvoiceOrNote }, { it.supplier }, { it.startDelivery }, { it.scanToAddColourByColour },
    { it.piecesLabel }, { it.cost }, { it.postDelivery }, { it.printHangTags },
    { it.costMoved }, { it.recentDeliveries }, { it.quantity }, { it.unitCost },
    { it.add }, { it.scanReceiptQrOrNumber }, { it.refund }, { it.needsAnApproval },
    { it.getApproval }, { it.startOver }, { it.approvalNeeded }, { it.username },
    { it.password }, { it.approve }, { it.testPrint }, { it.testLabel },
    { it.host }, { it.port }, { it.backToTill }, { it.cashCounted },
    { it.closeShift }, { it.zReport }, { it.seller }, { it.admin },
    { it.whoIsOnTheTill }, { it.signIn }, { it.createOwnerAccount }, { it.yourName },
    { it.passwordAtLeastEight }, { it.createAccount }, { it.writeThisDown }, { it.iHaveWrittenItDown },
    { it.chooseNewPassword }, { it.chooseNewPin }, { it.saveAndContinue }, { it.openingFloat },
    { it.openShift }, { it.shiftOpen }, { it.heldSales }, { it.resume },
    { it.cashHandedOver }, { it.addCash }, { it.card }, { it.addCard },
    { it.complete }, { it.detailsResetByAdmin },
    { it.receiptPrinter }, { it.receiptPrinterNote }, { it.labelPrinter },
    { it.labelPrinterNote }, { it.widthMm }, { it.heightMm },
    { it.barcodeScanner }, { it.barcodeScannerNote }, { it.maxGapMs },
    { it.countTheDrawerFirst }, { it.changeGiven }, { it.refundedInCash },
    { it.expectedInDrawer }, { it.salesStillWorkNoZ }, { it.lockedTryShortly },
    { it.newInstallationNobody }, { it.recoveryOnlyWayBack }, { it.eachColourOwnSkuLong },
    { it.addAColour }, { it.scanCodeOnGarment }, { it.youDefineThisTree },
    { it.adjustNoteLong }, { it.appendOnlyCorrection }, { it.pasteSpreadsheetNote },
    { it.nothingWasImported }, { it.oneBadRowNone }, { it.stockArrivedAsDelivery },
    { it.blindCountNote }, { it.nothingMovesUntilPosted }, { it.noSupplier },
    { it.weightedAverageNote }, { it.noCustomersYet }, { it.cashOnlyShort },
    { it.cashOnlyNoLimit }, { it.takeAPayment }, { it.againstAccountNote },
    { it.accountHistory }, { it.notYetDue }, { it.overSixtyDays },
    { it.allReturned }, { it.outsideWindowAdmin }, { it.adminCanTakeBack },
)
