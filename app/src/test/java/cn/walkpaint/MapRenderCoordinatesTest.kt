package cn.walkpaint
import kotlin.math.atan
import kotlin.math.sinh
import org.junit.Assert.*
import org.junit.Test

class MapRenderCoordinatesTest {
    @Test fun wideViewUsesMapProjectionInsteadOfLinearLatitude() {
        val south=20.0;val north=60.0
        val middle=Math.toDegrees(atan(sinh((MapRenderCoordinates.northing(south)+MapRenderCoordinates.northing(north))/2)))
        assertEquals(256f,MapRenderCoordinates.y(middle,south,north,512),.001f)
        assertTrue(kotlin.math.abs(middle-(north+south)/2)>2)
    }
    @Test fun edgesAndPolarInputsRemainFinite() {
        assertEquals(0f,MapRenderCoordinates.y(60.0,20.0,60.0,512),.001f)
        assertEquals(512f,MapRenderCoordinates.y(20.0,20.0,60.0,512),.001f)
        assertTrue(MapRenderCoordinates.northing(90.0).isFinite())
        assertTrue(MapRenderCoordinates.northing(-90.0).isFinite())
    }
}
