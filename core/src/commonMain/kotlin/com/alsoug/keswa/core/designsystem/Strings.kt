package com.alsoug.keswa.core.designsystem

import androidx.compose.runtime.Immutable
import com.alsoug.keswa.core.domain.model.LedgerEntryType
import com.alsoug.keswa.core.domain.model.MovementReason
import com.alsoug.keswa.core.domain.model.Permission

/**
 * Every word the interface says, in one place, per language.
 *
 * A plain Kotlin interface rather than Compose resources. Resources generate a `Res` class **per
 * module**, so nine feature modules would mean nine `Res` imports and nine places to add a string —
 * and a string used by two screens would be declared twice, in two languages, with nothing to say
 * when the copies drifted. This is one file the compiler checks: add a property and Arabic will not
 * build until it is translated, which is the only mechanism that actually keeps a second language
 * alive.
 *
 * It does **not** carry the data's own bilingual fields. Every catalogue row already has `nameAr`
 * beside `name` — a product is named by the shop, not by this file.
 */
@Immutable
interface Strings {

    // Shell
    val appName: String
    val till: String
    val tillHint: String
    val returns: String
    val returnsHint: String
    val stockroom: String
    val stockroomHint: String
    val catalogue: String
    val catalogueHint: String
    val customers: String
    val customersHint: String
    val shift: String
    val shiftHint: String
    val numbers: String
    val numbersHint: String
    val signOut: String
    val language: String

    // Common
    val search: String
    val clear: String
    val apply: String
    val cancel: String
    val back: String

    // Till
    val scanOrSearch: String
    val scanToStart: String
    val scanToStartHint: String
    val takePayment: String
    val discountOrHold: String
    val fewerOptions: String
    val orderDiscount: String
    val holdAs: String
    val hold: String
    val subtotal: String
    val discount: String
    val vat: String
    val total: String
    val pieces: String
    val noPrice: String
    val reprint: String

    // Returns
    val returnsSubtitle: String
    val findTheReceipt: String
    val findTheReceiptHint: String
    val returnDone: String
    val returnDoneHint: String

    // Stockroom
    val stockroomSubtitle: String
    val receiving: String
    val receivingHint: String
    val stockCount: String
    val stockCountHint: String
    val adjust: String
    val adjustHint: String
    val importCatalogue: String
    val importCatalogueHint: String
    val printersAndScanner: String
    val startADelivery: String
    val startADeliveryHint: String
    val scanFirstCarton: String
    val scanFirstCartonHint: String
    val nothingCounted: String
    val nothingCountedHint: String

    // Catalogue
    val catalogueSubtitle: String
    val nothingInCategory: String
    val nothingInCategoryHint: String
    val eachColourItsOwnSku: String

    // Customers
    val customersSubtitle: String
    val pickACustomer: String
    val pickACustomerHint: String

    // Shift
    val shiftSubtitle: String
    val noShiftOpen: String
    val noShiftOpenHint: String

    // Settings
    val settings: String
    val settingsSubtitle: String

    // Dashboard
    val numbersSubtitle: String
    val forTheOwner: String
    val forTheOwnerHint: String
    val today: String
    val sevenDays: String
    val thirtyDays: String
    val ninetyDays: String
    val netOf: String
    val sales: String
    val basket: String
    val units: String
    val returnRate: String
    val margin: String
    val revenue: String
    val colourPerformance: String
    val sellThrough: String
    val busyHours: String
    val topMovers: String
    val soldLegend: String
    val onHandLegend: String
    val colourPerformanceNote: String
    val sellThroughNote: String
    val soldSuffix: String
    val throughSuffix: String
    val leftSuffix: String
    val marginPrefix: String
    val nothingSoldPeriod: String
    val noStockToCompare: String
    val noTradingHours: String
    val nothingReceived: String
    val nothingHasSold: String

    /** Sunday first, matching SQLite's `%w` and the Egyptian working week. */
    val weekdays: List<String>

    // Forms — the labels on the fields somebody types into.
    val scanTheGarment: String
    val changeNegativeToWriteOff: String
    val reasonRequired: String
    val thisItemsLedger: String
    val categories: String
    val searchProducts: String
    val newCategory: String
    val addTopLevel: String
    val addUnderSelected: String
    val newProduct: String
    val create: String
    val blindByDesign: String
    val startACount: String
    val scanThenTypeShelf: String
    val noteOptional: String
    val postCount: String
    val discard: String
    val counted: String
    val howManyOnShelf: String
    val record: String
    val nameOrPhone: String
    val newShort: String
    val amount: String
    val receivePayment: String
    val howOverdue: String
    val newCustomer: String
    val name: String
    val phone: String
    val creditLimitBlankForCash: String
    val paymentTermsDays: String
    val rows: String
    val check: String
    val imported: String
    val importAnother: String
    val retire: String
    val price: String
    val save: String
    val supplierBarcode: String
    val eanThirteen: String
    val attach: String
    val referenceInvoiceOrNote: String
    val supplier: String
    val startDelivery: String
    val scanToAddColourByColour: String
    val piecesLabel: String
    val cost: String
    val postDelivery: String
    val printHangTags: String
    val costMoved: String
    val recentDeliveries: String
    val quantity: String
    val unitCost: String
    val add: String
    val scanReceiptQrOrNumber: String
    val refund: String
    val needsAnApproval: String
    val getApproval: String
    val startOver: String
    val approvalNeeded: String
    val username: String
    val password: String
    val approve: String
    val testPrint: String
    val testLabel: String
    val host: String
    val port: String
    val backToTill: String
    val cashCounted: String
    val closeShift: String
    val zReport: String
    val seller: String
    val admin: String
    val whoIsOnTheTill: String
    val signIn: String
    val createOwnerAccount: String
    val yourName: String
    val passwordAtLeastEight: String
    val createAccount: String
    val writeThisDown: String
    val iHaveWrittenItDown: String
    val chooseNewPassword: String
    val chooseNewPin: String
    val saveAndContinue: String
    val openingFloat: String
    val openShift: String
    val shiftOpen: String
    val heldSales: String
    val resume: String
    val cashHandedOver: String
    val addCash: String
    val card: String
    val addCard: String
    val complete: String
    val detailsResetByAdmin: String

    // Screen prose — the sentences that explain why a screen behaves as it does.
    val receiptPrinter: String
    val receiptPrinterNote: String
    val labelPrinter: String
    val labelPrinterNote: String
    val widthMm: String
    val heightMm: String
    val barcodeScanner: String
    val barcodeScannerNote: String
    val maxGapMs: String
    val countTheDrawerFirst: String
    val changeGiven: String
    val refundedInCash: String
    val expectedInDrawer: String
    val salesStillWorkNoZ: String
    val lockedTryShortly: String
    val newInstallationNobody: String
    val recoveryOnlyWayBack: String
    val eachColourOwnSkuLong: String
    val addAColour: String
    val scanCodeOnGarment: String
    val youDefineThisTree: String
    val adjustNoteLong: String
    val appendOnlyCorrection: String
    val pasteSpreadsheetNote: String
    val nothingWasImported: String
    val oneBadRowNone: String
    val stockArrivedAsDelivery: String
    val blindCountNote: String
    val nothingMovesUntilPosted: String
    val noSupplier: String
    val weightedAverageNote: String
    val noCustomersYet: String
    val cashOnlyShort: String
    val cashOnlyNoLimit: String
    val takeAPayment: String
    val againstAccountNote: String
    val accountHistory: String
    val notYetDue: String
    val overSixtyDays: String
    val allReturned: String
    val outsideWindowAdmin: String
    val adminCanTakeBack: String
    val generateBarcode: String
    val printLabel: String
    val gapMm: String
    val each: String
    val soldLabel: String
    val conditionSellable: String
    val conditionDamaged: String
    val salesCountLabel: String
    val voidedSales: String
    val grossSales: String
    val discountsLabel: String
    val netSales: String
    val floatLabel: String
    val cashLabel: String
    val cardLabel: String
    val returnsLabel: String
    val countedLabel: String
    val balanced: String
    val onAccount: String
    val oneToThirtyDays: String
    val thirtyOneToSixtyDays: String
    val outstanding: String
    val change: String
    val draft: String
    val approved: String

