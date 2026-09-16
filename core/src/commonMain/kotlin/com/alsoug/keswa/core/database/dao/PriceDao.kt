package com.alsoug.keswa.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.alsoug.keswa.core.database.entities.PriceEntity
import com.alsoug.keswa.core.database.entities.PriceListEntity

@Dao
interface PriceDao {

    @Upsert
    suspend fun upsertList(priceList: PriceListEntity)

    @Upsert
    suspend fun upsertPrice(price: PriceEntity)

    @Query("SELECT * FROM price_list WHERE isActive = 1")
    suspend fun getLists(): List<PriceListEntity>

    @Query("SELECT * FROM price_list WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefaultList(): PriceListEntity?

    @Query("SELECT * FROM price_list WHERE id = :id")
    suspend fun getListById(id: String): PriceListEntity?

    @Query(
        """
        SELECT * FROM price
        WHERE variantId = :variantId AND priceListId = :priceListId
          AND validFrom <= :at AND (validTo IS NULL OR validTo > :at)
        ORDER BY validFrom DESC LIMIT 1
        """,
    )
    suspend fun getEffectivePrice(variantId: String, priceListId: String, at: Long): PriceEntity?

    /**
     * Ends whatever price is in force at [at].
     *
     * A price change closes the old row and opens a new one rather than overwriting, so an old
     * receipt stays explicable and "what was this selling for in March" has an answer.
     */
    @Query(
        """
        UPDATE price SET validTo = :at
        WHERE variantId = :variantId AND priceListId = :priceListId
          AND validFrom <= :at AND (validTo IS NULL OR validTo > :at)
        """,
    )
    suspend fun closeCurrent(variantId: String, priceListId: String, at: Long)

    @Query("SELECT * FROM price WHERE variantId = :variantId ORDER BY validFrom DESC")
    suspend fun historyFor(variantId: String): List<PriceEntity>
}
