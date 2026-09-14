package com.alsoug.keswa.core.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * The shared, admin-managed colour list every product draws from, so "Navy" means the same thing
 * shop-wide and analytics can group by it.
 *
 * [hex] drives the swatch only. It is never the identifier — two shades can share a hex.
 */
@Entity(tableName = "colour")
data class ColourEntity(
    @PrimaryKey val id: String,
    val name: String,
    val nameAr: String,
    val hex: String,
    val sortOrder: Int,
    val isActive: Boolean,
)
