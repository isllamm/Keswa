package com.alsoug.keswa.core.domain.repository

import com.alsoug.keswa.core.domain.model.Location

interface ILocationRepository {

    /** The place this install sells from. Seeded at first run, so null means a broken database. */
    suspend fun default(): Result<Location?>

    suspend fun ensureDefault(id: String, name: String, nameAr: String): Result<Location>

    suspend fun getAll(): Result<List<Location>>
}
