package cn.walkpaint
import org.junit.Assert.*
import org.junit.Test
class VisualSkinTest {
    @Test fun preferenceIdsAreStableAndUnknownValuesFallBack() {
        assertEquals(VisualSkin.CITY,VisualSkin.stored(null))
        assertEquals(VisualSkin.CITY,VisualSkin.stored("future"))
        VisualSkin.entries.forEach {assertEquals(it,VisualSkin.stored(it.id))}
        assertEquals(3,VisualSkin.entries.map {it.paint}.distinct().size)
    }
}
