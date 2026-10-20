package cn.walkpaint

import java.io.File
import java.nio.file.Files
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ExplorationLandmarksTest {
    private val point=ExplorationLandmark("fixture-park","示例公园",0.0,0.0)
    private fun sample(a:LandmarkArrival,t:Long,lat:Double=0.0,accuracy:Float=5f,age:Long=0,
                       gps:Boolean=true,mock:Boolean=false,enabled:Boolean=true)=
        a.sample(listOf(point),lat,0.0,accuracy,t,age,gps,mock,enabled)
    @Test fun arrivalNeedsTwoSeparateFreshSamplesTenSecondsApart() {
        val a=LandmarkArrival()
        assertTrue(sample(a,1000).isEmpty());assertTrue(sample(a,1000).isEmpty())
        assertTrue(sample(a,10999).isEmpty());assertEquals(listOf(point.id),sample(a,11000))
    }
    @Test fun rejectsMockNetworkStaleInaccurateAndDisabledFixes() {
        val cases=listOf<(LandmarkArrival)->List<String>>(
            {sample(it,11000,mock=true)},{sample(it,11000,gps=false)},
            {sample(it,11000,age=15001)},{sample(it,11000,age=-1)},
            {sample(it,11000,accuracy=26f)},{sample(it,11000,accuracy=Float.NaN)},
            {sample(it,11000,enabled=false)},{sample(it,-1)})
        cases.forEach {bad ->val a=LandmarkArrival();sample(a,1000);assertTrue(bad(a).isEmpty());assertTrue(sample(a,12000).isEmpty())}
    }
    @Test fun accuracyCircleMustFitWithinFiftyMetresAndOutsideResets() {
        val a=LandmarkArrival();sample(a,1000)
        assertTrue(sample(a,11000,lat=40.0/111195.0,accuracy=15f).isEmpty())
        assertTrue(sample(a,12000).isEmpty());assertEquals(listOf(point.id),sample(a,22000))
    }
    @Test fun longGapOrClockReversalRestartsDwell() {
        val a=LandmarkArrival();sample(a,1000);assertTrue(sample(a,62000).isEmpty())
        assertTrue(sample(a,500).isEmpty());assertEquals(listOf(point.id),sample(a,10500))
    }
    @Test fun visitedDeletedAndEmptyListsNeverCreateArrival() {
        val a=LandmarkArrival()
        for(items in listOf(emptyList(),listOf(point.copy(visitedAt=1)),listOf(point.copy(deleted=true)))) {
            assertTrue(a.sample(items,0.0,0.0,5f,1000,0,true,false,true).isEmpty())
            assertTrue(a.sample(items,0.0,0.0,5f,11000,0,true,false,true).isEmpty())
        }
    }
    @Test fun metadataRoundTripAndDuplicateRejection() {
        val original=mapOf(point.id to point.copy(visitedAt=123))
        assertEquals(original,LandmarkCodec.decode(LandmarkCodec.encode(original)))
        val encoded=LandmarkCodec.encode(original);val array=encoded.getJSONArray("items")
        array.put(array.getJSONObject(0));assertThrows(IllegalArgumentException::class.java){LandmarkCodec.decode(encoded)}
    }
    @Test fun unknownCoordinateSystemIsRejectedInsteadOfConvertedTwice() {
        val encoded=LandmarkCodec.encode(mapOf(point.id to point))
        assertEquals("WGS84",encoded.getString("coordinateSystem"))
        encoded.put("coordinateSystem","GCJ02")
        assertThrows(IllegalArgumentException::class.java){LandmarkCodec.decode(encoded)}
    }
    @Test fun invalidCoordinatesNamesAndOversizeAreRejected() {
        for(p in listOf(point.copy(lat=Double.NaN),point.copy(lon=181.0),point.copy(name=""),point.copy(id="x".repeat(201)),point.copy(visitedAt=0))) {
            assertThrows(IllegalArgumentException::class.java){LandmarkCodec.validate(p)}
        }
        assertThrows(IllegalArgumentException::class.java){LandmarkCodec.encode((0..500).associate {"$it" to point.copy(id="$it")})}
    }
    @Test fun atomicStoreRetainsVisitAndLocalTombstoneAgainstOldBackup() {
        val dir=Files.createTempDirectory("landmark-test").toFile()
        try {
            val file=File(dir,"points.json");val store=LandmarkStore(file)
            store.save(point);store.visit(point.id,123);store.save(point.copy(name="新名字"))
            assertEquals(123L,store.snapshot()[point.id]?.visitedAt)
            store.remove(point.id);store.mergeMissing(mapOf(point.id to point))
            val deleted=LandmarkStore(file).snapshot().getValue(point.id)
            assertTrue(deleted.deleted);assertEquals("",deleted.name);assertEquals(0.0,deleted.lat,0.0)
            assertFalse(File(dir,"points.json.tmp").exists())
        } finally {dir.deleteRecursively()}
    }
    @Test fun corruptedOriginalIsNeverReplacedBySave() {
        val dir=Files.createTempDirectory("landmark-test").toFile()
        try {val file=File(dir,"points.json");file.writeText("broken")
            assertThrows(Exception::class.java){LandmarkStore(file).save(point)}
            assertEquals("broken",file.readText())
        } finally {dir.deleteRecursively()}
    }
    @Test fun featherHasNoBrighterHaloThanExploredTint() {
        VisualSkin.entries.forEach {skin ->
            val color=FogAppearance.color(skin)
            val alphas=(0..36).map {FogAppearance.featherAlpha(color,it/36f,84)}
            assertEquals(84,alphas.first());assertEquals(color ushr 24,alphas.last())
            assertTrue(alphas.zipWithNext().all {(a,b)->b>=a})
        }
    }
    @Test fun themesConcealUnvisitedBasemapAndDifferWithoutGeometryChanges() {
        assertEquals(3,VisualSkin.entries.map {FogAppearance.color(it)}.toSet().size)
        VisualSkin.entries.forEach {
            val color=FogAppearance.color(it)
            assertEquals("$it must conceal roads and labels",255,color ushr 24)
            assertTrue("$it must not revert to black",(color and 0x00FFFFFF)!=0)
        }
        assertEquals(50.0,TrailPaint.REVEAL_RADIUS_M,0.0)
    }
}
