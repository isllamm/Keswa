package com.alsoug.keswa.core.designsystem

import androidx.compose.runtime.Immutable

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
fun localisedName(name: String, nameAr: String): String =
    if (KeswaTheme.language == KeswaLanguage.ARABIC && nameAr.isNotBlank()) nameAr else name
