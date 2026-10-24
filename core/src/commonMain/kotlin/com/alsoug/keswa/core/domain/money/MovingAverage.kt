package com.alsoug.keswa.core.domain.money

/**
 * The cost of a variant after a delivery lands — KD-008's whole arithmetic, in one pure function.
 *
 * ```
 * newCost = (onHand × currentCost + received × receiptCost) / (onHand + received)
 * ```
 *
 * Rounded half to even, matching every other lossy money operation in the project.
 *
 * **With nothing on hand there is nothing to average against**, so the delivery's own cost becomes
 * the cost outright. That covers the first receipt of a new SKU and the case where stock has gone
 * negative — averaging against a negative quantity produces a number with no meaning, and a till
 * that prints a negative cost price is worse than one that simply takes the newest figure.
 */
fun movingAverageCost(
    onHand: Int,
    currentCost: Money,
    receivedQuantity: Int,
    receiptCost: Money,
): Money {
    require(receivedQuantity > 0) { "a receipt must add stock" }

    if (onHand <= 0) return receiptCost

    val existingValue = currentCost.piastres * onHand
    val arrivingValue = receiptCost.piastres * receivedQuantity
    val totalQuantity = (onHand + receivedQuantity).toLong()

    return Money.ofPiastres(divideHalfEven(existingValue + arrivingValue, totalQuantity))
}
