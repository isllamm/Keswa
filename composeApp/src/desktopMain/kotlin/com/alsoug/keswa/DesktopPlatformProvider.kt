package com.alsoug.keswa

import com.alsoug.keswa.core.platform.IPlatformProvider
import com.alsoug.keswa.core.platform.LogLevel
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Desktop [IPlatformProvider]. Naming follows KD-004 (`Desktop*`), extending the
 * `Android*` / `Ios*` convention in `kmp_cashimobile`.
 */
class DesktopPlatformProvider : IPlatformProvider {

    override val platformName: String = System.getProperty("os.name") ?: "Desktop"

    override val platformVersion: String =
        "${System.getProperty("os.version")} · JVM ${System.getProperty("java.version")}"

    override fun log(
        level: LogLevel,
        tag: String,
        message: String,
        throwable: Throwable?,
    ) {
        // ADR-029 routes all logging through this interface precisely so the sink is replaceable.
        // Phase 0 writes to the console; a file sink lands when there is a shop to support.
        val stream = if (level == LogLevel.ERROR) System.err else System.out
        stream.println("${LocalTime.now().format(TIME)} ${level.name.first()}/$tag: $message")
        throwable?.printStackTrace(stream)
    }

    private companion object {
        val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
    }
}
