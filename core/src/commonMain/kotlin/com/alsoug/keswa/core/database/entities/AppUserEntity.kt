package com.alsoug.keswa.core.database.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.alsoug.keswa.core.domain.model.SecretKind
import com.alsoug.keswa.core.domain.model.UserRole

/**
 * Someone who can operate the till.
 *
 * Stores a **derived key**, never a credential: [secretHash] is PBKDF2-HMAC-SHA256 over the PIN or
 * password with [secretSalt], and the iteration count is the whole defence for something as short
 * as a four-digit PIN.
 *
 * [failedAttempts] and [lockedUntil] are persisted rather than held in memory, because a lockout
 * that a restart clears is not a lockout — a 4-digit PIN has ten thousand combinations and a
 * patient person with the machine.
 */
@Entity(
    tableName = "app_user",
    indices = [Index(value = ["username"], unique = true)],
)
data class AppUserEntity(
    @PrimaryKey val id: String,
    val username: String,
    val displayName: String,
    val displayNameAr: String,
    val role: UserRole,
    val secretHash: String,
    val secretSalt: String,
    val secretKind: SecretKind,
    val isActive: Boolean,
    val mustChangeSecret: Boolean,
    val failedAttempts: Int,
    val lockedUntil: Long?,
    val createdAt: Long,
    val updatedAt: Long,
)
