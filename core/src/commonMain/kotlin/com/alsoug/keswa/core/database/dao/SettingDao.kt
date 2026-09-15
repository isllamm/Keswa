package com.alsoug.keswa.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.alsoug.keswa.core.database.entities.AppSettingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SettingDao {

    @Upsert
    suspend fun put(setting: AppSettingEntity)

    @Upsert
    suspend fun putAll(settings: List<AppSettingEntity>)

    @Query("SELECT value FROM app_setting WHERE key = :key")
    suspend fun get(key: String): String?

    @Query("SELECT * FROM app_setting")
    suspend fun getAll(): List<AppSettingEntity>

    @Query("SELECT * FROM app_setting")
    fun observeAll(): Flow<List<AppSettingEntity>>
}
