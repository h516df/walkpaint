package cn.walkpaint
import org.junit.Assert.*
import org.junit.Test
class VisualSkinTest {
    @Test fun appearanceModesKeepFogOpaqueAndDistinct() {
        for(skin in VisualSkin.entries) for(dark in listOf(false,true)) assertEquals(255,FogAppearance.color(skin,dark) ushr 24)
        assertNotEquals(FogAppearance.color(VisualSkin.CITY,false),FogAppearance.color(VisualSkin.CITY,true))
        assertNotEquals(FogAppearance.color(VisualSkin.DUSK,false),FogAppearance.color(VisualSkin.DUSK,true))
    }
    @Test fun preferenceIdsAreStableAndUnknownValuesFallBack() {
        assertEquals(VisualSkin.CITY,VisualSkin.stored(null))
        assertEquals(VisualSkin.CITY,VisualSkin.stored("future"))
        VisualSkin.entries.forEach {assertEquals(it,VisualSkin.stored(it.id))}
        assertEquals(3,VisualSkin.entries.map {it.paint}.distinct().size)
    }
}
