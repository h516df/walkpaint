package cn.walkpaint

import org.junit.Assert.*
import org.junit.Test

class RoadPaintColorTest {
    @Test fun defaultsAndPresetsStayDistinctAndOpaque() {
        assertEquals(0xFF579B8C.toInt(),RoadPaintColor.stored(null))
        // Five muted presets plus four high-contrast ones, added because the muted set alone was
        // hard to read against an online basemap.
        assertEquals(9,RoadPaintColor.presets.map { it.argb }.distinct().size)
        for(preset in RoadPaintColor.presets) assertEquals(255,preset.argb ushr 24)
    }
    @Test fun customInputRoundTripsWithOpaqueCanonicalHex() {
        assertEquals(0xFFB56F77.toInt(),RoadPaintColor.parse(" #b56f77 "))
        for(hex in listOf("000000","FFFFFF","647D9E","0123ab","B56F77")) {
            val color=RoadPaintColor.parse(hex)!!
            assertEquals(255,color ushr 24)
            assertEquals(color,RoadPaintColor.parse(RoadPaintColor.hex(color)))
        }
    }
    @Test fun corruptAndInvalidInputCannotChangeTheStoredDefault() {
        for(text in listOf("","#123","579B8C00","#FF579B8C","transparent","#ZZ1234","##579B8C")) {
            assertNull(RoadPaintColor.parse(text));assertEquals(RoadPaintColor.DEFAULT,RoadPaintColor.stored(text))
        }
    }
}
