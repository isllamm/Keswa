package com.alsoug.keswa.core.data.repository

import com.alsoug.keswa.core.coroutines.runCatchingCancellable
import com.alsoug.keswa.core.data.mapper.toDomain
import com.alsoug.keswa.core.data.mapper.toEntity
import com.alsoug.keswa.core.database.KeswaDatabase
import com.alsoug.keswa.core.database.dao.HeldSaleDao
import com.alsoug.keswa.core.database.dao.PriceDao
import com.alsoug.keswa.core.database.dao.SellableDao
import com.alsoug.keswa.core.database.entities.HeldSaleEntity
import com.alsoug.keswa.core.database.entities.PriceEntity
import com.alsoug.keswa.core.database.entities.PriceListEntity
import com.alsoug.keswa.core.database.inTransaction
import com.alsoug.keswa.core.domain.model.HeldSale
import com.alsoug.keswa.core.domain.model.HeldSaleLine
import com.alsoug.keswa.core.domain.model.PriceList
import com.alsoug.keswa.core.domain.model.PriceListType
import com.alsoug.keswa.core.domain.model.SellableItem
import com.alsoug.keswa.core.domain.money.Money
import com.alsoug.keswa.core.domain.repository.IHeldSaleRepository
import com.alsoug.keswa.core.domain.repository.IPriceRepository
import com.alsoug.keswa.core.domain.repository.ISellableRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class HeldSaleRepositoryImpl(
    private val database: KeswaDatabase,
    private val dao: HeldSaleDao,
) : IHeldSaleRepository {

    override suspend fun hold(
        id: String,
        label: String,
        locationId: String,
        userId: String,
        atMillis: Long,
        lines: List<HeldSaleLine>,
    ): Result<HeldSale> = runCatchingCancellable {
        require(lines.isNotEmpty()) { "nothing to hold" }

        database.inTransaction {
            val entity = HeldSaleEntity(
                id = id,
                label = label,
                locationId = locationId,
                userId = userId,
                heldAt = atMillis,
            )
            dao.insert(entity)
            dao.insertLines(lines.map { it.toEntity() })
            entity.toDomain(lines)
        }
    }

    override suspend fun getById(id: String): Result<HeldSale?> = runCatchingCancellable {
        dao.getById(id)?.let { held ->
            held.toDomain(dao.getLines(id).map { it.toDomain() })
        }
    }

    /** Headers only — the list shows labels and times; lines are loaded when one is resumed. */
    override suspend fun list(locationId: String): Result<List<HeldSale>> =
        runCatchingCancellable { dao.list(locationId).map { it.toDomain() } }

    override suspend fun discard(id: String): Result<Unit> =
        runCatchingCancellable { dao.delete(id) }

    override fun observe(locationId: String): Flow<List<HeldSale>> =
        dao.observe(locationId).map { held -> held.map { it.toDomain() } }
}

class PriceRepositoryImpl(
    private val dao: PriceDao,
) : IPriceRepository {

    override suspend fun defaultList(): Result<PriceList?> =
        runCatchingCancellable { dao.getDefaultList()?.toDomain() }

    override suspend fun ensureDefaultList(
        id: String,
        name: String,
        nameAr: String,
    ): Result<PriceList> = runCatchingCancellable {
        dao.getDefaultList()?.toDomain() ?: PriceListEntity(
            id = id,
            name = name,
            nameAr = nameAr,
            type = PriceListType.RETAIL,
            isDefault = true,
            isActive = true,
        ).also { dao.upsertList(it) }.toDomain()
    }

    override suspend fun listsOfType(type: PriceListType): Result<List<PriceList>> =
        runCatchingCancellable { dao.getLists().filter { it.type == type }.map { it.toDomain() } }

    override suspend fun createList(
        id: String,
        name: String,
        nameAr: String,
        type: PriceListType,
    ): Result<PriceList> = runCatchingCancellable {
        dao.getListById(id)?.toDomain() ?: PriceListEntity(
            id = id,
            name = name,
            nameAr = nameAr,
            type = type,
            // Never the default: one list is what the till falls back to, and a trade list is not it.
            isDefault = false,
            isActive = true,
        ).also { dao.upsertList(it) }.toDomain()
    }

    override suspend fun effectivePrice(
        variantId: String,
        priceListId: String,
        at: Long,
    ): Result<Money?> = runCatchingCancellable {
        dao.getEffectivePrice(variantId, priceListId, at)?.let { Money.ofPiastres(it.pricePiastres) }
    }

    override suspend fun setPrice(
        id: String,
        variantId: String,
        priceListId: String,
        price: Money,
        from: Long,
    ): Result<Unit> = runCatchingCancellable {
        require(!price.isNegative) { "a price cannot be negative" }

        dao.closeCurrent(variantId, priceListId, from)
        dao.upsertPrice(
            PriceEntity(
                id = id,
                priceListId = priceListId,
                variantId = variantId,
                pricePiastres = price.piastres,
                validFrom = from,
                validTo = null,
            ),
        )
    }
}

class SellableRepositoryImpl(
    private val dao: SellableDao,
) : ISellableRepository {

    override suspend fun byBarcode(
        barcode: String,
        priceListId: String,
        locationId: String,
        at: Long,
    ): Result<SellableItem?> = runCatchingCancellable {
        dao.findByBarcode(barcode.trim(), priceListId, locationId, at)?.toDomain()
    }

    override suspend fun byVariantId(
        variantId: String,
        priceListId: String,
        locationId: String,
        at: Long,
    ): Result<SellableItem?> = runCatchingCancellable {
        dao.byVariantId(variantId, priceListId, locationId, at)?.toDomain()
    }

    override suspend fun search(
        term: String,
        priceListId: String,
        locationId: String,
        at: Long,
        limit: Int,
    ): Result<List<SellableItem>> = runCatchingCancellable {
        val cleaned = term.trim()
        if (cleaned.isEmpty()) emptyList()
        else dao.search(cleaned, priceListId, locationId, at, limit).map { it.toDomain() }
    }
}
