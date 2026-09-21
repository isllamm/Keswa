package com.alsoug.keswa

import android.os.Build
import android.util.Log
import com.alsoug.keswa.core.platform.IPlatformProvider
import com.alsoug.keswa.core.platform.LogLevel

/**
 * Android [IPlatformProvider], the sibling of `DesktopPlatformProvider` (KD-004 naming).
 *
 * ADR-029 routes every log through this interface precisely so the sink is replaceable — which is
 * why `Log.d` is banned everywhere except here, where it *is* the sink.
 */
class AndroidPlatformProvider : IPlatformProvider {

    override val platformName: String = "Android"

    override val platformVersion: String = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"

    override fun log(level: LogLevel, tag: String, message: String, throwable: Throwable?) {
        when (level) {
            LogLevel.DEBUG -> Log.d(tag, message, throwable)
            LogLevel.INFO -> Log.i(tag, message, throwable)
            LogLevel.WARNING -> Log.w(tag, message, throwable)
            LogLevel.ERROR -> Log.e(tag, message, throwable)
        }
    }
}
