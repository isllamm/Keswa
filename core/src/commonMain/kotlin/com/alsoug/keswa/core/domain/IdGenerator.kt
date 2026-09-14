package com.alsoug.keswa.core.domain

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Supplies the client-generated identifiers every row carries.
 *
 * An interface rather than a call to [Uuid] at each site so tests are deterministic, and because
 * these ids are the basis of idempotent sync in Phase 9 — `cashi_pax` defect F2 was a
 * non-idempotent id letting a double submit create two records.
 */
interface IdGenerator {
    fun newId(): String
}

@OptIn(ExperimentalUuidApi::class)
class UuidIdGenerator : IdGenerator {
    override fun newId(): String = Uuid.random().toString()
}
