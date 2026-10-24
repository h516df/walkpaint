package cn.walkpaint

import java.io.StringWriter
import java.time.Instant
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test

class TrackGpxTest {
    private val time=Instant.parse("2026-09-01T12:00:00Z").toEpochMilli()
    private fun point(offset:Long=0,lat:Double=0.00001,segment:Int=0)=TrackPoint(lat,0.00002,time+offset,5f,segment)
    private fun write(tracks:List<Track>,name:String="合成路线<&>")=StringWriter().also {TrackGpx.write(tracks,name,it)}.toString()
    private fun parse(xml:String)=DocumentBuilderFactory.newInstance().apply {isNamespaceAware=true}
        .newDocumentBuilder().parse(xml.byteInputStream())
    @Test fun standardXmlRetainsRawCoordinatesAndUtcWithoutLocaleOrExponent() {
        val locale=Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            val track=Track("synthetic",time,false,listOf(point(),point(10000,0.00011)))
            val xml=write(listOf(track));val doc=parse(xml)
            assertEquals("http://www.topografix.com/GPX/1/1",doc.documentElement.namespaceURI)
            assertEquals("1.1",doc.documentElement.getAttribute("version"))
            assertEquals("合成路线<&>",doc.getElementsByTagName("name").item(0).textContent)
            val points=doc.getElementsByTagName("trkpt")
            assertEquals(2,points.length)
            assertEquals("0.00001",points.item(0).attributes.getNamedItem("lat").nodeValue)
            assertEquals("0.00002",points.item(0).attributes.getNamedItem("lon").nodeValue)
            assertEquals("2026-09-01T12:00:00Z",doc.getElementsByTagName("time").item(0).textContent)
            assertEquals(listOf(point(),point(10000,0.00011)),track.points)
        } finally {Locale.setDefault(locale)}
    }
    @Test fun gapsSegmentChangesAndSeparateTripsNeverBecomeConnected() {
        val a=Track("first",time,false,listOf(point(),point(10000,0.00011),point(80000,0.0002),point(90000,0.0003,1)))
        val b=Track("second",time,false,listOf(point()))
        val doc=parse(write(listOf(a,b,a.copy(id="demo",demo=true))))
        assertEquals(2,doc.getElementsByTagName("trk").length)
        assertEquals(4,doc.getElementsByTagName("trkseg").length)
        assertEquals(5,doc.getElementsByTagName("trkpt").length)
    }
    @Test fun rejectsInvalidOrDemoOnlyDataBeforeWritingAnything() {
        for(tracks in listOf(emptyList(),listOf(Track("demo",time,true,listOf(point()))),
            listOf(Track("bad",time,false,listOf(point(),point(lat=Double.NaN)))))) {
            val writer=StringWriter()
            try {TrackGpx.write(tracks,"test",writer);fail("Invalid data should fail")}
            catch(_:IllegalArgumentException) {assertEquals("",writer.toString())}
        }
    }
    @Test fun outputFailurePropagatesAndLeavesSourceUntouched() {
        val tracks=listOf(Track("synthetic",time,false,listOf(point())))
        val failing=object:java.io.Writer() {
            override fun write(c:CharArray,o:Int,n:Int){throw java.io.IOException("synthetic full destination")}
            override fun flush(){};override fun close(){}
        }
        try {TrackGpx.write(tracks,"test",failing);fail("Write must fail")}
        catch(_:java.io.IOException) {assertEquals(1,tracks.single().points.size)}
    }
}
