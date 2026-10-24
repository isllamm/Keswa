package com.alsoug.keswa.core.domain.model

data class SyncDeviceStatus(
    val deviceId: String,
    val deviceName: String,
    val serverUrl: String?,
    val ordinal: Int,
    val isEnrolled: Boolean,
    val receiptRange: String,
)

data class SyncResult(
    val pushed: Int,
    val pulled: Int,
    val deferred: Int,
    val superseded: Int,
    val isConfigured: Boolean,
    val isEnrolled: Boolean,
)
