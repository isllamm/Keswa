package keswa.feature.auth

import keswa.core.common.ErrorKey
import keswa.core.ui.mvi.MviEffect
import keswa.core.ui.mvi.MviIntent
import keswa.core.ui.mvi.MviState
import keswa.domain.Principal
import keswa.domain.auth.AppUser

/**
 * The reference Contract/Store/Route/Screen every later feature copies — see
 * docs/presentation-architecture.md. Two visual states in one screen: no user selected yet shows
 * a picker; a selected user shows a PIN pad for them.
 */
object PinContract {

    data class State(
        val isLoadingUsers: Boolean = true,
        val users: List<AppUser> = emptyList(),
        val selectedUser: AppUser? = null,
        val pin: String = "",
        val isUnlocking: Boolean = false,
        val error: ErrorKey? = null,
    ) : MviState

    sealed interface Intent : MviIntent {
        data object ScreenEntered : Intent
        data class UserSelected(val userId: String) : Intent
        data object BackToUserPicker : Intent
        data class DigitPressed(val digit: Char) : Intent
        data object BackspacePressed : Intent
        data object UnlockClicked : Intent
        data object ErrorDismissed : Intent

        sealed interface Internal : Intent {
            data class UsersLoaded(val users: List<AppUser>) : Internal
            data class UnlockSucceeded(val principal: Principal) : Internal
            data class UnlockFailed(val error: ErrorKey) : Internal
        }
    }

    sealed interface Effect : MviEffect {
        data class Unlocked(val principal: Principal) : Effect
    }

    /** A 4-digit PIN is the whole point of a fast till unlock — see docs/architecture.md §7.4-adjacent ADR-008. */
    const val PIN_LENGTH = 4
}
