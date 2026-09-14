package com.alsoug.keswa.core.platform

/**
 * Log severity levels.
 */
enum class LogLevel {
    DEBUG,
    INFO,
    WARNING,
    ERROR,
}

/**
 * Access to platform capabilities from shared code.
 *
 * Design principles, inherited from ADR-018:
 * - Capability-based: exposes what you can DO, not what the platform HAS
 * - Type-safe: no raw context exposure or downcasting
 * - Testable: trivially faked in commonTest
 *
 * Implementations are registered in the platform DI module in `:composeApp`.
 */
interface IPlatformProvider {
    val platformName: String
    val platformVersion: String

    fun log(
        level: LogLevel,
        tag: String,
        message: String,
        throwable: Throwable? = null,
    )
}
