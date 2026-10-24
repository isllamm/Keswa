package com.alsoug.keswa.features.auth.presentation

import com.alsoug.keswa.core.coroutines.DispatcherProvider
import com.alsoug.keswa.core.domain.auth.encodeSalt
import com.alsoug.keswa.core.domain.model.SecretKind
import com.alsoug.keswa.core.domain.model.UserRole
import com.alsoug.keswa.core.session.InMemorySessionManager
import com.alsoug.keswa.features.auth.domain.FakeHasher
import com.alsoug.keswa.features.auth.domain.FakeRecoveryStore
import com.alsoug.keswa.features.auth.domain.FakeUserRepository
import com.alsoug.keswa.features.auth.domain.SequentialIds
import com.alsoug.keswa.features.auth.domain.usecase.BootstrapFirstAdminUseCase
import com.alsoug.keswa.features.auth.domain.usecase.ChangeOwnSecretUseCase
import com.alsoug.keswa.features.auth.domain.usecase.ListSellersUseCase
import com.alsoug.keswa.features.auth.domain.usecase.NeedsFirstRunSetupUseCase
import com.alsoug.keswa.features.auth.domain.usecase.SignInUseCase
import com.alsoug.keswa.features.auth.presentation.screens.signin.SignInMode
import com.alsoug.keswa.features.auth.presentation.screens.signin.SignInNavigation
import com.alsoug.keswa.features.auth.presentation.screens.signin.SignInUiEffect
import com.alsoug.keswa.features.auth.presentation.screens.signin.SignInUiEvent
import com.alsoug.keswa.features.auth.presentation.screens.signin.SignInViewModel
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class SignInViewModelTest {

    private val scheduler = StandardTestDispatcher()
    private val dispatchers = object : DispatcherProvider {
        override val io: CoroutineDispatcher = scheduler
        override val main: CoroutineDispatcher = scheduler
        override val default: CoroutineDispatcher = scheduler
    }

    private var users = FakeUserRepository()
    private val hasher = FakeHasher()
    private var sessions = InMemorySessionManager()
    private var recovery = FakeRecoveryStore()
    private var now = 1_000_000L

    private fun viewModel() = SignInViewModel(
        signIn = SignInUseCase(users, hasher, sessions) { now },
        needsSetup = NeedsFirstRunSetupUseCase(users),
        bootstrap = BootstrapFirstAdminUseCase(
            users, hasher, SequentialIds(), recovery, newRecoveryCode = { "RECOVERY-123" },
        ),
        listSellers = ListSellersUseCase(users),
        changeSecret = ChangeOwnSecretUseCase(users, hasher, sessions),
        dispatchers = dispatchers,
    )

    private suspend fun givenSeller(pin: String = "1234", id: String = "u1", name: String = "Mona") {
        val salt = hasher.newSalt()
        users.create(
            id = id,
            username = name.lowercase(),
            displayName = name,
            displayNameAr = name,
            role = UserRole.SELLER,
            secretKind = SecretKind.PIN,
            secretHash = hasher.hash(pin.toCharArray(), salt),
            secretSalt = salt.encodeSalt(),
        )
    }

    private suspend fun givenAdmin(password: String = "adminPass123", id: String = "admin-1", username: String = "admin") {
        val salt = hasher.newSalt()
        users.create(
            id = id,
            username = username,
            displayName = "Admin",
            displayNameAr = "مسؤول",
            role = UserRole.ADMIN,
            secretKind = SecretKind.PASSWORD,
            secretHash = hasher.hash(password.toCharArray(), salt),
            secretSalt = salt.encodeSalt(),
        )
    }

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(scheduler)
        users = FakeUserRepository()
        sessions = InMemorySessionManager()
        recovery = FakeRecoveryStore()
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `load on fresh install indicates setup needed and defaults to ADMIN mode`() = runTest(scheduler) {
        val vm = viewModel()
        vm.onEvent(SignInUiEvent.Load)
        advanceUntilIdle()

        val state = vm.state.value
        assertFalse(state.isLoading)
        assertTrue(state.needsSetup)
        assertEquals(SignInMode.ADMIN, state.mode)
        assertTrue(state.sellers.isEmpty())
    }

    @Test
    fun `load with existing sellers defaults to SELLER mode and selects first seller`() = runTest(scheduler) {
        givenSeller(id = "s1", name = "Sami")
        givenSeller(id = "s2", name = "Mona")

        val vm = viewModel()
        vm.onEvent(SignInUiEvent.Load)
        advanceUntilIdle()

        val state = vm.state.value
        assertFalse(state.isLoading)
        assertFalse(state.needsSetup)
        assertEquals(2, state.sellers.size)
        assertEquals("s1", state.selectedSellerId)
    }

    @Test
    fun `entering 4 PIN digits automatically submits PIN and navigates to SignedIn on success`() = runTest(scheduler) {
        givenSeller(pin = "5678", id = "s1", name = "Sami")

        val vm = viewModel()
        val navigations = mutableListOf<SignInNavigation>()
        val job = launch { vm.navigation.collect { navigations += it } }
        runCurrent()

        vm.onEvent(SignInUiEvent.Load)
        advanceUntilIdle()

        vm.onEvent(SignInUiEvent.PinDigit('5'))
        vm.onEvent(SignInUiEvent.PinDigit('6'))
        vm.onEvent(SignInUiEvent.PinDigit('7'))
        assertEquals("567", vm.state.value.pin)

        vm.onEvent(SignInUiEvent.PinDigit('8'))
        advanceUntilIdle()

        assertEquals(1, navigations.size)
        assertEquals(SignInNavigation.SignedIn, navigations.first())
        assertEquals("s1", sessions.current.value?.user?.id)
        assertEquals("", vm.state.value.pin)
        job.cancel()
    }

    @Test
    fun `incorrect PIN emits error effect and clears entered PIN`() = runTest(scheduler) {
        givenSeller(pin = "5678", id = "s1", name = "Sami")

        val vm = viewModel()
        val effects = mutableListOf<SignInUiEffect>()
        val job = launch { vm.effect.collect { effects += it } }
        runCurrent()

        vm.onEvent(SignInUiEvent.Load)
        advanceUntilIdle()

        vm.onEvent(SignInUiEvent.PinDigit('0'))
        vm.onEvent(SignInUiEvent.PinDigit('0'))
        vm.onEvent(SignInUiEvent.PinDigit('0'))
        vm.onEvent(SignInUiEvent.PinDigit('0'))
        advanceUntilIdle()

        assertEquals(1, effects.size)
        assertIs<SignInUiEffect.ShowError>(effects.first())
        assertNull(sessions.current.value)
        assertEquals("", vm.state.value.pin)
        job.cancel()
    }

    @Test
    fun `backspace removes last entered PIN digit`() = runTest(scheduler) {
        val vm = viewModel()
        vm.onEvent(SignInUiEvent.PinDigit('1'))
        vm.onEvent(SignInUiEvent.PinDigit('2'))
        assertEquals("12", vm.state.value.pin)

        vm.onEvent(SignInUiEvent.PinBackspace)
        assertEquals("1", vm.state.value.pin)
    }

    @Test
    fun `admin password sign in navigates to SignedIn on success`() = runTest(scheduler) {
        givenAdmin(password = "secretAdmin99", username = "superadmin")

        val vm = viewModel()
        val navigations = mutableListOf<SignInNavigation>()
        val job = launch { vm.navigation.collect { navigations += it } }
        runCurrent()

        vm.onEvent(SignInUiEvent.ModeChanged(SignInMode.ADMIN))
        vm.onEvent(SignInUiEvent.UsernameChanged("superadmin"))
        vm.onEvent(SignInUiEvent.PasswordChanged("secretAdmin99"))
        vm.onEvent(SignInUiEvent.SubmitPassword)
        advanceUntilIdle()

        assertEquals(1, navigations.size)
        assertEquals(SignInNavigation.SignedIn, navigations.first())
        assertEquals("superadmin", sessions.current.value?.user?.username)
        assertEquals("", vm.state.value.password)
        job.cancel()
    }

    @Test
    fun `admin password sign in with wrong password shows error effect`() = runTest(scheduler) {
        givenAdmin(password = "correctPass123", username = "superadmin")

        val vm = viewModel()
        val effects = mutableListOf<SignInUiEffect>()
        val job = launch { vm.effect.collect { effects += it } }
        runCurrent()

        vm.onEvent(SignInUiEvent.ModeChanged(SignInMode.ADMIN))
        vm.onEvent(SignInUiEvent.UsernameChanged("superadmin"))
        vm.onEvent(SignInUiEvent.PasswordChanged("wrongPassword"))
        vm.onEvent(SignInUiEvent.SubmitPassword)
        advanceUntilIdle()

        assertEquals(1, effects.size)
        assertIs<SignInUiEffect.ShowError>(effects.first())
        assertNull(sessions.current.value)
        job.cancel()
    }

    @Test
    fun `bootstrap creates first admin, gives recovery code, and dismiss triggers navigation`() = runTest(scheduler) {
        val vm = viewModel()
        val navigations = mutableListOf<SignInNavigation>()
        val job = launch { vm.navigation.collect { navigations += it } }
        runCurrent()

        vm.onEvent(SignInUiEvent.Bootstrap("hala", "Hala Fouad", "keswaAdminPass2026"))
        advanceUntilIdle()

        val state = vm.state.value
        assertFalse(state.needsSetup)
        assertEquals("RECOVERY-123", state.recoveryCode)

        vm.onEvent(SignInUiEvent.DismissRecoveryCode)
        advanceUntilIdle()

        assertNull(vm.state.value.recoveryCode)
        assertEquals(1, navigations.size)
        assertEquals(SignInNavigation.SignedIn, navigations.first())
        job.cancel()
    }
}