    /**
     * The handful of labels that carry a value inside them.
     *
     * Methods rather than a template string with a placeholder, because Arabic does not always put
     * the number where English does — "استيراد 42 صفًا" and "Import 42 rows" agree here, but a
     * format string would let the next one silently disagree.
     */
    fun importRows(count: Int): String
    fun takeAmount(amount: String): String
    fun refundAmount(amount: String): String
    fun chooseNewSecret(isPin: Boolean): String
    fun newSecretAtLeast(minimum: Int): String

    /** On a basket line: the price before an override, and a short-stock warning. */
    fun wasPrice(price: String): String
    fun onlyInStock(onHand: Int): String

    fun importedProducts(count: Int): String
    fun importedVariants(count: Int): String
    fun importedPieces(count: Int): String
    fun coloursCount(count: Int): String
    fun totalOnHand(count: Int): String
    fun countSummary(lines: Int, discrepancies: Int): String
    fun countedVsExpected(counted: Int, expected: Int?): String
    fun alreadyReturned(count: Int): String
    fun customerTerms(limit: String, terms: Int): String
    fun availableCredit(available: String, limit: String): String
    fun differenceAmount(amount: String): String
    fun salesOutsideShift(count: Int): String
    fun ledgerEntryType(type: LedgerEntryType): String
    fun receiptDaysAgo(receiptNumber: Long, daysSince: Long): String
    fun outsideReturnWindow(days: Int): String
    fun inStock(count: Int): String
    fun blockedByStock(onHand: Int): String
    fun importLineProblem(lineNumber: Int, message: String): String
    fun importPreviewSummary(rows: Int, pieces: Int): String
    fun importRowDetail(sku: String, cost: String, quantity: Int): String
    fun movementReason(reason: MovementReason): String
    fun permissionNeeded(permission: Permission): String

    // Messages — what a screen says back after somebody has done something.
    val somethingWentWrong: String
    val notAnAmount: String
    val notAQuantity: String
    val notANumberOfDays: String
    val incorrectDetails: String
    val accountCannotApprove: String
    val tooManyAttempts: String
    val settingsSaved: String
    val sentToPrinter: String
    val enterPrinterAddressFirst: String
    val couldNotCloseShift: String
    val saleVoided: String
    val tenderMoreThanZero: String
    val nothingToSell: String
    val customerIsCashOnly: String
    val pickCustomerBeforeCredit: String
    val updatedSignInAgain: String
    val signInFailed: String
    val colourAlreadyStocked: String
    val productNotFound: String
    val colourNotFound: String
    val colourRetired: String
    val barcodeAttached: String
    val barcodeAlreadyInUse: String
    val notValidEanThirteen: String
    val priceSet: String
    val noRetailPriceList: String
    val categoryAdded: String
    val pickACategoryFirst: String
    val startACountFirst: String
    val everythingMatched: String
    val startADeliveryFirst: String
    val noLabelPrinterConfigured: String
    val labelPrinterSilent: String
    val noSaleWithThatReceipt: String
    val saleAlreadyVoided: String
    val everythingCameBack: String
    val refundSavedPrinterSilent: String
    val nothingSelectedToReturn: String
    val couldNotLoadNumbers: String
    val heldConfirmation: String
    val shiftOpened: String
    val changeNotAllowed: String

    fun notInCatalogue(term: String): String
    fun hasNoPriceYet(sku: String): String
    fun skuCreated(sku: String): String
    fun adjustedBy(quantity: Int): String
    fun receivedPieces(pieces: Int): String
    fun tagsSent(tags: Int): String
    fun tagsSentSomeSkipped(tags: Int, skipped: Int): String
    fun drawerDiffersBy(amount: String): String
    fun importedSummary(skus: Int, pieces: Int): String
    fun linesDidNotMatch(lines: Int): String
    fun refundedReturn(amount: String, number: Long): String
    fun onlyLeftToReturn(description: String, returnable: Int): String
    fun shortBy(amount: String): String
    fun overCreditLimitBy(amount: String): String
    fun salePrinted(number: Long?): String
    fun saleNoPrinter(number: Long?): String
    fun salePrinterSilent(number: Long?): String
    fun linesNoLongerSellable(lines: Int): String
    fun printerDidNotAnswer(detail: String): String
    fun paymentReceived(amount: String): String
    fun soldBelowStock(sku: String, sold: Int, onHand: Int): String
}

object EnglishStrings : Strings {
    override val appName = "Keswa"
    override val till = "Till"
    override val tillHint = "Scan, charge, print"
    override val returns = "Returns"
    override val returnsHint = "Refunds and exchanges"
    override val stockroom = "Stockroom"
    override val stockroomHint = "Receiving, counts, adjustments"
    override val catalogue = "Catalogue"
    override val catalogueHint = "Products, colours, prices"
    override val customers = "Customers"
    override val customersHint = "Accounts and what is owed"
    override val shift = "Shift"
    override val shiftHint = "Float, takings, close"
    override val numbers = "Numbers"
    override val numbersHint = "What the shop is doing"
    override val signOut = "Sign out"
    override val language = "Language"

    override val search = "Search"
    override val clear = "Clear"
    override val apply = "Apply"
    override val cancel = "Cancel"
    override val back = "Back"

    override val scanOrSearch = "Scan or search — barcode, SKU or name"
    override val scanToStart = "Scan to start"
    override val scanToStartHint = "Or type a SKU or a name and press Search"
    override val takePayment = "Take payment"
    override val discountOrHold = "Discount or hold"
    override val fewerOptions = "Fewer options"
    override val orderDiscount = "Order discount"
    override val holdAs = "Hold as"
    override val hold = "Hold"
    override val subtotal = "Subtotal"
    override val discount = "Discount"
    override val vat = "VAT · ض.ق.م"
    override val total = "Total"
    override val pieces = "pcs"
    override val noPrice = "no price"
    override val reprint = "Reprint"

    override val returnsSubtitle = "Scan the receipt's code, or find the sale"
    override val findTheReceipt = "Find the receipt"
    override val findTheReceiptHint = "Scan the code on it, or enter the receipt number"
    override val returnDone = "Return done"
    override val returnDoneHint = "Scan another receipt when you are ready"

    override val stockroomSubtitle = "Everything that is not selling"
    override val receiving = "Receiving"
    override val receivingHint = "Book in a delivery, and let it set the cost"
    override val stockCount = "Stock count"
    override val stockCountHint = "Blind — the expected figure comes after"
    override val adjust = "Adjust stock"
    override val adjustHint = "Damage, theft, a sample given away"
    override val importCatalogue = "Import catalogue"
    override val importCatalogueHint = "A supplier's spreadsheet, validated as a whole"
    override val printersAndScanner = "Printers and scanner"
    override val startADelivery = "Start a delivery"
    override val startADeliveryHint = "Name the supplier and a reference, then scan what arrived"
    override val scanFirstCarton = "Scan the first carton"
    override val scanFirstCartonHint =
        "Each colour is counted separately — the cost follows from what you enter"
    override val nothingCounted = "Nothing counted yet"
    override val nothingCountedHint =
        "Scan a garment to add it. The expected figure is shown after you post."

