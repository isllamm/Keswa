package com.alsoug.keswa.core.data.repository

import com.alsoug.keswa.core.coroutines.runCatchingCancellable
import com.alsoug.keswa.core.data.mapper.toDomain
import com.alsoug.keswa.core.database.dao.LocationDao
import com.alsoug.keswa.core.database.entities.LocationEntity
import com.alsoug.keswa.core.domain.model.Location
import com.alsoug.keswa.core.domain.model.LocationType
import com.alsoug.keswa.core.domain.repository.ILocationRepository

class LocationRepositoryImpl(
    private val dao: LocationDao,
) : ILocationRepository {

    override suspend fun default(): Result<Location?> =
        runCatchingCancellable { dao.getDefault()?.toDomain() }

    /**
     * Idempotent, and called at every startup.
     *
     * Without a location the first sale fails on a foreign key, because `stock_movement.locationId`
     * is `RESTRICT` — a fresh install has to have one before anything can be sold.
     */
    override suspend fun ensureDefault(
        id: String,
        name: String,
        nameAr: String,
    ): Result<Location> = runCatchingCancellable {
        dao.getDefault()?.toDomain() ?: LocationEntity(
            id = id,
            name = name,
            nameAr = nameAr,
            type = LocationType.SHOP,
            isDefault = true,
            isActive = true,
        ).also { dao.upsert(it) }.toDomain()
    }

    override suspend fun getAll(): Result<List<Location>> =
        runCatchingCancellable { dao.getAll().map { it.toDomain() } }
}
