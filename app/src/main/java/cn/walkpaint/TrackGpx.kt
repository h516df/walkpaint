package cn.walkpaint

import java.io.Writer
import java.time.Instant

/** GPX 1.1 in original WGS84. No map conversions, interpolation, or display simplification. */
internal object TrackGpx {
    fun write(tracks:List<Track>,name:String,output:Writer) {
        val real=tracks.filter {!it.demo && it.points.isNotEmpty()}
        require(real.isNotEmpty()) {"没有可导出的真实足迹"}
        real.forEach {track->track.points.forEach {point->
            require(point.lat.isFinite() && point.lon.isFinite() && point.lat in -90.0..90.0 && point.lon in -180.0..180.0 && point.time>=0) {"足迹包含无效坐标或时间"}
        }}
        output.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        output.write("<gpx version=\"1.1\" creator=\"WalkPaint\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n")
        real.forEach {track->
            output.write("<trk><name>${xml(name)}</name>\n")
            var previous:TrackPoint?=null
            track.points.forEach {point->
                val last=previous
                if(last==null || !RecordedTrail.connected(last,point)) {
                    if(last!=null) output.write("</trkseg>\n")
                    output.write("<trkseg>\n")
                }
                output.write("<trkpt lat=\"${decimal(point.lat)}\" lon=\"${decimal(point.lon)}\"><time>${Instant.ofEpochMilli(point.time)}</time></trkpt>\n")
                previous=point
            }
            output.write("</trkseg></trk>\n")
        }
        output.write("</gpx>\n")
    }
    private fun decimal(value:Double)=java.math.BigDecimal.valueOf(value).toPlainString()
    private fun xml(value:String)=value.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;")
        .filter {it=='\n' || it=='\r' || it=='\t' || it>=' '}
}