    override val catalogueSubtitle = "Products, colours and prices"
    override val nothingInCategory = "Nothing in this category"
    override val nothingInCategoryHint = "Add a product, or pick another category"
    override val eachColourItsOwnSku = "Each colour is its own SKU"

    override val customersSubtitle = "Accounts, terms, and what is owed"
    override val pickACustomer = "Pick a customer"
    override val pickACustomerHint = "Their balance, ageing and history are all on one panel"

    override val shiftSubtitle = "Float, takings, and the count that closes it"
    override val noShiftOpen = "No shift is open"
    override val noShiftOpenHint = "Open one at the till to start counting takings against a float"

    override val settings = "Settings"
    override val settingsSubtitle = "Printers, scanner and the shop's own details"

    override val numbersSubtitle = "How the shop is doing"
    override val forTheOwner = "These figures are for the owner"
    override val forTheOwnerHint = "Ask an admin to sign in if you need them"
    override val today = "Today"
    override val sevenDays = "7 days"
    override val thirtyDays = "30 days"
    override val ninetyDays = "90 days"
    override val netOf = "net of"
    override val sales = "Sales"
    override val basket = "Basket"
    override val units = "Units"
    override val returnRate = "Returns"
    override val margin = "Margin"
    override val revenue = "Revenue"
    override val colourPerformance = "Colour performance"
    override val sellThrough = "Sell-through"
    override val busyHours = "Busy hours"
    override val topMovers = "Top movers"
    override val soldLegend = "Sold"
    override val onHandLegend = "On hand"
    override val colourPerformanceNote =
        "Sold against what is still on the rail. Cash tied up in the wrong colours is next " +
            "season's buying decision."
    override val sellThroughNote =
        "Against a 70% season target. Below it late in a season means discount now, not in January."
    override val soldSuffix = "sold"
    override val throughSuffix = "through"
    override val leftSuffix = "left"
    override val marginPrefix = "margin"
    override val nothingSoldPeriod = "Nothing sold in this period"
    override val noStockToCompare = "No stock to compare yet"
    override val noTradingHours = "No trading hours to show yet"
    override val nothingReceived = "Nothing received yet"
    override val nothingHasSold = "Nothing has sold yet"
    override val weekdays = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

    override val scanTheGarment = "Scan the garment"
    override val changeNegativeToWriteOff = "Change — negative to write off, e.g. -3"
    override val reasonRequired = "Reason — required"
    override val thisItemsLedger = "This item's ledger"
    override val categories = "Categories"
    override val searchProducts = "Search products"
    override val newCategory = "New category"
    override val addTopLevel = "Add top level"
    override val addUnderSelected = "Add under selected"
    override val newProduct = "New product"
    override val create = "Create"
    override val blindByDesign = "Blind by design"
    override val startACount = "Start a count"
    override val scanThenTypeShelf = "Scan a garment, then type what is on the shelf"
    override val noteOptional = "Note (optional)"
    override val postCount = "Post count"
    override val discard = "Discard"
    override val counted = "Counted"
    override val howManyOnShelf = "How many are on the shelf?"
    override val record = "Record"
    override val nameOrPhone = "Name or phone"
    override val newShort = "New"
    override val amount = "Amount"
    override val receivePayment = "Receive"
    override val howOverdue = "How overdue"
    override val newCustomer = "New customer"
    override val name = "Name"
    override val phone = "Phone"
    override val creditLimitBlankForCash = "Credit limit — blank for cash only"
    override val paymentTermsDays = "Payment terms, in days"
    override val rows = "Rows"
    override val check = "Check"
    override val imported = "Imported"
    override val importAnother = "Import another"
    override val retire = "Retire"
    override val price = "Price"
    override val save = "Save"
    override val supplierBarcode = "Supplier barcode"
    override val eanThirteen = "EAN-13"
    override val attach = "Attach"
    override val referenceInvoiceOrNote = "Reference — invoice or delivery note"
    override val supplier = "Supplier"
    override val startDelivery = "Start delivery"
    override val scanToAddColourByColour = "Scan a garment to add it — colour by colour"
    override val piecesLabel = "Pieces"
    override val cost = "Cost"
    override val postDelivery = "Post delivery"
    override val printHangTags = "Print hang tags"
    override val costMoved = "Cost moved"
    override val recentDeliveries = "Recent deliveries"
    override val quantity = "Quantity"
    override val unitCost = "Unit cost"
    override val add = "Add"
    override val scanReceiptQrOrNumber = "Scan the receipt QR, or type its number"
    override val refund = "Refund"
    override val needsAnApproval = "Needs an approval"
    override val getApproval = "Get approval"
    override val startOver = "Start over"
    override val approvalNeeded = "Approval needed"
    override val username = "Username"
    override val password = "Password"
    override val approve = "Approve"
    override val testPrint = "Test print"
    override val testLabel = "Test label"
    override val host = "Host"
    override val port = "Port"
    override val backToTill = "Back to till"
    override val cashCounted = "Cash counted"
    override val closeShift = "Close shift"
    override val zReport = "Z-report"
    override val seller = "Seller"
    override val admin = "Admin"
    override val whoIsOnTheTill = "Who is on the till"
    override val signIn = "Sign in"
    override val createOwnerAccount = "Create the owner account"
    override val yourName = "Your name"
    override val passwordAtLeastEight = "Password — at least 8 characters"
    override val createAccount = "Create account"
    override val writeThisDown = "Write this down"
    override val iHaveWrittenItDown = "I have written it down"
    override val chooseNewPassword = "Choose a new password"
    override val chooseNewPin = "Choose a new PIN"
    override val saveAndContinue = "Save and continue"
    override val openingFloat = "Opening float"
    override val openShift = "Open shift"
    override val shiftOpen = "Shift open"
    override val heldSales = "Held"
    override val resume = "Resume"
    override val cashHandedOver = "Cash handed over"
    override val addCash = "Add cash"
    override val card = "Card"
    override val addCard = "Add card"
    override val complete = "Complete"
    override val detailsResetByAdmin =
        "Your details were reset by an admin, who knows the temporary one."
    override val receiptPrinter = "Receipt printer"
    override val receiptPrinterNote = "ESC/POS over TCP 9100. Arabic prints as an image, so the firmware never sees it."
    override val labelPrinter = "Label printer"
    override val labelPrinterNote = "TSPL. Use thermal transfer with a ribbon — direct thermal tags fade against fabric."
    override val widthMm = "Width mm"
    override val heightMm = "Height mm"
    override val barcodeScanner = "Barcode scanner"
    override val barcodeScannerNote =
        "An HID keyboard — no driver. Characters arriving faster than this gap are a scan, not typing."
    override val maxGapMs = "Max gap ms"
    override val countTheDrawerFirst =
        "Count the drawer first. The expected figure is shown after you close, on purpose — counting towards a number you can already see finds nothing."
    override val changeGiven = "Change given"
    override val refundedInCash = "Refunded in cash"
    override val expectedInDrawer = "Expected in drawer"
    override val salesStillWorkNoZ = "Sales still work, but they will not appear on a Z-report."
    override val lockedTryShortly = "Locked after too many attempts. Try again shortly."
    override val newInstallationNobody = "This is a new installation, so there is nobody to sign in as yet."
    override val recoveryOnlyWayBack =
        "It is the only way back into the shop if the admin password is forgotten. There is no server and no email — nobody can reset it for you."
    override val eachColourOwnSkuLong = "Each colour is its own SKU, with its own barcode and stock."
    override val addAColour = "Add a colour"
    override val scanCodeOnGarment = "Scan the code already on the garment — it will resolve to the same SKU as ours."
    override val youDefineThisTree = "You define this tree — nest it as deep as you like."
    override val adjustNoteLong = "For what a delivery and a count cannot explain: damage, theft, a sample given away."
    override val appendOnlyCorrection = "Append-only: a correction is another line, never an edit of one above it."
    override val pasteSpreadsheetNote =
        "Paste the spreadsheet as comma-separated rows: product, productAr, colour, sku, cost, price, quantity. Quantity is optional, and arrives as a delivery so it carries a cost."
    override val nothingWasImported = "Nothing was imported"
    override val oneBadRowNone =
        "A file with one bad row imports none of it, so nobody has to work out which half went in."
    override val stockArrivedAsDelivery =
        "Stock arrived through an ordinary delivery, so it carries a cost and appears in the ledger exactly as a van-load would."
    override val blindCountNote =
        "You will not see what the system expects until the count is posted. A counter who can see that the system expects twelve will count until they get twelve — and the discrepancy that would have told the owner something disappears."
    override val nothingMovesUntilPosted = "Nothing moves until the delivery is posted, so unpacking can be interrupted."
    override val noSupplier = "No supplier"
    override val weightedAverageNote = "Weighted average, so the new figure sits between the old stock and this delivery."
    override val noCustomersYet = "No customers yet"
    override val cashOnlyShort = "cash only"
    override val cashOnlyNoLimit = "Cash only — nobody has set a limit"
    override val takeAPayment = "Take a payment"
    override val againstAccountNote = "Against the account, not against an invoice — allocation is the report's job."
    override val accountHistory = "Account history"
    override val notYetDue = "Not yet due"
    override val overSixtyDays = "Over 60 days"
    override val allReturned = "all returned"
    override val outsideWindowAdmin = "Outside the window, so an admin has to say yes — and it goes on the record."
    override val adminCanTakeBack = "An admin can take this back outside the window."
    override val generateBarcode = "Gen barcode"
    override val printLabel = "🖨️ Print label"
    override val gapMm = "Gap mm"
    override val each = "each"
    override val soldLabel = "sold"
    override val conditionSellable = "Sellable"
    override val conditionDamaged = "Damaged"
    override val salesCountLabel = "Sales"
    override val voidedSales = "Voided"
    override val grossSales = "Gross"
    override val discountsLabel = "Discounts"
    override val netSales = "Net"
    override val floatLabel = "Float"
    override val cashLabel = "Cash"
    override val cardLabel = "Card"
    override val returnsLabel = "Returns"
    override val countedLabel = "Counted"
    override val balanced = "Balanced"
    override val onAccount = "On account"
    override val oneToThirtyDays = "1–30 days"
    override val thirtyOneToSixtyDays = "31–60 days"
    override val outstanding = "Outstanding"
    override val change = "Change"
    override val draft = "Draft"
    override val approved = "Approved."

