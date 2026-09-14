package com.alsoug.keswa.core.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.alsoug.keswa.core.domain.model.LocationType

/**
 * A place stock can sit. Single-shop installs have exactly one; the column exists from v1 because
 * adding a location dimension to a populated ledger later is far more expensive than carrying it.
 */
@Entity(tableName = "location")
data class LocationEntity(
    @PrimaryKey val id: String,
    val name: String,
    val nameAr: String,
    val type: LocationType,
    val isDefault: Boolean,
    val isActive: Boolean,
)
