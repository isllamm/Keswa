package com.alsoug.keswa.features.settings.presentation

import com.alsoug.keswa.core.coroutines.DispatcherProvider
import com.alsoug.keswa.core.domain.model.ShopSettings
import com.alsoug.keswa.core.domain.repository.ISettingsRepository
import com.alsoug.keswa.core.platform.ILabelNameRenderer
import com.alsoug.keswa.core.platform.IReceiptRenderer
import com.alsoug.keswa.core.printing.MonoBitmap
import com.alsoug.keswa.core.printing.model.Receipt
import com.alsoug.keswa.features.settings.domain.FakeSyncRepository
import com.alsoug.keswa.features.settings.domain.usecase.EnrolDeviceUseCase
import com.alsoug.keswa.features.settings.domain.usecase.GetSettingsUseCase
import com.alsoug.keswa.features.settings.domain.usecase.GetSyncStatusUseCase
import com.alsoug.keswa.features.settings.domain.usecase.PrintTestLabelUseCase
import com.alsoug.keswa.features.settings.domain.usecase.PrintTestPageUseCase
import com.alsoug.keswa.features.settings.domain.usecase.SaveSettingsUseCase
import com.alsoug.keswa.features.settings.domain.usecase.SyncNowUseCase
import com.alsoug.keswa.features.settings.presentation.screens.settings.SettingsNavigation
import com.alsoug.keswa.features.settings.presentation.screens.settings.SettingsUiEffect
import com.alsoug.keswa.features.settings.presentation.screens.settings.SettingsUiEvent
import com.alsoug.keswa.features.settings.presentation.screens.settings.SettingsViewModel
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

private class FakeSettingsRepo(var settings: ShopSettings = ShopSettings()) : ISettingsRepository {
    override suspend fun get(): Result<ShopSettings> = Result.success(settings)
    override suspend fun save(settings: ShopSettings): Result<Unit> {
        this.settings = settings
        return Result.success(Unit)
    }
}

private class StubReceiptRenderer : IReceiptRenderer {
    override fun render(receipt: Receipt, widthDots: Int) = MonoBitmap(widthDots, 4)
    override fun renderTestPage(widthDots: Int) = MonoBitmap(widthDots, 4)
}

private class StubNameRenderer : ILabelNameRenderer {
    override fun render(text: String, widthDots: Int, maxHeightDots: Int) = MonoBitmap(widthDots, 4)
}

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val scheduler = StandardTestDispatcher()
    private val dispatchers = object : DispatcherProvider {
        override val io: CoroutineDispatcher = scheduler
        override val main: CoroutineDispatcher = scheduler
        override val default: CoroutineDispatcher = scheduler
    }

    private val settingsRepo = FakeSettingsRepo()
    private val syncRepo = FakeSyncRepository()

    private fun createViewModel(): SettingsViewModel {
        return SettingsViewModel(
            getSettings = GetSettingsUseCase(settingsRepo),
            saveSettings = SaveSettingsUseCase(settingsRepo),
            printTestPage = PrintTestPageUseCase(settingsRepo, StubReceiptRenderer()) { _, _ -> error("unused") },
            printTestLabel = PrintTestLabelUseCase(settingsRepo, StubNameRenderer()) { _, _ -> error("unused") },
            getSyncStatus = GetSyncStatusUseCase(syncRepo),
            enrolDevice = EnrolDeviceUseCase(syncRepo),
            syncNow = SyncNowUseCase(syncRepo),
            dispatchers = dispatchers,
        )
    }

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(scheduler)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `load fetches settings and sync status into state`() = runTest {
        val vm = createViewModel()

        vm.onEvent(SettingsUiEvent.Load)
        advanceUntilIdle()

        val state = vm.state.value
        assertFalse(state.isLoading)
        assertEquals("Keswa", state.settings.shopName)
        assertEquals("dev-1", state.syncStatus?.deviceId)
        assertEquals("Till 1", state.syncStatus?.deviceName)
        assertFalse(state.syncStatus?.isEnrolled ?: true)
    }

    @Test
    fun `enrol updates status and triggers immediate sync`() = runTest {
        val vm = createViewModel()
        vm.onEvent(SettingsUiEvent.Load)
        advanceUntilIdle()

        val effects = mutableListOf<SettingsUiEffect>()
        val job = launch { vm.effect.collect { effects += it } }

        vm.onEvent(SettingsUiEvent.EditSyncServerUrl("http://192.168.1.100:8080"))
        vm.onEvent(SettingsUiEvent.EditSyncDeviceName("Till 2"))
        vm.onEvent(SettingsUiEvent.EditSyncCode("AB12CD34"))
        vm.onEvent(SettingsUiEvent.EnrolDevice)
        advanceUntilIdle()

        assertEquals("http://192.168.1.100:8080", syncRepo.enrolledUrl)
        assertEquals("Till 2", syncRepo.enrolledName)
        assertEquals("AB12CD34", syncRepo.enrolledCode)
        assertTrue(syncRepo.status.isEnrolled)
        assertEquals(1, syncRepo.syncCount)
        assertTrue(vm.state.value.syncStatus?.isEnrolled ?: false)

        assertTrue(effects.any { it is SettingsUiEffect.ShowMessage })
        job.cancel()
    }

    @Test
    fun `sync now performs sync pass and shows result message`() = runTest {
        val vm = createViewModel()
        vm.onEvent(SettingsUiEvent.Load)
        advanceUntilIdle()

        val effects = mutableListOf<SettingsUiEffect>()
        val job = launch { vm.effect.collect { effects += it } }

        vm.onEvent(SettingsUiEvent.SyncNow)
        advanceUntilIdle()

        assertEquals(1, syncRepo.syncCount)
        assertTrue(effects.any { it is SettingsUiEffect.ShowMessage })
        job.cancel()
    }

    @Test
    fun `back navigation emits back event`() = runTest {
        val vm = createViewModel()
        val nav = mutableListOf<SettingsNavigation>()
        val job = launch { vm.navigation.collect { nav += it } }

        vm.onEvent(SettingsUiEvent.Back)
        advanceUntilIdle()

        assertEquals(listOf<SettingsNavigation>(SettingsNavigation.Back), nav)
        job.cancel()
    }
}