    override fun importRows(count: Int) = "Import $count rows"
    override fun takeAmount(amount: String) = "Take $amount"
    override fun refundAmount(amount: String) = "Refund $amount"
    override fun chooseNewSecret(isPin: Boolean) = if (isPin) chooseNewPin else chooseNewPassword
    override fun newSecretAtLeast(minimum: Int) = "New — at least $minimum characters"
    override fun wasPrice(price: String) = "was $price"
    override fun onlyInStock(onHand: Int) = "only $onHand in stock"

    override fun importedProducts(count: Int) = "$count products"
    override fun importedVariants(count: Int) = "$count SKUs"
    override fun importedPieces(count: Int) = "$count pieces received"
    override fun coloursCount(count: Int) = if (count == 1) "1 colour" else "$count colours"
    override fun totalOnHand(count: Int) = "On hand: $count"
    override fun countSummary(lines: Int, discrepancies: Int) = "$lines line(s), $discrepancies did not match"
    override fun countedVsExpected(counted: Int, expected: Int?) = "counted $counted · expected ${expected ?: "—"}"
    override fun alreadyReturned(count: Int) = "$count already returned"
    override fun customerTerms(limit: String, terms: Int) = "limit $limit · net $terms"
    override fun availableCredit(available: String, limit: String) = "$available of $limit still available"
    override fun differenceAmount(amount: String) = "Difference $amount"
    override fun salesOutsideShift(count: Int) = "$count sale(s) were rung up outside any shift"
    override fun ledgerEntryType(type: LedgerEntryType) = when (type) {
        LedgerEntryType.INVOICE -> "Invoice"
        LedgerEntryType.PAYMENT -> "Payment"
        LedgerEntryType.CREDIT_NOTE -> "Credit note"
        LedgerEntryType.ADJUSTMENT -> "Adjustment"
    }
    override fun receiptDaysAgo(receiptNumber: Long, daysSince: Long) = "Receipt #$receiptNumber · $daysSince days ago"
    override fun outsideReturnWindow(days: Int) = "Outside the $days-day window"
    override fun inStock(count: Int) = "$count in stock"
    override fun blockedByStock(onHand: Int) = "$onHand still in stock — sell or adjust them first"
    override fun importLineProblem(lineNumber: Int, message: String) = "Line $lineNumber: $message"
    override fun importPreviewSummary(rows: Int, pieces: Int) = "$rows rows, $pieces pieces"
    override fun importRowDetail(sku: String, cost: String, quantity: Int) = "$sku · cost $cost · $quantity pcs"
    override fun movementReason(reason: MovementReason) = when (reason) {
        MovementReason.SALE -> "Sale"
        MovementReason.RETURN -> "Return"
        MovementReason.RECEIPT -> "Receipt"
        MovementReason.ADJUSTMENT -> "Adjustment"
        MovementReason.TRANSFER_IN -> "Transfer in"
        MovementReason.TRANSFER_OUT -> "Transfer out"
        MovementReason.COUNT -> "Count"
        MovementReason.DAMAGE -> "Damage"
    }
    override fun permissionNeeded(permission: Permission) = when (permission) {
        Permission.DISCOUNT_LINE -> "This needs approval for discounts."
        Permission.OVERRIDE_PRICE -> "This needs approval for price overrides."
        Permission.VOID_SALE -> "This needs approval to void a sale."
        else -> "This needs ${permission.name.lowercase().replace('_', ' ')}."
    }
    override val somethingWentWrong = "Something went wrong"
    override val notAnAmount = "That is not an amount"
    override val notAQuantity = "That is not a quantity"
    override val notANumberOfDays = "That is not a number of days"
    override val incorrectDetails = "Incorrect details"
    override val accountCannotApprove = "That account cannot approve this"
    override val tooManyAttempts = "Too many attempts — locked for a few minutes"
    override val settingsSaved = "Settings saved"
    override val sentToPrinter = "Sent to printer"
    override val enterPrinterAddressFirst = "Enter the printer address first"
    override val couldNotCloseShift = "Could not close the shift"
    override val saleVoided = "Sale voided"
    override val tenderMoreThanZero = "A tender must be more than zero"
    override val nothingToSell = "Nothing to sell"
    override val customerIsCashOnly = "This customer is cash only"
    override val pickCustomerBeforeCredit = "Pick a customer before selling on account"
    override val updatedSignInAgain = "Updated — sign in with your new details"
    override val signInFailed = "Sign-in failed"
    override val colourAlreadyStocked = "That colour is already stocked"
    override val productNotFound = "Product not found"
    override val colourNotFound = "Colour not found"
    override val colourRetired = "Colour retired"
    override val barcodeAttached = "Barcode attached"
    override val barcodeAlreadyInUse = "That barcode is already in use"
    override val notValidEanThirteen = "Not a valid EAN-13"
    override val priceSet = "Price set"
    override val noRetailPriceList = "No retail price list"
    override val categoryAdded = "Category added"
    override val pickACategoryFirst = "Pick a category first"
    override val startACountFirst = "Start a count first"
    override val everythingMatched = "Everything matched"
    override val startADeliveryFirst = "Start a delivery first"
    override val noLabelPrinterConfigured = "No label printer configured"
    override val labelPrinterSilent = "The label printer did not answer"
    override val noSaleWithThatReceipt = "No sale with that receipt"
    override val saleAlreadyVoided = "That sale was voided — already reversed"
    override val everythingCameBack = "Everything on that receipt has come back"
    override val refundSavedPrinterSilent = "Refund saved, but the printer did not answer"
    override val nothingSelectedToReturn = "Nothing selected to return"
    override val couldNotLoadNumbers = "Could not load the numbers"
    override val heldConfirmation = "Held"
    override val shiftOpened = "Shift open"
    override val changeNotAllowed = "That change is not allowed"
    override fun notInCatalogue(term: String) = "Not in the catalogue: $term"
    override fun hasNoPriceYet(sku: String) = "$sku has no price yet"
    override fun skuCreated(sku: String) = "SKU $sku created"
    override fun adjustedBy(quantity: Int) = "Adjusted by $quantity"
    override fun receivedPieces(pieces: Int) = "Received $pieces pieces"
    override fun tagsSent(tags: Int) = "$tags tags sent"
    override fun tagsSentSomeSkipped(tags: Int, skipped: Int) = "$tags tags sent; $skipped have no barcode"
    override fun drawerDiffersBy(amount: String) = "Drawer differs by $amount"
    override fun importedSummary(skus: Int, pieces: Int) = "$skus SKUs, $pieces pieces"
    override fun linesDidNotMatch(lines: Int) = "$lines line(s) did not match"
    override fun refundedReturn(amount: String, number: Long) = "Refunded $amount · return #$number"
    override fun onlyLeftToReturn(description: String, returnable: Int) = "$description: only $returnable left to return"
    override fun shortBy(amount: String) = "Short by $amount"
    override fun overCreditLimitBy(amount: String) = "Over the credit limit by $amount — needs an approval"
    override fun salePrinted(number: Long?) = "Sale #$number"
    override fun saleNoPrinter(number: Long?) = "Sale #$number — no printer configured"
    override fun salePrinterSilent(number: Long?) = "Sale #$number saved, but the printer did not answer"
    override fun linesNoLongerSellable(lines: Int) = "$lines line(s) no longer sellable"
    override fun printerDidNotAnswer(detail: String) = "Printer did not answer — $detail"
    override fun paymentReceived(amount: String) = "Received $amount"
    override fun soldBelowStock(sku: String, sold: Int, onHand: Int) =
        "$sku: sold $sold, stock said $onHand"
}

