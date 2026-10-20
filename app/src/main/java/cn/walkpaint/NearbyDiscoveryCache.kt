package cn.walkpaint

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class NearbyRefreshDeferred:IllegalStateException("位置已变化，请稍候再查附近探索点（最多一分钟）。")

/** One ephemeral cache for list and map. No database writes and no camera-based queries. */
internal class NearbyDiscoveryCache(private val clock:()->Long) {
    private val mutex=Mutex()
    private var cachedCenter:RegionPoint?=null
    private var cached:List<NearbyCandidate> = emptyList()
    private var cachedError:Exception?=null
    private var attemptedAt:Long?=null
    suspend fun get(center:RegionPoint,load:()->List<NearbyCandidate>):List<NearbyCandidate> = mutex.withLock {
        currentCoroutineContext().ensureActive()
        val age=attemptedAt?.let {clock()-it}
        val same=cachedCenter?.let {NearbyRefreshPolicy.near(it,center)}==true
        if(age!=null && ((same && cachedError==null && age<NearbyRefreshPolicy.CACHE_MS) || age<NearbyRefreshPolicy.INTERVAL_MS)) {
            if(!same) throw NearbyRefreshDeferred()
            cachedError?.let {throw it}
            return@withLock NearbyCandidates.select(center,cached.map {it.point to it.category})
        }
        attemptedAt=clock();cachedCenter=center;cached=emptyList();cachedError=null
        // The synchronous SDK has no cancellation method. Keep the mutex until it returns,
        // even if the consumer leaves; withContext cancels delivery to the departed UI.
        withContext(Dispatchers.IO) {
            try {cached=load()}
            catch(e:Exception) {cachedError=e;throw e}
        }
        cached
    }
}
