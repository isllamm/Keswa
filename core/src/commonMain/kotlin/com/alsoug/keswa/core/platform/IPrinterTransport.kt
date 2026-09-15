package com.alsoug.keswa.core.platform

/**
 * A byte pipe to a printer.
 *
 * ADR-018's "no raw context exposure" applies: nothing vendor- or platform-specific crosses this
 * boundary — no socket, no `javax.print` handle, no USB descriptor. Just bytes, so the protocol
 * layer above stays pure and testable.
 */
interface IPrinterTransport {

    suspend fun open(): Result<Unit>

    suspend fun write(bytes: ByteArray): Result<Unit>

    suspend fun close()

    /** Opens, writes, and closes — the normal case for one document. */
    suspend fun send(bytes: ByteArray): Result<Unit> {
        open().onFailure { return Result.failure(it) }
        return try {
            write(bytes)
        } finally {
            close()
        }
    }
}