/**
 * Egyptian retail Arabic, not a dictionary rendering of the English.
 *
 * "الكاشير" rather than "نقطة البيع" because that is what the person standing at it calls it;
 * "العهدة" is the float a shop actually opens a drawer with. Where a word is the same on both
 * sides of the till — SKU, VAT's own abbreviation — it is left alone rather than invented.
 */
object ArabicStrings : Strings {
    override val appName = "كسوة"
    override val till = "الكاشير"
    override val tillHint = "امسح، احسب، اطبع"
    override val returns = "المرتجعات"
    override val returnsHint = "الاسترداد والاستبدال"
    override val stockroom = "المخزن"
    override val stockroomHint = "الاستلام والجرد والتسويات"
    override val catalogue = "الأصناف"
    override val catalogueHint = "المنتجات والألوان والأسعار"
    override val customers = "العملاء"
    override val customersHint = "الحسابات والمديونيات"
    override val shift = "الوردية"
    override val shiftHint = "العهدة والتحصيل والإقفال"
    override val numbers = "الأرقام"
    override val numbersHint = "أداء المحل"
    override val signOut = "تسجيل الخروج"
    override val language = "اللغة"

    override val search = "بحث"
    override val clear = "مسح"
    override val apply = "تطبيق"
    override val cancel = "إلغاء"
    override val back = "رجوع"

    override val scanOrSearch = "امسح أو ابحث — باركود أو كود الصنف أو الاسم"
    override val scanToStart = "امسح للبدء"
    override val scanToStartHint = "أو اكتب كود الصنف أو الاسم واضغط بحث"
    override val takePayment = "تحصيل"
    override val discountOrHold = "خصم أو تعليق"
    override val fewerOptions = "خيارات أقل"
    override val orderDiscount = "خصم على الفاتورة"
    override val holdAs = "تعليق باسم"
    override val hold = "تعليق"
    override val subtotal = "المجموع"
    override val discount = "الخصم"
    override val vat = "ض.ق.م"
    override val total = "الإجمالي"
    override val pieces = "قطعة"
    override val noPrice = "بدون سعر"
    override val reprint = "إعادة طباعة"

    override val returnsSubtitle = "امسح كود الإيصال أو ابحث عن الفاتورة"
    override val findTheReceipt = "ابحث عن الإيصال"
    override val findTheReceiptHint = "امسح الكود الموجود عليه أو اكتب رقم الإيصال"
    override val returnDone = "تم المرتجع"
    override val returnDoneHint = "امسح إيصالًا آخر عند الاستعداد"

    override val stockroomSubtitle = "كل ما هو خارج البيع"
    override val receiving = "الاستلام"
    override val receivingHint = "سجّل الوارد ودع التكلفة تُحتسب منه"
    override val stockCount = "الجرد"
    override val stockCountHint = "جرد أعمى — الكمية المتوقعة تظهر بعد الترحيل"
    override val adjust = "تسوية المخزون"
    override val adjustHint = "تلف أو فقد أو عينة"
    override val importCatalogue = "استيراد الأصناف"
    override val importCatalogueHint = "ملف المورد، يُراجَع بالكامل قبل الاعتماد"
    override val printersAndScanner = "الطابعات والماسح"
    override val startADelivery = "ابدأ استلام وارد"
    override val startADeliveryHint = "اكتب اسم المورد ورقم الإذن ثم امسح ما وصل"
    override val scanFirstCarton = "امسح أول كرتونة"
    override val scanFirstCartonHint = "كل لون يُعدّ على حدة — والتكلفة تتبع ما تُدخله"
    override val nothingCounted = "لم يُجرد شيء بعد"
    override val nothingCountedHint = "امسح قطعة لإضافتها. الكمية المتوقعة تظهر بعد الترحيل."

    override val catalogueSubtitle = "المنتجات والألوان والأسعار"
    override val nothingInCategory = "لا يوجد شيء في هذا القسم"
    override val nothingInCategoryHint = "أضف منتجًا أو اختر قسمًا آخر"
    override val eachColourItsOwnSku = "كل لون له كود صنف مستقل"

    override val customersSubtitle = "الحسابات والشروط والمديونيات"
    override val pickACustomer = "اختر عميلًا"
    override val pickACustomerHint = "الرصيد وأعمار الديون والحركة كلها في لوحة واحدة"

    override val shiftSubtitle = "العهدة والتحصيل والجرد الذي يقفلها"
    override val noShiftOpen = "لا توجد وردية مفتوحة"
    override val noShiftOpenHint = "افتح وردية من الكاشير لبدء التحصيل مقابل عهدة"

