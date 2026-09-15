package com.alsoug.keswa.core.printing

/** Golden-byte assertions are unreadable in decimal; compare hex. */
fun ByteArray.hex(): String = joinToString(" ") {
    val v = it.toInt() and 0xFF
    "0123456789ABCDEF"[v shr 4].toString() + "0123456789ABCDEF"[v and 0xF]
}
