package com.alsoug.keswa.server

import com.alsoug.keswa.core.platform.IPlatformProvider
import com.alsoug.keswa.core.platform.LogLevel

/**
 * The server's end of ADR-029.
 *
 * A headless process has no `DesktopPlatformProvider` and no window to put a message in, but the
 * rule is the same one: everything goes through [IPlatformProvider.log], so there is one place that
 * decides what a log line looks like and one place a reviewer has to check that a secret never
 * reaches it. `check-gates.sh` exempts this file by name, exactly as it exempts the other two.
 */
class ServerPlatformProvider : IPlatformProvider {

    override val platformName: String = "server"
    override val platformVersion: String = System.getProperty("java.version").orEmpty()

    override fun log(level: LogLevel, tag: String, message: String, throwable: Throwable?) {
        println("$level [$tag] $message")
        throwable?.printStackTrace()
    }
}