    override val settings = "الإعدادات"
    override val settingsSubtitle = "الطابعات والماسح وبيانات المحل"

    override val numbersSubtitle = "أداء المحل"
    override val forTheOwner = "هذه الأرقام لصاحب المحل"
    override val forTheOwnerHint = "اطلب من المدير تسجيل الدخول إذا احتجتها"
    override val today = "اليوم"
    override val sevenDays = "٧ أيام"
    override val thirtyDays = "٣٠ يومًا"
    override val ninetyDays = "٩٠ يومًا"
    override val netOf = "بعد خصم"
    override val sales = "الفواتير"
    override val basket = "متوسط الفاتورة"
    override val units = "القطع"
    override val returnRate = "المرتجعات"
    override val margin = "هامش الربح"
    override val revenue = "الإيرادات"
    override val colourPerformance = "أداء الألوان"
    override val sellThrough = "نسبة التصريف"
    override val busyHours = "ساعات الذروة"
    override val topMovers = "الأكثر مبيعًا"
    override val soldLegend = "المُباع"
    override val onHandLegend = "المتبقي"
    override val colourPerformanceNote =
        "المُباع مقابل ما زال على الرفوف. المال المحتجز في ألوان لا تتحرك هو قرار الشراء للموسم القادم."
    override val sellThroughNote =
        "مقابل هدف ٧٠٪ للموسم. النزول عنه في آخر الموسم يعني الخصم الآن لا في يناير."
    override val soldSuffix = "مُباع"
    override val throughSuffix = "تصريف"
    override val leftSuffix = "متبقي"
    override val marginPrefix = "هامش"
    override val nothingSoldPeriod = "لا مبيعات في هذه الفترة"
    override val noStockToCompare = "لا يوجد مخزون للمقارنة بعد"
    override val noTradingHours = "لا توجد ساعات بيع لعرضها بعد"
    override val nothingReceived = "لم يُستلم شيء بعد"
    override val nothingHasSold = "لم يُبَع شيء بعد"
    override val weekdays = listOf("الأحد", "الاثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت")

    override val scanTheGarment = "امسح القطعة"
    override val changeNegativeToWriteOff = "التغيير — بالسالب للإعدام، مثال ‎-3"
    override val reasonRequired = "السبب — مطلوب"
    override val thisItemsLedger = "حركة هذا الصنف"
    override val categories = "الأقسام"
    override val searchProducts = "ابحث في المنتجات"
    override val newCategory = "قسم جديد"
    override val addTopLevel = "أضف قسمًا رئيسيًا"
    override val addUnderSelected = "أضف داخل المحدد"
    override val newProduct = "منتج جديد"
    override val create = "إنشاء"
    override val blindByDesign = "جرد أعمى بقصد"
    override val startACount = "ابدأ جردًا"
    override val scanThenTypeShelf = "امسح قطعة ثم اكتب ما هو موجود على الرف"
    override val noteOptional = "ملاحظة (اختياري)"
    override val postCount = "ترحيل الجرد"
    override val discard = "تجاهل"
    override val counted = "المجرود"
    override val howManyOnShelf = "كم القطع الموجودة على الرف؟"
    override val record = "تسجيل"
    override val nameOrPhone = "الاسم أو الهاتف"
    override val newShort = "جديد"
    override val amount = "المبلغ"
    override val receivePayment = "تسجيل دفعة"
    override val howOverdue = "أعمار الديون"
    override val newCustomer = "عميل جديد"
    override val name = "الاسم"
    override val phone = "الهاتف"
    override val creditLimitBlankForCash = "حد الائتمان — اتركه فارغًا للنقدي فقط"
    override val paymentTermsDays = "مدة السداد بالأيام"
    override val rows = "الصفوف"
    override val check = "مراجعة"
    override val imported = "تم الاستيراد"
    override val importAnother = "استيراد ملف آخر"
    override val retire = "إيقاف"
    override val price = "السعر"
    override val save = "حفظ"
    override val supplierBarcode = "باركود المورد"
    override val eanThirteen = "باركود EAN-13"
    override val attach = "إرفاق"
    override val referenceInvoiceOrNote = "المرجع — فاتورة أو إذن استلام"
    override val supplier = "المورد"
    override val startDelivery = "ابدأ الاستلام"
    override val scanToAddColourByColour = "امسح قطعة لإضافتها — لونًا بلون"
    override val piecesLabel = "القطع"
    override val cost = "التكلفة"
    override val postDelivery = "ترحيل الاستلام"
    override val printHangTags = "طباعة التيكيت"
    override val costMoved = "تغيّرت التكلفة"
    override val recentDeliveries = "آخر عمليات الاستلام"
    override val quantity = "الكمية"
    override val unitCost = "تكلفة الوحدة"
    override val add = "إضافة"
    override val scanReceiptQrOrNumber = "امسح كود الإيصال أو اكتب رقمه"
    override val refund = "المبلغ المسترد"
    override val needsAnApproval = "يحتاج موافقة"
    override val getApproval = "اطلب الموافقة"
    override val startOver = "ابدأ من جديد"
    override val approvalNeeded = "مطلوب موافقة"
    override val username = "اسم المستخدم"
    override val password = "كلمة المرور"
    override val approve = "موافقة"
    override val testPrint = "طباعة تجريبية"
    override val testLabel = "تيكيت تجريبي"
    override val host = "العنوان"
    override val port = "المنفذ"
    override val backToTill = "العودة للكاشير"
    override val cashCounted = "النقدية المعدودة"
    override val closeShift = "إقفال الوردية"
    override val zReport = "تقرير الإقفال"
    override val seller = "بائع"
    override val admin = "مدير"
    override val whoIsOnTheTill = "مَن على الكاشير"
    override val signIn = "تسجيل الدخول"
    override val createOwnerAccount = "أنشئ حساب المالك"
    override val yourName = "اسمك"
    override val passwordAtLeastEight = "كلمة المرور — 8 أحرف على الأقل"
    override val createAccount = "إنشاء الحساب"
    override val writeThisDown = "اكتب هذا واحتفظ به"
    override val iHaveWrittenItDown = "كتبته واحتفظت به"
    override val chooseNewPassword = "اختر كلمة مرور جديدة"
    override val chooseNewPin = "اختر رقمًا سريًا جديدًا"
    override val saveAndContinue = "حفظ ومتابعة"
    override val openingFloat = "العهدة الافتتاحية"
    override val openShift = "افتح وردية"
    override val shiftOpen = "وردية مفتوحة"
    override val heldSales = "المعلقة"
    override val resume = "استئناف"
    override val cashHandedOver = "النقدية المستلمة"
    override val addCash = "أضف نقدًا"
    override val card = "بطاقة"
    override val addCard = "أضف بطاقة"
    override val complete = "إتمام"
    override val detailsResetByAdmin = "أعاد المدير ضبط بياناتك، وهو يعرف المؤقتة."
    override val receiptPrinter = "طابعة الإيصالات"
    override val receiptPrinterNote = "ESC/POS عبر المنفذ 9100. العربية تُطبع كصورة، فلا يراها برنامج الطابعة أصلًا."
    override val labelPrinter = "طابعة التيكيت"
    override val labelPrinterNote = "بروتوكول TSPL. استخدم النقل الحراري بشريط — التيكيت الحراري المباشر يبهت على القماش."
    override val widthMm = "العرض مم"
    override val heightMm = "الارتفاع مم"
    override val barcodeScanner = "قارئ الباركود"
    override val barcodeScannerNote = "لوحة مفاتيح HID بلا تعريف. الحروف الأسرع من هذه الفترة تعني مسحًا لا كتابة."
    override val maxGapMs = "أقصى فارق بالمللي ثانية"
    override val countTheDrawerFirst =
        "عُدّ الدرج أولًا. الرقم المتوقع يظهر بعد الإقفال عن قصد — العدّ نحو رقم تراه أمامك لا يكشف شيئًا."
    override val changeGiven = "الباقي المصروف"
    override val refundedInCash = "المسترد نقدًا"
    override val expectedInDrawer = "المتوقع في الدرج"
    override val salesStillWorkNoZ = "البيع يعمل، لكن الفواتير لن تظهر في تقرير الإقفال."
    override val lockedTryShortly = "تم القفل بعد محاولات كثيرة. حاول بعد قليل."
    override val newInstallationNobody = "هذا تركيب جديد، فلا يوجد بعد حساب لتسجيل الدخول به."
    override val recoveryOnlyWayBack =
        "هو الطريق الوحيد للعودة إذا نُسيت كلمة مرور المدير. لا يوجد خادم ولا بريد — لا أحد يستطيع إعادة الضبط نيابة عنك."
    override val eachColourOwnSkuLong = "كل لون له كود صنف مستقل، بباركود ورصيد خاصين به."
    override val addAColour = "أضف لونًا"
    override val scanCodeOnGarment = "امسح الكود الموجود على القطعة — سيشير إلى نفس كود الصنف عندنا."
    override val youDefineThisTree = "أنت من يحدد هذه الشجرة — تفرّعها كما تشاء."
    override val adjustNoteLong = "لما لا يفسره استلام ولا جرد: تلف أو سرقة أو عينة أُعطيت."
    override val appendOnlyCorrection = "الإضافة فقط: التصحيح سطر جديد، لا تعديل لسطر فوقه."
    override val pasteSpreadsheetNote =
        "الصق الملف كصفوف مفصولة بفواصل: المنتج، الاسم بالعربية، اللون، كود الصنف، التكلفة، السعر، الكمية. الكمية اختيارية وتدخل كاستلام فتحمل تكلفة."
    override val nothingWasImported = "لم يتم استيراد شيء"
    override val oneBadRowNone = "ملف فيه صف واحد خاطئ لا يُستورد منه شيء، فلا يضطر أحد لمعرفة أي نصف دخل."
    override val stockArrivedAsDelivery = "دخل المخزون عبر استلام عادي، فيحمل تكلفة ويظهر في الدفتر تمامًا كأي شحنة."
    override val blindCountNote =
        "لن ترى ما يتوقعه النظام إلا بعد ترحيل الجرد. من يرى أن النظام يتوقع اثني عشر سيعدّ حتى يصل إليها — ويختفي الفرق الذي كان سيخبر صاحب المحل بشيء."
    override val nothingMovesUntilPosted = "لا يتحرك شيء قبل ترحيل الاستلام، فيمكن مقاطعة التفريغ والعودة إليه."
    override val noSupplier = "بدون مورد"
    override val weightedAverageNote = "متوسط مرجّح، فالرقم الجديد يقع بين المخزون القديم وهذا الاستلام."
    override val noCustomersYet = "لا يوجد عملاء بعد"
    override val cashOnlyShort = "نقدي فقط"
    override val cashOnlyNoLimit = "نقدي فقط — لم يضع أحد حدًا للائتمان"
    override val takeAPayment = "تسجيل دفعة"
    override val againstAccountNote = "على الحساب لا على فاتورة بعينها — التوزيع مهمة التقرير."
    override val accountHistory = "حركة الحساب"
    override val notYetDue = "لم يستحق بعد"
    override val overSixtyDays = "أكثر من 60 يومًا"
    override val allReturned = "رُدّ بالكامل"
    override val outsideWindowAdmin = "خارج المدة المسموحة، فيلزم موافقة مدير — وتُسجَّل الموافقة."
    override val adminCanTakeBack = "يستطيع المدير قبول هذا خارج المدة المسموحة."
    override val generateBarcode = "توليد باركود"
    override val printLabel = "🖨️ طباعة ملصق"
    override val gapMm = "المسافة مم"
    override val each = "للقطعة"
    override val soldLabel = "تم بيع"
    override val conditionSellable = "صالح للبيع"
    override val conditionDamaged = "تالف"
    override val salesCountLabel = "المبيعات"
    override val voidedSales = "الملغاة"
    override val grossSales = "الإجمالي"
    override val discountsLabel = "الخصومات"
    override val netSales = "الصافي"
    override val floatLabel = "العهدة"
    override val cashLabel = "نقدًا"
    override val cardLabel = "بطاقة"
    override val returnsLabel = "المرتجعات"
    override val countedLabel = "المعدود"
    override val balanced = "متطابقة"
    override val onAccount = "على الحساب"
    override val oneToThirtyDays = "1–30 يومًا"
    override val thirtyOneToSixtyDays = "31–60 يومًا"
    override val outstanding = "المتبقي"
    override val change = "الباقي"
    override val draft = "مسودة"
    override val approved = "تمت الموافقة."

