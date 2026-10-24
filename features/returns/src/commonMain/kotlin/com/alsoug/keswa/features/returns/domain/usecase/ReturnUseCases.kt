package com.alsoug.keswa.features.returns.domain.usecase

import com.alsoug.keswa.core.domain.IdGenerator
import com.alsoug.keswa.core.domain.model.Permission
import com.alsoug.keswa.core.domain.model.ReturnCondition
import com.alsoug.keswa.core.domain.model.ReturnableLine
import com.alsoug.keswa.core.domain.model.Sale
import com.alsoug.keswa.core.domain.model.SaleReturn
import com.alsoug.keswa.core.domain.model.SaleReturnLine
import com.alsoug.keswa.core.domain.model.SaleStatus
import com.alsoug.keswa.core.domain.model.TenderMethod
import com.alsoug.keswa.core.domain.model.User
import com.alsoug.keswa.core.domain.model.permissions
import com.alsoug.keswa.core.domain.money.Money
import com.alsoug.keswa.core.domain.money.taxIncludedAt
import com.alsoug.keswa.core.domain.repository.ILocationRepository
import com.alsoug.keswa.core.domain.repository.ISaleRepository
import com.alsoug.keswa.core.domain.repository.ISaleReturnRepository
import com.alsoug.keswa.core.domain.repository.ISettingsRepository
import com.alsoug.keswa.core.domain.repository.IUserRepository
import com.alsoug.keswa.core.domain.repository.ReturnDraft
import com.alsoug.keswa.core.error.Error
import com.alsoug.keswa.core.session.ISessionManager

/** What a lookup found, or why it did not. */
sealed interface SaleLookup {
    data class Found(
        val sale: Sale,
        val lines: List<ReturnableLine>,
        val daysSince: Int,
        val isInsidePolicy: Boolean,
    ) : SaleLookup

    data object NotFound : SaleLookup
    data class Voided(val sale: Sale) : SaleLookup
    data object FullyReturned : SaleLookup
}

/**
 * Finds the sale a customer is returning against.
 *
 * The receipt's QR carries the sale's **id**, which is why Phase 5 printed it — a return is the
 * thing that QR existed for. A typed receipt number works too, because a QR that will not scan is
 * a Tuesday.
 */
class FindSaleForReturnUseCase(
    private val sales: ISaleRepository,
    private val returns: ISaleReturnRepository,
    private val settings: ISettingsRepository,
    private val now: () -> Long,
) {
    suspend fun byQrCode(saleId: String): Result<SaleLookup> =
        runCatching { classify(sales.getById(saleId.trim()).getOrThrow()) }

    suspend fun byReceiptNumber(receiptNumber: Long): Result<SaleLookup> =
        runCatching { classify(sales.getByReceiptNumber(receiptNumber).getOrThrow()) }

    private suspend fun classify(sale: Sale?): SaleLookup {
        if (sale == null) return SaleLookup.NotFound
        // A voided sale was already reversed; returning against it would refund twice.
        if (sale.status == SaleStatus.VOIDED) return SaleLookup.Voided(sale)

        val lines = returns.returnableLines(sale.id).getOrThrow()
        if (lines.all { it.isFullyReturned }) return SaleLookup.FullyReturned

        val window = settings.get().getOrThrow().returnWindowDays
        val days = ((now() - sale.occurredAt) / MILLIS_PER_DAY).toInt()

        return SaleLookup.Found(
            sale = sale,
            lines = lines,
            daysSince = days,
            isInsidePolicy = days <= window,
        )
    }

    private companion object {
        const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000
    }
}

/** One line the cashier has decided is coming back. */
data class ReturningLine(
    val saleLineId: String?,
    val variantId: String,
    val description: String,
    val quantity: Int,
    val unitRefund: Money,
    val unitCost: Money,
    val condition: ReturnCondition = ReturnCondition.SELLABLE,
) {
    val lineRefund: Money get() = unitRefund * quantity
}

sealed interface ReturnResult {
    data class Completed(val saleReturn: SaleReturn) : ReturnResult
    data object NothingToReturn : ReturnResult
    data class ExceedsSold(val description: String, val returnable: Int) : ReturnResult
}

/**
 * Takes the goods back and gives the money out, in one transaction.
 *
 * Three rules live here rather than in the screen, because each of them costs real money when the
 * screen is the only thing enforcing it:
 *
 * - **You cannot return more than was sold.** Otherwise the same jacket comes back three times
 *   against one receipt.
 * - **Outside the window, or with no receipt, needs `REFUND_ANY`** — an admin, approving in place.
 *   The permission is the policy.
 * - **The approving user is looked up, not trusted.** An id in a field is a claim the UI makes.
 */
