package com.alsoug.keswa.core.data.repository

import com.alsoug.keswa.core.coroutines.runCatchingCancellable
import com.alsoug.keswa.core.data.mapper.toDomain
import com.alsoug.keswa.core.database.dao.SaleDao
import com.alsoug.keswa.core.database.dao.ShiftDao
import com.alsoug.keswa.core.database.entities.ShiftEntity
import com.alsoug.keswa.core.domain.model.Shift
import com.alsoug.keswa.core.domain.model.TenderMethod
import com.alsoug.keswa.core.domain.model.ZReport
import com.alsoug.keswa.core.domain.money.Money
import com.alsoug.keswa.core.domain.repository.IShiftRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ShiftRepositoryImpl(
    private val dao: ShiftDao,
    private val sales: SaleDao,
) : IShiftRepository {

    override suspend fun open(
        id: String,
        locationId: String,
        userId: String,
        openingFloat: Money,
        atMillis: Long,
    ): Result<Shift> = runCatchingCancellable {
        // One open shift per location: two make every cash figure ambiguous, and the ambiguity is
        // only ever discovered at the point where it matters most.
        require(dao.getOpen(locationId) == null) { "a shift is already open at $locationId" }
        require(!openingFloat.isNegative) { "opening float cannot be negative" }

        val entity = ShiftEntity(
            id = id,
            locationId = locationId,
            openedByUserId = userId,
            openedAt = atMillis,
            openingFloatPiastres = openingFloat.piastres,
            closedAt = null,
            closedByUserId = null,
            countedCashPiastres = null,
            expectedCashPiastres = null,
            note = null,
        )
        dao.insert(entity)
        entity.toDomain()
    }

    override suspend fun close(
        shiftId: String,
        userId: String,
        countedCash: Money,
        note: String?,
        atMillis: Long,
    ): Result<ZReport> = runCatchingCancellable {
        val shift = requireNotNull(dao.getById(shiftId)) { "shift not found: $shiftId" }
        require(shift.closedAt == null) { "shift is already closed: $shiftId" }

        val expected = expectedCash(shift)
        val affected = dao.close(
            shiftId = shiftId,
            atMillis = atMillis,
            userId = userId,
            countedCash = countedCash.piastres,
            expectedCash = expected.piastres,
            note = note,
        )
        require(affected == 1) { "shift closed underneath us: $shiftId" }

        buildReport(requireNotNull(dao.getById(shiftId)))
    }

    override suspend fun current(locationId: String): Result<Shift?> =
        runCatchingCancellable { dao.getOpen(locationId)?.toDomain() }

    override suspend fun report(shiftId: String): Result<ZReport?> = runCatchingCancellable {
        dao.getById(shiftId)?.let { buildReport(it) }
    }

    override fun observeCurrent(locationId: String): Flow<Shift?> =
        dao.observeOpen(locationId).map { it?.toDomain() }

    /**
     * What should be in the drawer: the float plus what cash actually settled.
     *
     * `amountPiastres` is the settled portion of each tender, so change is already netted off —
     * subtracting it again is the classic way to make every till look short.
     */
    private suspend fun expectedCash(shift: ShiftEntity): Money =
        Money.ofPiastres(shift.openingFloatPiastres) +
            Money.ofPiastres(sales.sumTakenInShift(shift.id, TenderMethod.CASH))

    private suspend fun buildReport(shift: ShiftEntity): ZReport {
        val closedAt = shift.closedAt ?: Long.MAX_VALUE
        return ZReport(
            shift = shift.toDomain(),
            saleCount = sales.countInShift(shift.id),
            voidedCount = sales.countVoidedInShift(shift.id),
            grossSales = Money.ofPiastres(sales.sumSubtotalInShift(shift.id)),
            discounts = Money.ofPiastres(sales.sumDiscountInShift(shift.id)),
            tax = Money.ofPiastres(sales.sumTaxInShift(shift.id)),
            netSales = Money.ofPiastres(sales.sumTotalInShift(shift.id)),
            cashTaken = Money.ofPiastres(sales.sumTakenInShift(shift.id, TenderMethod.CASH)),
            cardTaken = Money.ofPiastres(sales.sumTakenInShift(shift.id, TenderMethod.CARD)),
            changeGiven = Money.ofPiastres(sales.sumChangeInShift(shift.id)),
            // Frozen at close, so reopening the report next month reads the same as it did tonight.
            expectedCash = shift.expectedCashPiastres
                ?.let { Money.ofPiastres(it) }
                ?: expectedCash(shift),
            countedCash = shift.countedCashPiastres?.let { Money.ofPiastres(it) },
            salesOutsideShift = sales.countOutsideShift(shift.openedAt, closedAt),
        )
    }
}