    override fun importRows(count: Int) = "استيراد $count صفًا"
    override fun takeAmount(amount: String) = "تحصيل $amount"
    override fun refundAmount(amount: String) = "استرداد $amount"
    override fun chooseNewSecret(isPin: Boolean) = if (isPin) chooseNewPin else chooseNewPassword
    override fun newSecretAtLeast(minimum: Int) = "الجديد — $minimum أحرف على الأقل"
    override fun wasPrice(price: String) = "كان $price"
    override fun onlyInStock(onHand: Int) = "المتاح $onHand فقط"

    override fun importedProducts(count: Int) = "$count منتج"
    override fun importedVariants(count: Int) = "$count صنف"
    override fun importedPieces(count: Int) = "$count قطعة مستلمة"
    override fun coloursCount(count: Int) = when (count) {
        1 -> "لون واحد"
        2 -> "لونان"
        in 3..10 -> "$count ألوان"
        else -> "$count لونًا"
    }
    override fun totalOnHand(count: Int) = "المتبقي: $count"
    override fun countSummary(lines: Int, discrepancies: Int) = "$lines سطر، لم يتطابق منها $discrepancies"
    override fun countedVsExpected(counted: Int, expected: Int?) = "المعدود $counted · المتوقع ${expected ?: "—"}"
    override fun alreadyReturned(count: Int) = "$count تم إرجاعها بالفعل"
    override fun customerTerms(limit: String, terms: Int) = "الحد $limit · الأجل $terms يومًا"
    override fun availableCredit(available: String, limit: String) = "$available من $limit متاح"
    override fun differenceAmount(amount: String) = "فارق $amount"
    override fun salesOutsideShift(count: Int) = "$count عملية بيع تم تسجيلها خارج أي وردية"
    override fun ledgerEntryType(type: LedgerEntryType) = when (type) {
        LedgerEntryType.INVOICE -> "فاتورة"
        LedgerEntryType.PAYMENT -> "دفعة"
        LedgerEntryType.CREDIT_NOTE -> "إشعار دائن"
        LedgerEntryType.ADJUSTMENT -> "تسوية"
    }
    override fun receiptDaysAgo(receiptNumber: Long, daysSince: Long) = "إيصال رقم #$receiptNumber · منذ $daysSince يوم"
    override fun outsideReturnWindow(days: Int) = "خارج نافذة الإرجاع ($days يوم)"
    override fun inStock(count: Int) = "$count في المخزن"
    override fun blockedByStock(onHand: Int) = "$onHand ما زالت في المخزن — يجب بيعها أو تسويتها أولًا"
    override fun importLineProblem(lineNumber: Int, message: String) = "السطر $lineNumber: $message"
    override fun importPreviewSummary(rows: Int, pieces: Int) = "$rows صفوف، $pieces قطعة"
    override fun importRowDetail(sku: String, cost: String, quantity: Int) = "$sku · التكلفة $cost · $quantity قطعة"
    override fun movementReason(reason: MovementReason) = when (reason) {
        MovementReason.SALE -> "بيع"
        MovementReason.RETURN -> "مرتجع"
        MovementReason.RECEIPT -> "استلام"
        MovementReason.ADJUSTMENT -> "تسوية"
        MovementReason.TRANSFER_IN -> "تحويل وارد"
        MovementReason.TRANSFER_OUT -> "تحويل صادر"
        MovementReason.COUNT -> "جرد"
        MovementReason.DAMAGE -> "تالف"
    }
    override fun permissionNeeded(permission: Permission) = when (permission) {
        Permission.DISCOUNT_LINE -> "هذا الإجراء يتطلب موافقة على تطبيق الخصم."
        Permission.OVERRIDE_PRICE -> "هذا الإجراء يتطلب موافقة على تعديل السعر."
        Permission.VOID_SALE -> "هذا الإجراء يتطلب موافقة على إلغاء البيع."
        else -> "هذا الإجراء يتطلب إذن صلاحية."
    }
    override val somethingWentWrong = "حدث خطأ ما"
    override val notAnAmount = "هذا ليس مبلغًا"
    override val notAQuantity = "هذه ليست كمية"
    override val notANumberOfDays = "هذا ليس عدد أيام"
    override val incorrectDetails = "بيانات غير صحيحة"
    override val accountCannotApprove = "هذا الحساب لا يملك صلاحية الموافقة"
    override val tooManyAttempts = "محاولات كثيرة — مقفل لبضع دقائق"
    override val settingsSaved = "تم حفظ الإعدادات"
    override val sentToPrinter = "أُرسل إلى الطابعة"
    override val enterPrinterAddressFirst = "أدخل عنوان الطابعة أولًا"
    override val couldNotCloseShift = "تعذّر إقفال الوردية"
    override val saleVoided = "تم إلغاء الفاتورة"
    override val tenderMoreThanZero = "المبلغ المدفوع يجب أن يزيد عن صفر"
    override val nothingToSell = "لا يوجد ما يُباع"
    override val customerIsCashOnly = "هذا العميل نقدي فقط"
    override val pickCustomerBeforeCredit = "اختر عميلًا قبل البيع على الحساب"
    override val updatedSignInAgain = "تم التحديث — سجّل الدخول ببياناتك الجديدة"
    override val signInFailed = "فشل تسجيل الدخول"
    override val colourAlreadyStocked = "هذا اللون موجود بالفعل"
    override val productNotFound = "المنتج غير موجود"
    override val colourNotFound = "اللون غير موجود"
    override val colourRetired = "تم إيقاف اللون"
    override val barcodeAttached = "تم إرفاق الباركود"
    override val barcodeAlreadyInUse = "هذا الباركود مستخدم بالفعل"
    override val notValidEanThirteen = "باركود EAN-13 غير صالح"
    override val priceSet = "تم ضبط السعر"
    override val noRetailPriceList = "لا توجد قائمة أسعار تجزئة"
    override val categoryAdded = "تمت إضافة القسم"
    override val pickACategoryFirst = "اختر قسمًا أولًا"
    override val startACountFirst = "ابدأ جردًا أولًا"
    override val everythingMatched = "كل شيء مطابق"
    override val startADeliveryFirst = "ابدأ استلامًا أولًا"
    override val noLabelPrinterConfigured = "لم تُضبط طابعة تيكيت"
    override val labelPrinterSilent = "طابعة التيكيت لم تستجب"
    override val noSaleWithThatReceipt = "لا توجد فاتورة بهذا الإيصال"
    override val saleAlreadyVoided = "هذه الفاتورة ملغاة — وعُكست بالفعل"
    override val everythingCameBack = "كل ما في هذا الإيصال قد رُدّ"
    override val refundSavedPrinterSilent = "حُفظ الاسترداد، لكن الطابعة لم تستجب"
    override val nothingSelectedToReturn = "لم يُحدد شيء للإرجاع"
    override val couldNotLoadNumbers = "تعذّر تحميل الأرقام"
    override val heldConfirmation = "تم التعليق"
    override val shiftOpened = "تم فتح الوردية"
    override val changeNotAllowed = "هذا التغيير غير مسموح"
    override fun notInCatalogue(term: String) = "غير موجود في الأصناف: $term"
    override fun hasNoPriceYet(sku: String) = "$sku بدون سعر بعد"
    override fun skuCreated(sku: String) = "تم إنشاء كود الصنف $sku"
    override fun adjustedBy(quantity: Int) = "تمت التسوية بمقدار $quantity"
    override fun receivedPieces(pieces: Int) = "تم استلام $pieces قطعة"
    override fun tagsSent(tags: Int) = "أُرسل $tags تيكيت"
    override fun tagsSentSomeSkipped(tags: Int, skipped: Int) = "أُرسل $tags تيكيت؛ $skipped بدون باركود"
    override fun drawerDiffersBy(amount: String) = "الدرج مختلف بمقدار $amount"
    override fun importedSummary(skus: Int, pieces: Int) = "$skus كود صنف، $pieces قطعة"
    override fun linesDidNotMatch(lines: Int) = "$lines سطر غير مطابق"
    override fun refundedReturn(amount: String, number: Long) = "تم رد $amount · مرتجع رقم $number"
    override fun onlyLeftToReturn(description: String, returnable: Int) = "$description: المتبقي للإرجاع $returnable فقط"
    override fun shortBy(amount: String) = "ناقص $amount"
    override fun overCreditLimitBy(amount: String) = "تجاوز حد الائتمان بمقدار $amount — يلزم موافقة"
    override fun salePrinted(number: Long?) = "فاتورة رقم $number"
    override fun saleNoPrinter(number: Long?) = "فاتورة رقم $number — لم تُضبط طابعة"
    override fun salePrinterSilent(number: Long?) = "حُفظت الفاتورة رقم $number، لكن الطابعة لم تستجب"
    override fun linesNoLongerSellable(lines: Int) = "$lines سطر لم يعد قابلًا للبيع"
    override fun printerDidNotAnswer(detail: String) = "الطابعة لم تستجب — $detail"
    override fun paymentReceived(amount: String) = "تم تحصيل $amount"
    override fun soldBelowStock(sku: String, sold: Int, onHand: Int) =
        "$sku: بيع $sold، والرصيد المسجل $onHand"
}

fun stringsFor(language: KeswaLanguage): Strings =
    if (language == KeswaLanguage.ARABIC) ArabicStrings else EnglishStrings

/**
 * The shop's own name for a thing, in the language the shop is reading.
 *
 * Every catalogue row has carried `nameAr` beside `name` since Phase 1, and until now nothing ever
 * showed it — the Arabic was entered, stored, synced, and never read. This is the one line that
 * makes those columns worth their width.
 *
 * Falls back rather than showing an empty label: a product somebody has not got round to naming in
 * Arabic should read in English, not disappear.
 */
@androidx.compose.runtime.Composable
fun localisedName(name: String, nameAr: String?): String =
    if (KeswaTheme.language == KeswaLanguage.ARABIC && !nameAr.isNullOrBlank()) nameAr else name