class CompleteReturnUseCase(
    private val returns: ISaleReturnRepository,
    private val sessions: ISessionManager,
    private val users: IUserRepository,
    private val locations: ILocationRepository,
    private val settings: ISettingsRepository,
    private val ids: IdGenerator,
    private val now: () -> Long,
) {

    suspend operator fun invoke(
        originalSaleId: String?,
        lines: List<ReturningLine>,
        reason: String,
        refundMethod: TenderMethod,
        shiftId: String?,
        authorisedByUserId: String?,
        needsAuthority: Boolean,
    ): Result<ReturnResult> = runCatching {
        if (lines.isEmpty() || lines.all { it.quantity <= 0 }) {
            return@runCatching ReturnResult.NothingToReturn
        }

        // A plain in-policy return with a receipt is a seller's job. Anything else is an admin's,
        // and the approval is looked up before a single row is written.
        if (needsAuthority) {
            requireApproval(authorisedByUserId)
        } else {
            requirePermission(sessions.current.value?.user, Permission.REFUND_WITHIN_POLICY)
        }

        val user = requireNotNull(sessions.current.value) { "no session" }.user

        originalSaleId?.let { saleId ->
            val returnable = returns.returnableLines(saleId).getOrThrow().associateBy { it.saleLineId }
            lines.forEach { line ->
                val against = line.saleLineId?.let { returnable[it] }
                if (against != null && line.quantity > against.returnable) {
                    return@runCatching ReturnResult.ExceedsSold(
                        description = against.description,
                        returnable = against.returnable,
                    )
                }
            }
        }

        val locationId = requireNotNull(locations.default().getOrThrow()) {
            "no default location"
        }.id
        val vat = settings.get().getOrThrow().vatBasisPoints

        val returnId = ids.newId()
        val timestamp = now()
        val subtotal = lines.fold(Money.ZERO) { sum, line -> sum + line.lineRefund }
        // Extracted from the refund, exactly as it was extracted from the sale — the customer
        // gets back the tax they paid, not the tax recomputed at today's rate on today's price.
        val tax = if (vat > 0) subtotal.taxIncludedAt(vat) else Money.ZERO

        val draft = ReturnDraft(
            id = returnId,
            originalSaleId = originalSaleId,
            locationId = locationId,
            userId = user.id,
            shiftId = shiftId,
            reason = reason.trim(),
            refundMethod = refundMethod,
            refundAmount = subtotal,
            subtotal = subtotal,
            tax = tax,
            occurredAt = timestamp,
            authorisedByUserId = authorisedByUserId,
            lines = lines.filter { it.quantity > 0 }.mapIndexed { index, line ->
                SaleReturnLine(
                    id = ids.newId(),
                    returnId = returnId,
                    lineNumber = index + 1,
                    saleLineId = line.saleLineId,
                    variantId = line.variantId,
                    description = line.description,
                    quantity = line.quantity,
                    unitRefund = line.unitRefund,
                    lineRefund = line.lineRefund,
                    condition = line.condition,
                    unitCost = line.unitCost,
                )
            },
        )

        ReturnResult.Completed(returns.record(draft).getOrThrow())
    }

    private suspend fun requireApproval(authorisedByUserId: String?) {
        val approver = authorisedByUserId?.let { users.findById(it).getOrThrow()?.user }
            ?: throw Error.ForbiddenAccess("this return needs an approval")
        requirePermission(approver, Permission.REFUND_ANY)
    }

    private fun requirePermission(user: User?, permission: Permission) {
        if (user == null || permission !in user.role.permissions) {
            throw Error.ForbiddenAccess("not permitted: ${permission.name}")
        }
    }
}

/**
 * What a no-receipt return is worth.
 *
 * The lowest price the variant has ever actually sold for. Buying at a markdown and returning at
 * full price is the most common refund fraud in retail, and it costs exactly the markdown each
 * time — so the shop refunds the least it was ever paid, not the most.
 */
class LowestSoldPriceUseCase(private val returns: ISaleReturnRepository) {
    suspend operator fun invoke(variantId: String): Result<Money?> =
        returns.lowestSoldPrice(variantId)
}

/**
 * Reverses a return.
 *
 * Needs the approving user rather than the session, on the same reasoning as voiding a sale: it
 * moves money, so it costs somebody a deliberate act.
 */
class VoidReturnUseCase(
    private val returns: ISaleReturnRepository,
    private val now: () -> Long,
) {
    suspend operator fun invoke(
        returnId: String,
        reason: String,
        authorisedBy: User,
    ): Result<SaleReturn> = runCatching {
        if (Permission.REFUND_ANY !in authorisedBy.role.permissions) {
            throw Error.ForbiddenAccess("not permitted: ${Permission.REFUND_ANY.name}")
        }
        if (reason.isBlank()) throw Error.InvalidData("a void needs a reason")

        returns.void(returnId, authorisedBy.id, reason.trim(), now()).getOrThrow()
    }
}

/** Ties a return to the replacement purchase, which is all an exchange is. */
class LinkExchangeUseCase(private val returns: ISaleReturnRepository) {
    suspend operator fun invoke(returnId: String, saleId: String): Result<Unit> =
        returns.linkExchange(returnId, saleId)
}

class GetReturnUseCase(private val returns: ISaleReturnRepository) {
    suspend operator fun invoke(returnId: String): Result<SaleReturn?> = returns.getById(returnId)
}
