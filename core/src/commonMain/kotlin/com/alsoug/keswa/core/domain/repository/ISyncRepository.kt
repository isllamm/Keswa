package com.alsoug.keswa.core.domain.repository

import com.alsoug.keswa.core.domain.model.SyncDeviceStatus
import com.alsoug.keswa.core.domain.model.SyncResult

interface ISyncRepository {
    suspend fun getStatus(): Result<SyncDeviceStatus>
    suspend fun enrol(serverUrl: String, deviceName: String, code: String): Result<Unit>
    suspend fun syncNow(): Result<SyncResult>
}
