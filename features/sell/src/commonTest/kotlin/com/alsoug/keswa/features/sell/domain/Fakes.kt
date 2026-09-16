package com.alsoug.keswa.features.sell.domain

import com.alsoug.keswa.core.domain.IdGenerator
import com.alsoug.keswa.core.domain.model.HeldSale
import com.alsoug.keswa.core.domain.model.HeldSaleLine
import com.alsoug.keswa.core.domain.model.Sale
import com.alsoug.keswa.core.domain.model.SaleStatus
import com.alsoug.keswa.core.domain.model.SecretKind
import com.alsoug.keswa.core.domain.model.SellableItem
import com.alsoug.keswa.core.domain.model.ShopSettings
import com.alsoug.keswa.core.domain.model.User
import com.alsoug.keswa.core.domain.model.UserRole
import com.alsoug.keswa.core.domain.money.Money
import com.alsoug.keswa.core.domain.repository.IHeldSaleRepository
import com.alsoug.keswa.core.domain.repository.ISaleRepository
import com.alsoug.keswa.core.domain.repository.ISettingsRepository
import com.alsoug.keswa.core.domain.repository.ISellableRepository
import com.alsoug.keswa.core.domain.repository.IUserRepository
import com.alsoug.keswa.core.domain.repository.SaleDraft
import com.alsoug.keswa.core.domain.repository.StoredCredential
import com.alsoug.keswa.core.platform.IPasswordHasher
import com.alsoug.keswa.core.platform.IPrinterTransport
import com.alsoug.keswa.core.platform.IReceiptRenderer
import com.alsoug.keswa.core.printing.MonoBitmap
import com.alsoug.keswa.core.printing.model.Receipt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** ADR-019: hand-written fakes. */

class SequentialIds(private val prefix: String = "id") : IdGenerator {
    private var next = 0
    override fun newId(): String = "$prefix-${++next}"
}

/**
 * Honest about shape, not about cost: it derives deterministically so tests are fast. The real
 * key-derivation cost is asserted in `DesktopPasswordHasherTest`, where it belongs.
 */
class FakeHasher : IPasswordHasher {
    override suspend fun hash(secret: CharArray, salt: ByteArray): String =
        "hash(${secret.concatToString()}:${salt.joinToString("")})"

    override suspend fun verify(secret: CharArray, salt: ByteArray, expected: String): Boolean =
        hash(secret, salt) == expected

    override fun newSalt(): ByteArray = ByteArray(4) { 1 }
}

class FakeUserRepository : IUserRepository {

    val stored = mutableMapOf<String, StoredCredential>()

    suspend fun add(
        id: String,
        username: String,
        role: UserRole,
        secret: String,
        hasher: FakeHasher = FakeHasher(),
    ): User {
        val salt = "01010101"
        return create(
            id = id,
            username = username,
            displayName = username,
            displayNameAr = username,
            role = role,
            secretKind = if (role == UserRole.SELLER) SecretKind.PIN else SecretKind.PASSWORD,
            secretHash = hasher.hash(secret.toCharArray(), ByteArray(4) { 1 }),
            secretSalt = salt,
        ).getOrThrow()
    }

    override suspend fun create(
        id: String,
        username: String,
        displayName: String,
        displayNameAr: String,
        role: UserRole,
        secretKind: SecretKind,
        secretHash: String,
        secretSalt: String,
        mustChangeSecret: Boolean,
    ): Result<User> {
        val user = User(id, username.lowercase(), displayName, displayNameAr, role, mustChangeSecret)
        stored[id] = StoredCredential(user, secretHash, secretSalt, secretKind, 0, null)
        return Result.success(user)
    }

    override suspend fun findByUsername(username: String): Result<StoredCredential?> =
        Result.success(stored.values.firstOrNull { it.user.username == username.lowercase() })

    override suspend fun findById(id: String): Result<StoredCredential?> = Result.success(stored[id])

    override suspend fun sellers(): Result<List<User>> =
        Result.success(stored.values.map { it.user }.filter { it.role == UserRole.SELLER })

    override suspend fun countActive(): Result<Int> = Result.success(stored.size)

    override suspend fun countActiveAdmins(): Result<Int> =
        Result.success(stored.values.count { it.user.role == UserRole.ADMIN })

    override suspend fun recordFailure(
        userId: String,
        attempts: Int,
        lockedUntil: Long?,
    ): Result<Unit> {
        stored[userId]?.let {
            stored[userId] = it.copy(failedAttempts = attempts, lockedUntil = lockedUntil)
        }
        return Result.success(Unit)
    }

    override suspend fun clearFailures(userId: String): Result<Unit> {
        stored[userId]?.let { stored[userId] = it.copy(failedAttempts = 0, lockedUntil = null) }
        return Result.success(Unit)
    }

    override suspend fun replaceSecret(
        userId: String,
        secretHash: String,
        secretSalt: String,
        mustChangeSecret: Boolean,
    ): Result<Unit> {
        stored[userId]?.let {
            stored[userId] = it.copy(
                user = it.user.copy(mustChangeSecret = mustChangeSecret),
                secretHash = secretHash,
                secretSalt = secretSalt,
                failedAttempts = 0,
                lockedUntil = null,
            )
        }
        return Result.success(Unit)
    }
}

/** Records what it was asked to commit, so a test can assert on the draft the use case built. */
class RecordingSaleRepository : ISaleRepository {

