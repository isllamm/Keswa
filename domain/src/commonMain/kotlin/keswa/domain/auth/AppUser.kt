package keswa.domain.auth

data class AppUser(
    val id: String,
    val tenantId: String,
    val storeId: String,
    val fullName: String,
    val username: String,
    val pinHash: String,
    val pinSalt: String,
    val roleCode: String,
    val isActive: Boolean,
    val mustChangePin: Boolean,
    val lockedUntil: Long?,
    val failedAttempts: Int,
)
