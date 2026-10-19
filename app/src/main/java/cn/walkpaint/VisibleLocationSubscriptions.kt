package cn.walkpaint

/** Foreground display subscriptions only; never starts recording or treats subscription as a fix. */
internal class VisibleLocationSubscriptions {
    data class Request(val provider:String,val intervalMillis:Long,val distanceMeters:Float)
    private val requests=listOf(Request("gps",2_000L,0f),Request("network",20_000L,8f))
    private val registered=mutableSetOf<String>()
    private var attempted=false

    /** available includes disabled providers: Android resumes their registered listeners on enable. */
    fun refresh(active:Boolean,finePermission:Boolean,available:Set<String>,
                request:(Request)->Unit,remove:()->Unit) {
        if(!active || !finePermission) { stop(remove);return }
        if(registered.any {it !in available}) stop(remove)
        for(spec in requests) {
            if(spec.provider !in available || spec.provider in registered) continue
            // A failed GPS request must not prevent the independent network display subscription.
            attempted=true
            try { request(spec);registered+=spec.provider } catch(_:Exception) { }
        }
    }

    fun stop(remove:()->Unit) {
        if(attempted) {
            try {remove()} catch(_:Exception) { }
            finally {registered.clear();attempted=false}
        }
    }
}
