package com.alsoug.keswa.core.platform

import com.alsoug.keswa.core.coroutines.DispatcherProvider
import com.alsoug.keswa.core.database.appDataDirectory
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import kotlinx.coroutines.withContext

/**
 * The device token, in a file only this user account can read.
 *
 * KD-007. POSIX permissions where the filesystem has them; on Windows the file lives under the
 * user's own profile, which is the same protection by a different mechanism. If the threat model
 * ever demands more, the answer is encrypting the whole database rather than this one string —
 * the ADR says why.
 */
class DesktopSyncTokenStore(
    private val dispatchers: DispatcherProvider,
    private val file: File = appDataDirectory().resolve(TOKEN_FILE),
) : ISyncTokenStore {

    override suspend fun store(token: String) = withContext(dispatchers.io) {
        file.parentFile?.mkdirs()
        file.writeText(token)
        restrictToOwner()
    }

    override suspend fun read(): String? = withContext(dispatchers.io) {
        if (file.exists()) file.readText().trim().ifEmpty { null } else null
    }

    override suspend fun clear() = withContext(dispatchers.io) {
        file.delete()
        Unit
    }

    /**
     * Best effort by design: POSIX permissions are unsupported on Windows, and refusing to enrol a
     * till because its filesystem has a different permission model would be the wrong trade. The
     * protection there is the user profile the file sits in.
     */
    private fun restrictToOwner() {
        runCatching {
            Files.setPosixFilePermissions(
                file.toPath(),
                setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
            )
        }
    }

    companion object {
        const val TOKEN_FILE: String = "sync-token"
    }
}
