package keswa.feature.auth

import keswa.core.common.AppResult
import keswa.core.ui.mvi.MviStore
import keswa.domain.PrincipalHolder
import keswa.domain.auth.AppUserRepository
import keswa.domain.auth.UnlockWithPin
import keswa.feature.auth.PinContract.Effect
import keswa.feature.auth.PinContract.Intent
import keswa.feature.auth.PinContract.State

class PinStore(
    private val users: AppUserRepository,
    private val unlockWithPin: UnlockWithPin,
    private val principalHolder: PrincipalHolder,
) : MviStore<State, Intent, Effect>(State()) {

    override fun reduce(state: State, intent: Intent): State = when (intent) {
        is Intent.ScreenEntered -> state.copy(isLoadingUsers = true, error = null)

        is Intent.UserSelected -> {
            val user = state.users.find { it.id == intent.userId }
            state.copy(selectedUser = user, pin = "", error = null)
        }

        is Intent.BackToUserPicker -> state.copy(selectedUser = null, pin = "", error = null)

        is Intent.DigitPressed ->
            if (state.isUnlocking || state.pin.length >= PinContract.PIN_LENGTH) state
            else state.copy(pin = state.pin + intent.digit, error = null)

        is Intent.BackspacePressed ->
            if (state.isUnlocking) state else state.copy(pin = state.pin.dropLast(1))

        is Intent.UnlockClicked ->
            if (state.isUnlocking) state else state.copy(isUnlocking = true, error = null)

        is Intent.ErrorDismissed -> state.copy(error = null)

        is Intent.Internal.UsersLoaded ->
            state.copy(isLoadingUsers = false, users = intent.users)

        is Intent.Internal.UnlockSucceeded -> State(isLoadingUsers = false)

        is Intent.Internal.UnlockFailed ->
            state.copy(isUnlocking = false, pin = "", error = intent.error)
    }

    override suspend fun handle(intent: Intent, state: State) {
        when (intent) {
            is Intent.ScreenEntered -> {
                val loaded = users.findActiveForCurrentStore()
                dispatch(Intent.Internal.UsersLoaded(loaded))
            }

            is Intent.DigitPressed -> {
                // state here is pre-reduction: this is the length *before* this digit was appended.
                val wouldCompletePin = !state.isUnlocking && state.pin.length == PinContract.PIN_LENGTH - 1
                if (wouldCompletePin && state.selectedUser != null) {
                    attemptUnlock(state.selectedUser, state.pin + intent.digit)
                }
            }

            is Intent.UnlockClicked -> {
                if (state.isUnlocking) return // guard: pre-reduction state, see MviStore's kdoc
                val user = state.selectedUser ?: return
                attemptUnlock(user, state.pin)
            }

            else -> Unit
        }
    }

    private suspend fun attemptUnlock(user: keswa.domain.auth.AppUser, pin: String) {
        when (val result = unlockWithPin(user.id, pin)) {
            is AppResult.Ok -> {
                principalHolder.set(result.value)
                dispatch(Intent.Internal.UnlockSucceeded(result.value))
                emit(Effect.Unlocked(result.value))
            }
            is AppResult.Err -> dispatch(Intent.Internal.UnlockFailed(result.error.key))
        }
    }
}
