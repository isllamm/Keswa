package com.alsoug.keswa.features.auth.domain.usecase

import com.alsoug.keswa.core.domain.IdGenerator
import com.alsoug.keswa.core.domain.auth.decodeSalt
import com.alsoug.keswa.core.domain.auth.encodeSalt
import com.alsoug.keswa.core.domain.model.Permission
import com.alsoug.keswa.core.domain.model.SecretKind
import com.alsoug.keswa.core.domain.model.User
import com.alsoug.keswa.core.domain.model.UserRole
import com.alsoug.keswa.core.domain.repository.IUserRepository
import com.alsoug.keswa.core.platform.IPasswordHasher
import com.alsoug.keswa.core.session.ISessionManager
import com.alsoug.keswa.core.session.require

/** True when the database has no accounts — a fresh install, which opens on setup, not sign-in. */
class NeedsFirstRunSetupUseCase(private val users: IUserRepository) {
    suspend operator fun invoke(): Result<Boolean> = users.countActive().map { it == 0 }
}

data class BootstrapResult(val owner: User, val recoveryCode: String)

/**
 * Creates the owner account, and issues the one recovery code.
 *
 * ⚠️ **Q5 is still open.** This implements the recommended answer — a code shown once at setup —
 * because a shop with no server and no email otherwise has *no* path back from a forgotten admin
 * password, and the whole trading history sits in one local file. The alternatives were a mandatory
 * second admin (no help to a one-person shop) and a support-issued unlock (needs a support process
 * to exist). Swapping this out later touches only this use case and [RecoverWithCodeUseCase].
 */
class BootstrapFirstAdminUseCase(
    private val users: IUserRepository,
    private val hasher: IPasswordHasher,
    private val ids: IdGenerator,
    private val recovery: RecoveryCodeStore,
    private val newRecoveryCode: () -> String,
) {
    suspend operator fun invoke(
        username: String,
        displayName: String,
        password: CharArray,
    ): Result<BootstrapResult> = runCatching {
        require(users.countActive().getOrThrow() == 0) { "an account already exists" }
        require(password.size >= MIN_PASSWORD_LENGTH) {
            "an admin password needs at least $MIN_PASSWORD_LENGTH characters"
        }

        val salt = hasher.newSalt()
        val owner = users.create(
            id = ids.newId(),
            username = username,
            displayName = displayName,
            displayNameAr = displayName,
            role = UserRole.ADMIN,
            secretKind = SecretKind.PASSWORD,
            secretHash = hasher.hash(password, salt),
            secretSalt = salt.encodeSalt(),
        ).getOrThrow()

        val code = newRecoveryCode()
        val recoverySalt = hasher.newSalt()
        recovery.store(hasher.hash(code.toCharArray(), recoverySalt), recoverySalt.encodeSalt())

        BootstrapResult(owner, code)
    }

    private companion object {
        const val MIN_PASSWORD_LENGTH = 8
    }
}

/** Where the recovery code's verifier lives. Only ever the derived value, never the code. */
interface RecoveryCodeStore {
    suspend fun store(hash: String, salt: String)
    suspend fun read(): Pair<String, String>?
}

sealed interface RecoveryResult {
    data object Reset : RecoveryResult
    data object WrongCode : RecoveryResult
    data object NoCodeIssued : RecoveryResult
}

/**
 * Sets a new admin password using the recovery code issued at setup.
 *
 * The code is consumed on use and a fresh one is not minted here — an admin who has recovered
 * should generate a new code deliberately, so a copy left in a drawer stops working.
 */
class RecoverWithCodeUseCase(
    private val users: IUserRepository,
    private val hasher: IPasswordHasher,
    private val recovery: RecoveryCodeStore,
) {
    suspend operator fun invoke(
        username: String,
        code: CharArray,
        newPassword: CharArray,
    ): Result<RecoveryResult> = runCatching {
        val stored = recovery.read() ?: return@runCatching RecoveryResult.NoCodeIssued
        val (hash, salt) = stored
        if (!hasher.verify(code, salt.decodeSalt(), hash)) {
            return@runCatching RecoveryResult.WrongCode
        }

        val credential = users.findByUsername(username).getOrThrow()
            ?: return@runCatching RecoveryResult.WrongCode
        val newSalt = hasher.newSalt()
        users.replaceSecret(
            userId = credential.user.id,
            secretHash = hasher.hash(newPassword, newSalt),
            secretSalt = newSalt.encodeSalt(),
            mustChangeSecret = false,
        ).getOrThrow()
        RecoveryResult.Reset
    }
}

/**
 * Adds a seller, or another admin.
 *
 * Permission is checked here rather than by hiding a menu item, because the screen is not a
 * security boundary — on an offline desktop app the user owns the machine the UI runs on.
 */
class CreateUserUseCase(
    private val users: IUserRepository,
    private val hasher: IPasswordHasher,
    private val ids: IdGenerator,
    private val sessions: ISessionManager,
) {
    suspend operator fun invoke(
        username: String,
        displayName: String,
        displayNameAr: String,
        role: UserRole,
        secret: CharArray,
    ): Result<User> = runCatching {
        sessions.require(Permission.MANAGE_USERS)

        val kind = if (role == UserRole.SELLER) SecretKind.PIN else SecretKind.PASSWORD
        require(secret.size >= kind.minimumLength) {
            "a ${kind.name.lowercase()} needs at least ${kind.minimumLength} characters"
        }

        val salt = hasher.newSalt()
        users.create(
            id = ids.newId(),
            username = username,
            displayName = displayName,
            displayNameAr = displayNameAr,
            role = role,
            secretKind = kind,
            secretHash = hasher.hash(secret, salt),
            secretSalt = salt.encodeSalt(),
        ).getOrThrow()
    }
}

/** An admin resetting someone else's credential forces a change at next sign-in. */
class ResetUserSecretUseCase(
    private val users: IUserRepository,
    private val hasher: IPasswordHasher,
    private val sessions: ISessionManager,
) {
    suspend operator fun invoke(userId: String, temporarySecret: CharArray): Result<Unit> =
        runCatching {
            sessions.require(Permission.MANAGE_USERS)
            val salt = hasher.newSalt()
            users.replaceSecret(
                userId = userId,
                secretHash = hasher.hash(temporarySecret, salt),
                secretSalt = salt.encodeSalt(),
                mustChangeSecret = true,
            ).getOrThrow()
        }
}

class ChangeOwnSecretUseCase(
    private val users: IUserRepository,
    private val hasher: IPasswordHasher,
    private val sessions: ISessionManager,
) {
    suspend operator fun invoke(userId: String, newSecret: CharArray): Result<Unit> = runCatching {
        val credential = users.findById(userId).getOrThrow() ?: error("user not found")
        require(newSecret.size >= credential.secretKind.minimumLength) {
            "too short for a ${credential.secretKind.name.lowercase()}"
        }
        val salt = hasher.newSalt()
        users.replaceSecret(
            userId = userId,
            secretHash = hasher.hash(newSecret, salt),
            secretSalt = salt.encodeSalt(),
            mustChangeSecret = false,
        ).getOrThrow()
    }
}

class ListSellersUseCase(private val users: IUserRepository) {
    suspend operator fun invoke(): Result<List<User>> = users.sellers()
}

private val SecretKind.minimumLength: Int
    get() = when (this) {
        SecretKind.PIN -> 4
        SecretKind.PASSWORD -> 8
    }
