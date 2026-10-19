package cn.walkpaint

import kotlin.math.*

internal data class RegionPoint(val lat:Double,val lon:Double)
internal data class RegionBoundary(val name:String,val code:String,val rings:List<List<RegionPoint>>)
internal data class RegionShare(val squareMeters:Double,val totalSquareMeters:Double) {
    val percent get()=if(totalSquareMeters>0)(squareMeters/totalSquareMeters*100).coerceIn(0.0,100.0) else 0.0
}

/** Equal-area local grid, each cell counted once; only connected saved trails are interpolated. */
internal object RegionCoverage {
    private const val CELL=25.0
    fun contains(ring:List<RegionPoint>,point:RegionPoint):Boolean {
        var inside=false;var j=ring.lastIndex
        for(i in ring.indices){val a=ring[i];val b=ring[j]
            if((a.lat>point.lat)!=(b.lat>point.lat) && point.lon<(b.lon-a.lon)*(point.lat-a.lat)/(b.lat-a.lat)+a.lon)inside=!inside
            j=i
        };return inside
    }
    fun measure(paths:List<List<RegionPoint>>,center:RegionPoint,boundary:RegionBoundary?=null,radius:Double=2000.0,checkActive:()->Unit={}):RegionShare {
        require(center.lat.isFinite() && center.lon.isFinite() && center.lat in -85.0..85.0 && center.lon in -180.0..180.0)
        val scale=111194.9;val longitudeScale=scale*cos(Math.toRadians(center.lat))
        fun xy(p:RegionPoint)=((p.lon-center.lon)*longitudeScale) to ((p.lat-center.lat)*scale)
        val rings=boundary?.rings.orEmpty().filter{it.size>=3}
        val projected=rings.map{it.map(::xy)}
        val rowIntervals=HashMap<Int,List<Pair<Double,Double>>>()
        val total=if(boundary==null)Math.PI*radius*radius else rings.sumOf{ring->
            val p=ring.map(::xy);abs(p.indices.sumOf{i->val a=p[i];val b=p[(i+1)%p.size];a.first*b.second-b.first*a.second})/2
        }
        require(total>0 && total.isFinite())
        val visited=HashSet<Long>()
        fun stamp(x:Double,y:Double){
            if(boundary==null && hypot(x,y)>radius+50)return
            for(row in floor((y-50)/CELL).toInt()..floor((y+50)/CELL).toInt())
                for(col in floor((x-50)/CELL).toInt()..floor((x+50)/CELL).toInt()) {
                    val cx=(col+.5)*CELL;val cy=(row+.5)*CELL
                    if(hypot(cx-x,cy-y)>50)continue
                    val key=(row.toLong() shl 32) xor (col.toLong() and 0xffffffffL)
                    if(key in visited)continue
                    val inside=if(boundary==null)hypot(cx,cy)<=radius else {
                        // Reuse scan-line intersections for an entire 25m row rather than
                        // checking thousands of polygon edges for every footprint cell.
                        val intervals=rowIntervals.getOrPut(row){projected.flatMap{ring->
                            val cuts=ring.indices.mapNotNull{i->val a=ring[i];val b=ring[(i+1)%ring.size]
                                if((a.second>cy)!=(b.second>cy))a.first+(b.first-a.first)*(cy-a.second)/(b.second-a.second) else null
                            }.sorted()
                            cuts.chunked(2).filter{it.size==2}.map{it[0] to it[1]}
                        }}
                        intervals.any{cx>=it.first && cx<=it.second}
                    }
                    if(inside)visited.add(key)
                }
            require(visited.size<=2_000_000){"Area calculation too large"}
        }
        paths.forEach{path->checkActive();var previous:Pair<Double,Double>?=null
            path.forEach{point->checkActive();val p=xy(point);require(p.first.isFinite() && p.second.isFinite())
                previous?.let{a->val distance=hypot(p.first-a.first,p.second-a.second);val steps=ceil(distance/25).toInt().coerceAtLeast(1)
                    require(steps<=100_000)
                    for(step in 1 until steps){if(step%100==0)checkActive();val fraction=step.toDouble()/steps;stamp(a.first+(p.first-a.first)*fraction,a.second+(p.second-a.second)*fraction)}
                };stamp(p.first,p.second);previous=p
            }
        }
        return RegionShare(minOf(visited.size*CELL*CELL,total),total)
    }
}

internal data class ExplorationStage(val title:String,val next:Int?)
internal fun explorationStage(percent:Double):ExplorationStage = when {
    percent>=100 -> ExplorationStage("满光",null)
    percent>=80 -> ExplorationStage("流光",100)
    percent>=50 -> ExplorationStage("映野",80)
    percent>=20 -> ExplorationStage("照途",50)
    percent>=5 -> ExplorationStage("寻光",20)
    else -> ExplorationStage("初光",5)
}
