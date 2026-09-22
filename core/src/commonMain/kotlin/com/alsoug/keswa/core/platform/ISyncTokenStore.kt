package com.alsoug.keswa.core.platform

/**
 * Where this device keeps the token that proves it is enrolled.
 *
 * An interface bound per platform rather than `expect`/`actual`, per KD-005 and ADR-018, and
 * capability-shaped: store, read, clear. No file handle, no `KeyStore`, no `Context` crosses it.
 *
 * **KD-007 decides what sits behind it**, and the short version is that the answer differs by
 * platform because the threat models do. On Android the Keystore is there and costs nothing. On
 * desktop the token is a file with restrictive permissions, because `keswa.db` sits beside it
 * holding the shop's entire trading history in plaintext — encrypting one string next to that
 * protects the least valuable thing in the directory while looking like the problem is solved.
 *
 * What does the real work on both is that the token is device-scoped and revocable from the
 * server in seconds.
 */
interface ISyncTokenStore {
    suspend fun store(token: String)
    suspend fun read(): String?
    suspend fun clear()
}
