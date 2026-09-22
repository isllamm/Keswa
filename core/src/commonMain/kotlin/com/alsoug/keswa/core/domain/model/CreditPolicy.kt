package com.alsoug.keswa.core.domain.model

import com.alsoug.keswa.core.domain.money.Money

/**
 * Whether a customer may take goods on account — one definition, used by both the till and the
 * customer screen.
 *
 * Pure, so the rule can be reasoned about on its own and cannot drift between the place that
 * *warns* and the place that *stops*.
 *
 * **It is a stop, not a warning.** A credit limit that warns is a credit limit that gets clicked
 * through on a busy morning, and the busy morning is the entire reason it exists.
 */
object CreditPolicy {

    /** True when the limit is zero: cash only, the right default for unearned trust. */
    fun isCashOnly(limit: Money): Boolean = limit.isZero

    fun wouldExceed(balance: Money, limit: Money, amount: Money): Boolean =
        balance + amount > limit

    fun excess(balance: Money, limit: Money, amount: Money): Money =
        (balance + amount - limit).let { if (it.isNegative) Money.ZERO else it }
}
