package com.alsoug.keswa.core.data.repository

import com.alsoug.keswa.core.coroutines.runCatchingCancellable
import com.alsoug.keswa.core.domain.model.SyncDeviceStatus
import com.alsoug.keswa.core.domain.model.SyncResult
import com.alsoug.keswa.core.domain.repository.ISyncRepository
import com.alsoug.keswa.core.sync.SyncEngine
import com.alsoug.keswa.core.sync.SyncOutcome
import com.alsoug.keswa.core.sync.SyncSettings

class SyncRepositoryImpl(
    private val engine: SyncEngine,
    private val settings: SyncSettings,
) : ISyncRepository {

    override suspend fun getStatus(): Result<SyncDeviceStatus> = runCatchingCancellable {
        val block = settings.receiptBlock()
        SyncDeviceStatus(
            deviceId = settings.deviceId(),
            deviceName = settings.deviceName(),
            serverUrl = settings.serverUrl(),
            ordinal = settings.ordinal(),
            isEnrolled = settings.isEnrolled(),
            receiptRange = "#${block.first} – #${block.last}",
        )
    }

    override suspend fun enrol(
        serverUrl: String,
        deviceName: String,
        code: String,
    ): Result<Unit> = runCatchingCancellable {
        val cleanUrl = serverUrl.trim().removeSuffix("/")
        val cleanName = deviceName.trim().ifEmpty { "Till" }
        settings.setDeviceName(cleanName)
        engine.enrol(cleanUrl, code.trim()).getOrThrow()
    }

    override suspend fun syncNow(): Result<SyncResult> = runCatchingCancellable {
        when (val outcome = engine.syncOnce().getOrThrow()) {
            SyncOutcome.NotConfigured -> SyncResult(
                pushed = 0,
                pulled = 0,
                deferred = 0,
                superseded = 0,
                isConfigured = false,
                isEnrolled = false,
            )
            SyncOutcome.NotEnrolled -> SyncResult(
                pushed = 0,
                pulled = 0,
                deferred = 0,
                superseded = 0,
                isConfigured = true,
                isEnrolled = false,
            )
            is SyncOutcome.Completed -> SyncResult(
                pushed = outcome.pushed,
                pulled = outcome.pulled,
                deferred = outcome.deferred,
                superseded = outcome.superseded,
                isConfigured = true,
                isEnrolled = true,
            )
        }
    }
}
