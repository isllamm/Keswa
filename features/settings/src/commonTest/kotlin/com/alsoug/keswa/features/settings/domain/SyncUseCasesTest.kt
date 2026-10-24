package com.alsoug.keswa.features.settings.domain

import com.alsoug.keswa.core.domain.model.SyncDeviceStatus
import com.alsoug.keswa.core.domain.model.SyncResult
import com.alsoug.keswa.core.domain.repository.ISyncRepository
import com.alsoug.keswa.features.settings.domain.usecase.EnrolDeviceUseCase
import com.alsoug.keswa.features.settings.domain.usecase.GetSyncStatusUseCase
import com.alsoug.keswa.features.settings.domain.usecase.SyncNowUseCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class FakeSyncRepository(
    var status: SyncDeviceStatus = SyncDeviceStatus(
        deviceId = "dev-1",
        deviceName = "Till 1",
        serverUrl = null,
        ordinal = 0,
        isEnrolled = false,
        receiptRange = "#0 – #999999",
    ),
    var syncResult: SyncResult = SyncResult(
        pushed = 2,
        pulled = 5,
        deferred = 0,
        superseded = 0,
        isConfigured = true,
        isEnrolled = true,
    ),
    var enrolError: Throwable? = null,
    var syncError: Throwable? = null,
) : ISyncRepository {

    var enrolledUrl: String? = null
    var enrolledName: String? = null
    var enrolledCode: String? = null
    var syncCount: Int = 0

    override suspend fun getStatus(): Result<SyncDeviceStatus> = Result.success(status)

    override suspend fun enrol(
        serverUrl: String,
        deviceName: String,
        code: String,
    ): Result<Unit> {
        enrolError?.let { return Result.failure(it) }
        enrolledUrl = serverUrl
        enrolledName = deviceName
        enrolledCode = code
        status = status.copy(
            serverUrl = serverUrl,
            deviceName = deviceName,
            isEnrolled = true,
            ordinal = 1,
            receiptRange = "#1000000 – #1999999",
        )
        return Result.success(Unit)
    }

    override suspend fun syncNow(): Result<SyncResult> {
        syncError?.let { return Result.failure(it) }
        syncCount++
        return Result.success(syncResult)
    }
}

class SyncUseCasesTest {

    @Test
    fun `get status returns current device sync configuration`() = runTest {
        val repo = FakeSyncRepository()
        val useCase = GetSyncStatusUseCase(repo)

        val result = useCase().getOrThrow()

        assertEquals("dev-1", result.deviceId)
        assertEquals("Till 1", result.deviceName)
        assertFalse(result.isEnrolled)
    }

    @Test
    fun `enrol rejects empty server url`() = runTest {
        val repo = FakeSyncRepository()
        val useCase = EnrolDeviceUseCase(repo)

        val result = useCase(serverUrl = "   ", deviceName = "Till 1", code = "CODE1234")

        assertTrue(result.isFailure)
        assertEquals(null, repo.enrolledUrl)
    }

    @Test
    fun `enrol rejects empty code`() = runTest {
        val repo = FakeSyncRepository()
        val useCase = EnrolDeviceUseCase(repo)

        val result = useCase(serverUrl = "http://localhost:8080", deviceName = "Till 1", code = "   ")

        assertTrue(result.isFailure)
        assertEquals(null, repo.enrolledCode)
    }

    @Test
    fun `enrol forwards trimmed parameters and updates status`() = runTest {
        val repo = FakeSyncRepository()
        val useCase = EnrolDeviceUseCase(repo)

        val result = useCase(
            serverUrl = "  http://192.168.1.100:8080/  ",
            deviceName = "  Counter A  ",
            code = "  AB12CD34  ",
        )

        assertTrue(result.isSuccess)
        assertEquals("  http://192.168.1.100:8080/  ".trim(), repo.enrolledUrl)
        assertEquals("Counter A", repo.enrolledName)
        assertEquals("AB12CD34", repo.enrolledCode)
        assertTrue(repo.status.isEnrolled)
    }

    @Test
    fun `sync now returns pushed and pulled counts`() = runTest {
        val repo = FakeSyncRepository()
        val useCase = SyncNowUseCase(repo)

        val result = useCase().getOrThrow()

        assertEquals(2, result.pushed)
        assertEquals(5, result.pulled)
        assertEquals(1, repo.syncCount)
    }
}
