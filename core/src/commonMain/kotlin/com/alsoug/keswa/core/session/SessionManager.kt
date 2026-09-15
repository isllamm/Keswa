package com.alsoug.keswa.core.session

import com.alsoug.keswa.core.domain.model.Permission
import com.alsoug.keswa.core.domain.model.Session
import com.alsoug.keswa.core.domain.model.User
import com.alsoug.keswa.core.domain.model.can
import com.alsoug.keswa.core.error.Error
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Who is signed in, right now.
 *
 * **In memory only — nothing is persisted.** Closing the app signs everyone out, which is correct
 * for a till several people share, and it means KD-006 (there is no secure storage on desktop)
 * does not block this phase: with no server there is no token, so nothing Tier-1 to store. That
 * gap comes due in Phase 9.
 */
interface ISessionManager {

    val current: StateFlow<Session?>

    /** A session that is signed in but locked — the cart survives, the user must re-enter a PIN. */
    val isLocked: StateFlow<Boolean>

    fun signIn(user: User, atMillis: Long)

    fun lock()

    fun unlock()

    fun signOut()
}

class InMemorySessionManager : ISessionManager {

    private val _current = MutableStateFlow<Session?>(null)
    override val current: StateFlow<Session?> = _current.asStateFlow()

    private val _isLocked = MutableStateFlow(false)
    override val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    override fun signIn(user: User, atMillis: Long) {
        _current.value = Session(user, atMillis)
        _isLocked.value = false
    }

    override fun lock() {
        if (_current.value != null) _isLocked.value = true
    }

    override fun unlock() {
        _isLocked.value = false
    }

    override fun signOut() {
        _current.value = null
        _isLocked.value = false
    }
}

/**
 * Enforces a permission, or throws.
 *
 * **Call this in the use case, not the screen.** Hiding a button is a usability affordance, not a
 * security control — and on an offline desktop app the user owns the machine the UI runs on.
 */
fun Session?.require(permission: Permission) {
    if (!can(permission)) {
        throw Error.ForbiddenAccess("not permitted: ${permission.name}")
    }
}

fun ISessionManager.require(permission: Permission) = current.value.require(permission)
