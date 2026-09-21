package com.alsoug.keswa.core.data.mapper

import com.alsoug.keswa.core.database.dao.SellableRow
import com.alsoug.keswa.core.database.entities.HeldSaleEntity
import com.alsoug.keswa.core.database.entities.HeldSaleLineEntity
import com.alsoug.keswa.core.database.entities.PaymentEntity
import com.alsoug.keswa.core.database.entities.PriceListEntity
import com.alsoug.keswa.core.database.entities.SaleEntity
import com.alsoug.keswa.core.database.entities.SaleLineEntity
import com.alsoug.keswa.core.database.entities.ShiftEntity
import com.alsoug.keswa.core.database.entities.StockCountEntity
import com.alsoug.keswa.core.database.entities.StockCountLineEntity
import com.alsoug.keswa.core.database.entities.StockReceiptEntity
import com.alsoug.keswa.core.database.entities.StockReceiptLineEntity
import com.alsoug.keswa.core.domain.model.HeldSale
import com.alsoug.keswa.core.domain.model.HeldSaleLine
import com.alsoug.keswa.core.domain.model.Payment
import com.alsoug.keswa.core.domain.model.PriceList
import com.alsoug.keswa.core.domain.model.Sale
import com.alsoug.keswa.core.domain.model.SaleLine
import com.alsoug.keswa.core.domain.model.SellableItem
import com.alsoug.keswa.core.domain.model.Shift
import com.alsoug.keswa.core.domain.model.StockCount
import com.alsoug.keswa.core.domain.model.StockCountLine
import com.alsoug.keswa.core.domain.model.StockReceipt
import com.alsoug.keswa.core.domain.model.StockReceiptLine
import com.alsoug.keswa.core.domain.money.Money

fun SaleEntity.toDomain(
    lines: List<SaleLine> = emptyList(),
    payments: List<Payment> = emptyList(),
): Sale = Sale(
    id = id,
    receiptNumber = receiptNumber,
    locationId = locationId,
    priceListId = priceListId,
    userId = userId,
    shiftId = shiftId,
    status = status,
    subtotal = Money.ofPiastres(subtotalPiastres),
    discount = Money.ofPiastres(discountPiastres),
    tax = Money.ofPiastres(taxPiastres),
    total = Money.ofPiastres(totalPiastres),
    tendered = Money.ofPiastres(tenderedPiastres),
    change = Money.ofPiastres(changePiastres),
    occurredAt = occurredAt,
    voidedAt = voidedAt,
    voidedByUserId = voidedByUserId,
    voidReason = voidReason,
    lines = lines,
    payments = payments,
)

fun SaleLineEntity.toDomain(): SaleLine = SaleLine(
    id = id,
    saleId = saleId,
    lineNumber = lineNumber,
    variantId = variantId,
    description = description,
    descriptionAr = descriptionAr,
    quantity = quantity,
    unitPrice = Money.ofPiastres(unitPricePiastres),
    lineDiscount = Money.ofPiastres(lineDiscountPiastres),
    orderDiscount = Money.ofPiastres(orderDiscountPiastres),
    lineTotal = Money.ofPiastres(lineTotalPiastres),
    tax = Money.ofPiastres(taxPiastres),
    unitCost = Money.ofPiastres(unitCostPiastres),
    authorisedByUserId = authorisedByUserId,
)

fun SaleLine.toEntity(): SaleLineEntity = SaleLineEntity(
    id = id,
    saleId = saleId,
    lineNumber = lineNumber,
    variantId = variantId,
    description = description,
    descriptionAr = descriptionAr,
    quantity = quantity,
    unitPricePiastres = unitPrice.piastres,
    lineDiscountPiastres = lineDiscount.piastres,
    orderDiscountPiastres = orderDiscount.piastres,
    lineTotalPiastres = lineTotal.piastres,
    taxPiastres = tax.piastres,
    unitCostPiastres = unitCost.piastres,
    authorisedByUserId = authorisedByUserId,
)

