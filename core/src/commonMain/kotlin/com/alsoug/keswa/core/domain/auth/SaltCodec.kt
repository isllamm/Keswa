package com.alsoug.keswa.core.domain.auth

/**
 * How a per-user salt is written into `app_user.secretSalt`.
 *
 * Lower-case hex rather than Base64: it is fixed-width, has no padding or alphabet variants to get
 * wrong, and survives being looked at in a database browser during support.
 *
 * In `:core` because it is the storage format of a `:core` entity's column — every caller that
 * verifies a credential has to agree with it, and there is now more than one.
 */
private const val HEX = "0123456789abcdef"

fun String.decodeSalt(): ByteArray =
    chunked(2).map { it.toInt(radix = 16).toByte() }.toByteArray()

fun ByteArray.encodeSalt(): String =
    joinToString("") { byte ->
        val value = byte.toInt() and 0xFF
        "${HEX[value shr 4]}${HEX[value and 0xF]}"
    }
