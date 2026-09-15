package com.alsoug.keswa.core.domain.repository

import com.alsoug.keswa.core.domain.model.ShopSettings

interface ISettingsRepository {
    suspend fun get(): Result<ShopSettings>
    suspend fun save(settings: ShopSettings): Result<Unit>
}
