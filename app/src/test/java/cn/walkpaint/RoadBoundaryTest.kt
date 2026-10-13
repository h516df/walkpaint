package cn.walkpaint
import org.junit.Assert.*
import org.junit.Test
class RoadBoundaryTest {
    @Test fun missingNearbyCatalogDoesNotInventMatchOrCoverage() {
        val pack=RoadPackCodec.decode(javaClass.getResource("/oxford-roads.geojson")!!.readText())
        val catalog=RoadCatalog(pack.roads)
        assertTrue(catalog.near(pack.roads.first().points.first()).isNotEmpty())
        val outside=TrackPoint(52.0,-1.5,1000,5f)
        assertTrue(catalog.near(outside).isEmpty())
        assertNull(catalog.traversed(outside,outside.copy(lat=52.0001,time=11000)))
        assertTrue(RoadCoverage().snapshot().isEmpty())
    }
}
