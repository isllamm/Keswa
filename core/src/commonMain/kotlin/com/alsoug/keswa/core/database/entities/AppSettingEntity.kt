package com.alsoug.keswa.core.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Shop-wide settings as key/value pairs — printer host, paper width, scanner thresholds.
 *
 * A generic table rather than typed columns because these are configuration, not domain data: they
 * are read once at startup, written rarely, and every phase adds a few. A typed table would mean a
 * migration per setting.
 */
@Entity(tableName = "app_setting")
data class AppSettingEntity(
    @PrimaryKey val key: String,
    val value: String,
)