fun PaymentEntity.toDomain(): Payment = Payment(
    id = id,
    saleId = saleId,
    method = method,
    amount = Money.ofPiastres(amountPiastres),
    tendered = Money.ofPiastres(tenderedPiastres),
    reference = reference,
    occurredAt = occurredAt,
)

fun Payment.toEntity(): PaymentEntity = PaymentEntity(
    id = id,
    saleId = saleId,
    method = method,
    amountPiastres = amount.piastres,
    tenderedPiastres = tendered.piastres,
    reference = reference,
    occurredAt = occurredAt,
)

fun ShiftEntity.toDomain(): Shift = Shift(
    id = id,
    locationId = locationId,
    openedByUserId = openedByUserId,
    openedAt = openedAt,
    openingFloat = Money.ofPiastres(openingFloatPiastres),
    closedAt = closedAt,
    closedByUserId = closedByUserId,
    countedCash = countedCashPiastres?.let { Money.ofPiastres(it) },
    expectedCash = expectedCashPiastres?.let { Money.ofPiastres(it) },
    note = note,
)

fun HeldSaleEntity.toDomain(lines: List<HeldSaleLine> = emptyList()): HeldSale = HeldSale(
    id = id,
    label = label,
    locationId = locationId,
    userId = userId,
    heldAt = heldAt,
    lines = lines,
)

fun HeldSaleLineEntity.toDomain(): HeldSaleLine = HeldSaleLine(
    id = id,
    heldSaleId = heldSaleId,
    lineNumber = lineNumber,
    variantId = variantId,
    quantity = quantity,
    unitPrice = Money.ofPiastres(unitPricePiastres),
    lineDiscount = Money.ofPiastres(lineDiscountPiastres),
    authorisedByUserId = authorisedByUserId,
)

fun HeldSaleLine.toEntity(): HeldSaleLineEntity = HeldSaleLineEntity(
    id = id,
    heldSaleId = heldSaleId,
    lineNumber = lineNumber,
    variantId = variantId,
    quantity = quantity,
    unitPricePiastres = unitPrice.piastres,
    lineDiscountPiastres = lineDiscount.piastres,
    authorisedByUserId = authorisedByUserId,
)

fun PriceListEntity.toDomain(): PriceList =
    PriceList(id = id, name = name, nameAr = nameAr, type = type, isDefault = isDefault, isActive = isActive)

fun SellableRow.toDomain(): SellableItem = SellableItem(
    variantId = variantId,
    productId = productId,
    sku = sku,
    name = name,
    nameAr = nameAr,
    colourName = colourName,
    colourNameAr = colourNameAr,
    cost = Money.ofPiastres(costPiastres),
    price = pricePiastres?.let { Money.ofPiastres(it) },
    onHand = onHand,
)

fun StockReceiptEntity.toDomain(lines: List<StockReceiptLine> = emptyList()): StockReceipt =
    StockReceipt(
        id = id,
        reference = reference,
        supplierName = supplierName,
        locationId = locationId,
        status = status,
        note = note,
        createdAt = createdAt,
        createdByUserId = createdByUserId,
        postedAt = postedAt,
        postedByUserId = postedByUserId,
        totalCost = Money.ofPiastres(totalCostPiastres),
        lines = lines,
    )

fun StockReceiptLineEntity.toDomain(): StockReceiptLine = StockReceiptLine(
    id = id,
    receiptId = receiptId,
    lineNumber = lineNumber,
    variantId = variantId,
    quantity = quantity,
    unitCost = Money.ofPiastres(unitCostPiastres),
    lineTotal = Money.ofPiastres(lineTotalPiastres),
)

fun StockCountEntity.toDomain(lines: List<StockCountLine> = emptyList()): StockCount = StockCount(
    id = id,
    locationId = locationId,
    status = status,
    note = note,
    startedAt = startedAt,
    startedByUserId = startedByUserId,
    postedAt = postedAt,
    postedByUserId = postedByUserId,
    lines = lines,
)

fun StockCountLineEntity.toDomain(): StockCountLine = StockCountLine(
    id = id,
    countId = countId,
    lineNumber = lineNumber,
    variantId = variantId,
    counted = countedQuantity,
    expected = expectedQuantity,
    variance = varianceQuantity,
)