    val recorded = mutableListOf<SaleDraft>()
    var nextReceiptNumber = 1L
    var failWith: Throwable? = null

    override suspend fun record(draft: SaleDraft): Result<Sale> {
        failWith?.let { return Result.failure(it) }
        recorded += draft
        return Result.success(
            Sale(
                id = draft.id,
                receiptNumber = nextReceiptNumber++,
                locationId = draft.locationId,
                priceListId = draft.priceListId,
                userId = draft.userId,
                shiftId = draft.shiftId,
                status = SaleStatus.COMPLETED,
                subtotal = draft.subtotal,
                discount = draft.discount,
                tax = draft.tax,
                total = draft.total,
                tendered = draft.tendered,
                change = draft.change,
                occurredAt = draft.occurredAt,
                lines = draft.lines,
                payments = draft.payments,
            ),
        )
    }

    val voided = mutableListOf<Triple<String, String, String>>()

    override suspend fun void(
        saleId: String,
        byUserId: String,
        reason: String,
        atMillis: Long,
    ): Result<Sale> {
        voided += Triple(saleId, byUserId, reason)
        val draft = recorded.firstOrNull { it.id == saleId }
            ?: return Result.failure(IllegalArgumentException("no such sale"))
        return Result.success(
            record(draft).getOrThrow().copy(
                status = SaleStatus.VOIDED,
                voidedAt = atMillis,
                voidedByUserId = byUserId,
                voidReason = reason,
            ),
        )
    }

    override suspend fun getById(id: String): Result<Sale?> = Result.success(null)

    override suspend fun getByReceiptNumber(receiptNumber: Long): Result<Sale?> = Result.success(null)

    override fun observeRecent(limit: Int): Flow<List<Sale>> = flowOf(emptyList())
}

class FakeSellableRepository(private val items: List<SellableItem> = emptyList()) :
    ISellableRepository {

    override suspend fun byBarcode(
        barcode: String,
        priceListId: String,
        locationId: String,
        at: Long,
    ): Result<SellableItem?> = Result.success(items.firstOrNull { it.sku == barcode })

    override suspend fun byVariantId(
        variantId: String,
        priceListId: String,
        locationId: String,
        at: Long,
    ): Result<SellableItem?> = Result.success(items.firstOrNull { it.variantId == variantId })

    override suspend fun search(
        term: String,
        priceListId: String,
        locationId: String,
        at: Long,
        limit: Int,
    ): Result<List<SellableItem>> =
        Result.success(items.filter { it.name.contains(term, ignoreCase = true) })
}

fun sellable(
    variantId: String = "var-1",
    sku: String = "KSW-TSH-022-NV",
    price: Money? = Money.ofPounds(180),
    onHand: Int = 10,
) = SellableItem(
    variantId = variantId,
    productId = "prod-1",
    sku = sku,
    name = "Round-neck t-shirt",
    nameAr = "تيشيرت رقبة دائرية",
    colourName = "Navy",
    colourNameAr = "كحلي",
    cost = Money.ofPounds(120),
    price = price,
    onHand = onHand,
)

class FakeHeldSaleRepository : IHeldSaleRepository {

    val held = mutableMapOf<String, HeldSale>()

    override suspend fun hold(
        id: String,
        label: String,
        locationId: String,
        userId: String,
        atMillis: Long,
        lines: List<HeldSaleLine>,
    ): Result<HeldSale> {
        val parked = HeldSale(id, label, locationId, userId, atMillis, lines)
        held[id] = parked
        return Result.success(parked)
    }

    override suspend fun getById(id: String): Result<HeldSale?> = Result.success(held[id])

    override suspend fun list(locationId: String): Result<List<HeldSale>> =
        Result.success(held.values.filter { it.locationId == locationId })

    override suspend fun discard(id: String): Result<Unit> {
        held.remove(id)
        return Result.success(Unit)
    }

    override fun observe(locationId: String): Flow<List<HeldSale>> = flowOf(held.values.toList())
}

class FakeSettingsRepository(private var settings: ShopSettings = ShopSettings()) :
    ISettingsRepository {
    override suspend fun get(): Result<ShopSettings> = Result.success(settings)
    override suspend fun save(value: ShopSettings): Result<Unit> {
        settings = value
        return Result.success(Unit)
    }
}

/** Renders a bitmap of the right shape without a font stack, and remembers what it was asked to draw. */
class RecordingRenderer : IReceiptRenderer {
    var lastReceipt: Receipt? = null

    override fun render(receipt: Receipt, widthDots: Int): MonoBitmap {
        lastReceipt = receipt
        return MonoBitmap(widthDots, HEIGHT)
    }

    override fun renderTestPage(widthDots: Int): MonoBitmap =
        MonoBitmap(widthDots, HEIGHT)

    private companion object {
        const val HEIGHT = 8
    }
}

/** Captures the bytes a document would have been sent as, or refuses to open at all. */
class RecordingTransport(private val failOnOpen: Boolean = false) : IPrinterTransport {
    var sent: ByteArray? = null
    var openCount = 0

    override suspend fun open(): Result<Unit> {
        openCount++
        return if (failOnOpen) Result.failure(IllegalStateException("printer is off")) else Result.success(Unit)
    }

    override suspend fun write(bytes: ByteArray): Result<Unit> {
        sent = bytes
        return Result.success(Unit)
    }

    override suspend fun close() = Unit
}
