package com.alsoug.keswa.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.alsoug.keswa.core.database.entities.AppUserEntity
import com.alsoug.keswa.core.domain.model.UserRole
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {

    @Upsert
    suspend fun upsert(user: AppUserEntity)

    @Update
    suspend fun update(user: AppUserEntity)

    @Query("SELECT * FROM app_user WHERE username = :username AND isActive = 1")
    suspend fun findByUsername(username: String): AppUserEntity?

    @Query("SELECT * FROM app_user WHERE id = :id")
    suspend fun getById(id: String): AppUserEntity?

    @Query("SELECT * FROM app_user WHERE role = :role AND isActive = 1 ORDER BY displayName")
    suspend fun getByRole(role: UserRole): List<AppUserEntity>

    @Query("SELECT * FROM app_user WHERE isActive = 1 ORDER BY role, displayName")
    fun observeAll(): Flow<List<AppUserEntity>>

    @Query("SELECT COUNT(*) FROM app_user WHERE isActive = 1")
    suspend fun countActive(): Int

    @Query("SELECT COUNT(*) FROM app_user WHERE role = 'ADMIN' AND isActive = 1")
    suspend fun countActiveAdmins(): Int
}
