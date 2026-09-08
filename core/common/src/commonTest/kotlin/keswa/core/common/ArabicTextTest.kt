package keswa.core.common

import kotlin.test.Test
import kotlin.test.assertEquals

class ArabicTextTest {

    @Test
    fun `alef forms unify to plain alef`() {
        assertEquals(ArabicText.sortKey("أحمد"), ArabicText.sortKey("احمد"))
        assertEquals(ArabicText.sortKey("إحمد"), ArabicText.sortKey("احمد"))
        assertEquals(ArabicText.sortKey("آحمد"), ArabicText.sortKey("احمد"))
    }

    @Test
    fun `tashkeel is stripped`() {
        assertEquals(ArabicText.sortKey("مُحَمَّد"), ArabicText.sortKey("محمد"))
    }

    @Test
    fun `taa marbuta unifies with haa`() {
        assertEquals(ArabicText.sortKey("قميصة"), ArabicText.sortKey("قميصه"))
    }

    @Test
    fun `distinct names still produce distinct keys`() {
        assertEquals(false, ArabicText.sortKey("قميص") == ArabicText.sortKey("بنطلون"))
    }
}
