package com.alsoug.keswa.features.sell.domain.usecase

import com.alsoug.keswa.core.domain.model.Permission
import com.alsoug.keswa.core.domain.model.User
import com.alsoug.keswa.core.session.ApprovalResult
import com.alsoug.keswa.core.session.ICredentialVerifier

sealed interface ReauthResult {
    data class Approved(val user: User) : ReauthResult
    data object BadCredentials : ReauthResult
    data object NotPermitted : ReauthResult
    data class Locked(val untilMillis: Long) : ReauthResult
}

/**
 * The till's word for asking an admin to approve a discount, an overridden price or a void.
 *
 * The verification itself lives in `:core` as [ICredentialVerifier] — two features need it now, and
 * one of them is returns. This is the thin layer that gives the till its own vocabulary for it.
 */
class ReauthenticateUseCase(private val verifier: ICredentialVerifier) {

    suspend operator fun invoke(
        username: String,
        secret: CharArray,
        permission: Permission,
    ): Result<ReauthResult> = runCatching {
        when (val result = verifier.approve(username, secret, permission)) {
            is ApprovalResult.Approved -> ReauthResult.Approved(result.user)
            ApprovalResult.BadCredentials -> ReauthResult.BadCredentials
            ApprovalResult.NotPermitted -> ReauthResult.NotPermitted
            is ApprovalResult.Locked -> ReauthResult.Locked(result.untilMillis)
        }
    }
}
