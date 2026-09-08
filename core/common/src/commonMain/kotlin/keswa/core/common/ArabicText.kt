package keswa.core.common

/**
 * A sort key for Arabic names — SQLite's default collation is byte-order, which puts Arabic text
 * in a meaningless sequence. Strips tashkeel/tatweel and unifies alef/yaa forms so names sort the
 * way a reader expects. See docs/architecture.md §6. Not a display transform — never shown as-is.
 */
object ArabicText {
    private val TASHKEEL_AND_TATWEEL = Regex("[ؐ-ًؚ-ٰٟۖ-ۜ۟-۪ۨ-ۭـ]")

    fun sortKey(text: String): String {
        val stripped = text.replace(TASHKEEL_AND_TATWEEL, "")
        val builder = StringBuilder(stripped.length)
        for (ch in stripped) {
            builder.append(
                when (ch) {
                    'أ', 'إ', 'آ' -> 'ا'
                    'ى' -> 'ي'
                    'ة' -> 'ه'
                    else -> ch
                },
            )
        }
        return builder.toString().trim().lowercase()
    }
}
