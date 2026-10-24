package com.alsoug.keswa.features.settings.domain.usecase

import com.alsoug.keswa.core.domain.model.SyncDeviceStatus
import com.alsoug.keswa.core.domain.model.SyncResult
import com.alsoug.keswa.core.domain.repository.ISyncRepository

class GetSyncStatusUseCase(private val repository: ISyncRepository) {
    suspend operator fun invoke(): Result<SyncDeviceStatus> = repository.getStatus()
}

class EnrolDeviceUseCase(private val repository: ISyncRepository) {
    suspend operator fun invoke(serverUrl: String, deviceName: String, code: String): Result<Unit> {
        val trimmedUrl = serverUrl.trim()
        val trimmedCode = code.trim()
        val trimmedName = deviceName.trim().ifEmpty { "Till" }
        if (trimmedUrl.isEmpty()) {
            return Result.failure(IllegalArgumentException("server url cannot be blank"))
        }
        if (trimmedCode.isEmpty()) {
            return Result.failure(IllegalArgumentException("enrolment code cannot be blank"))
        }
        return repository.enrol(trimmedUrl, trimmedName, trimmedCode)
    }
}

class SyncNowUseCase(private val repository: ISyncRepository) {
    suspend operator fun invoke(): Result<SyncResult> = repository.syncNow()
}
