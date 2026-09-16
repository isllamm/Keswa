package com.alsoug.keswa.features.sell.domain.usecase

import com.alsoug.keswa.core.domain.IdGenerator
import com.alsoug.keswa.core.domain.model.Permission
import com.alsoug.keswa.core.domain.model.Shift
import com.alsoug.keswa.core.domain.model.ZReport
import com.alsoug.keswa.core.domain.money.Money
import com.alsoug.keswa.core.domain.repository.IShiftRepository
import com.alsoug.keswa.core.session.ISessionManager
import com.alsoug.keswa.core.session.require

/**
 * Starts a stint at the till with a counted float.
 *
 * `SELL` rather than an admin permission: the person opening the till is the person selling, and a
 * shift a cashier cannot open is a shift nobody opens.
 */
class OpenShiftUseCase(
    private val shifts: IShiftRepository,
    private val sessions: ISessionManager,
    private val ids: IdGenerator,
    private val now: () -> Long,
) {
    suspend operator fun invoke(openingFloat: Money, till: TillContext): Result<Shift> =
        runCatching {
            sessions.require(Permission.SELL)
            val user = requireNotNull(sessions.current.value) { "no session" }.user
            shifts.open(ids.newId(), till.locationId, user.id, openingFloat, now()).getOrThrow()
        }
}

/**
 * Closes the shift against a physical count and returns the Z-report.
 *
 * The count is entered before the expected figure is shown — that is the whole point. A cashier who
 * can see what the drawer should hold counts until it agrees, and the discrepancy that would have
 * told the owner something disappears.
 */
class CloseShiftUseCase(
    private val shifts: IShiftRepository,
    private val sessions: ISessionManager,
    private val now: () -> Long,
) {
    suspend operator fun invoke(
        shiftId: String,
        countedCash: Money,
        note: String?,
    ): Result<ZReport> = runCatching {
        sessions.require(Permission.SELL)
        require(!countedCash.isNegative) { "a count cannot be negative" }
        val user = requireNotNull(sessions.current.value) { "no session" }.user
        shifts.close(shiftId, user.id, countedCash, note?.trim()?.ifBlank { null }, now()).getOrThrow()
    }
}

class CurrentShiftUseCase(private val shifts: IShiftRepository) {
    suspend operator fun invoke(till: TillContext): Result<Shift?> = shifts.current(till.locationId)
}

/**
 * Reads a shift's figures without closing it — the mid-shift X-report a manager asks for.
 *
 * Gated on `VIEW_SHOP_ANALYTICS`, unlike closing: what the till has taken so far is a business
 * figure, and a seller needs to close their own shift without being able to browse takings.
 */
class ShiftReportUseCase(
    private val shifts: IShiftRepository,
    private val sessions: ISessionManager,
) {
    suspend operator fun invoke(shiftId: String): Result<ZReport?> = runCatching {
        sessions.require(Permission.VIEW_SHOP_ANALYTICS)
        shifts.report(shiftId).getOrThrow()
    }
}
