package com.alsoug.keswa.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.alsoug.keswa.core.database.entities.CategoryEntity
import com.alsoug.keswa.core.database.entities.ColourEntity
import com.alsoug.keswa.core.database.entities.LocationEntity
import com.alsoug.keswa.core.database.entities.ProductEntity
import com.alsoug.keswa.core.database.entities.VariantBarcodeEntity
import com.alsoug.keswa.core.database.entities.VariantEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {

    @Upsert
    suspend fun upsert(category: CategoryEntity)

    @Upsert
    suspend fun upsertAll(categories: List<CategoryEntity>)

    @Query("SELECT * FROM category WHERE isActive = 1 ORDER BY path")
    fun observeTree(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM category ORDER BY path")
    suspend fun getAll(): List<CategoryEntity>

    @Query("SELECT * FROM category WHERE id = :id")
    suspend fun getById(id: String): CategoryEntity?

    @Query("SELECT * FROM category WHERE parentId IS :parentId ORDER BY sortOrder, name")
    suspend fun getChildren(parentId: String?): List<CategoryEntity>

    /** Every descendant of [pathPrefix], and the node itself, by materialised path. */
    @Query("SELECT * FROM category WHERE path LIKE :pathPrefix || '%' ORDER BY path")
    suspend fun getSubtree(pathPrefix: String): List<CategoryEntity>

    @Query("DELETE FROM category WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface ColourDao {

    @Upsert
    suspend fun upsert(colour: ColourEntity)

    @Query("SELECT * FROM colour WHERE isActive = 1 ORDER BY sortOrder, name")
    fun observeAll(): Flow<List<ColourEntity>>

    @Query("SELECT * FROM colour ORDER BY sortOrder, name")
    suspend fun getAll(): List<ColourEntity>

    @Query("SELECT * FROM colour WHERE id = :id")
    suspend fun getById(id: String): ColourEntity?
}

@Dao
interface LocationDao {

    @Upsert
    suspend fun upsert(location: LocationEntity)

    @Query("SELECT * FROM location WHERE isActive = 1 ORDER BY name")
    suspend fun getAll(): List<LocationEntity>

    @Query("SELECT * FROM location WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefault(): LocationEntity?
}

@Dao
interface ProductDao {

    @Upsert
    suspend fun upsert(product: ProductEntity)

    @Query("SELECT * FROM product WHERE isActive = 1 ORDER BY name")
    fun observeAll(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM product WHERE id = :id")
    suspend fun getById(id: String): ProductEntity?

    /**
     * Products in [pathPrefix] and every category beneath it.
     *
     * The prefix match on the materialised path is why selecting "T-shirts" also returns products
     * filed under "Round neck" — the behaviour the whole tree exists for.
     */
    @Query(
        """
        SELECT p.* FROM product p
        JOIN category c ON c.id = p.categoryId
        WHERE c.path LIKE :pathPrefix || '%' AND p.isActive = 1
        ORDER BY p.name
        """,
    )
    suspend fun getInCategoryTree(pathPrefix: String): List<ProductEntity>

    @Query("SELECT COUNT(*) FROM product WHERE categoryId = :categoryId")
    suspend fun countInCategory(categoryId: String): Int
}

@Dao
interface VariantDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(variant: VariantEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(variants: List<VariantEntity>)

    @Update
    suspend fun update(variant: VariantEntity)

    @Query("SELECT * FROM variant WHERE productId = :productId ORDER BY sku")
    suspend fun getForProduct(productId: String): List<VariantEntity>

    @Query("SELECT * FROM variant WHERE id = :id")
    suspend fun getById(id: String): VariantEntity?

    @Query("SELECT * FROM variant WHERE sku = :sku")
    suspend fun getBySku(sku: String): VariantEntity?
}

@Dao
interface VariantBarcodeDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(barcode: VariantBarcodeEntity)

    @Query("SELECT * FROM variant_barcode WHERE barcode = :barcode")
    suspend fun getByBarcode(barcode: String): VariantBarcodeEntity?

    @Query("SELECT * FROM variant_barcode WHERE variantId = :variantId ORDER BY isPrimary DESC")
    suspend fun getForVariant(variantId: String): List<VariantBarcodeEntity>

    /** The till's hot path: one scan, one indexed lookup, straight to the variant. */
    @Query(
        """
        SELECT v.* FROM variant v
        JOIN variant_barcode b ON b.variantId = v.id
        WHERE b.barcode = :barcode
        """,
    )
    suspend fun findVariantByBarcode(barcode: String): VariantEntity?
}
