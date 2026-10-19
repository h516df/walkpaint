package cn.walkpaint

import android.app.Activity
import android.app.Application
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import java.lang.reflect.Field
import java.util.IdentityHashMap
import java.util.concurrent.atomic.AtomicReference

/** Observe the real SAF result without substituting the picker or changing production code. */
internal object DocumentResultCapture {
    private fun fields(type:Class<*>):List<Field> = generateSequence(type as Class<*>?) {it.superclass}
        .takeWhile {it!=Any::class.java}.flatMap {it.declaredFields.asSequence()}.onEach {it.isAccessible=true}.toList()

    fun install(activity:ComponentActivity,result:AtomicReference<Uri?>):AutoCloseable {
        val app=activity.application
        val installed=IdentityHashMap<Any,Boolean>()
        val restores=mutableListOf<()->Unit>()
        fun attach(target:ComponentActivity) {
            val registry=target.activityResultRegistry
            if(installed.put(registry,true)!=null)return
            val all=fields(registry.javaClass)
            val callbackMap=all.first {it.name=="keyToCallback" || it.name=="mKeyToCallback"}
            val pending=all.firstOrNull {it.name=="pendingResults" || it.name=="mPendingResults"}
            @Suppress("UNCHECKED_CAST") val original=callbackMap.get(registry) as MutableMap<String,Any>
            val patched=IdentityHashMap<Any,Boolean>()
            fun patch(key:String,group:Any) {
                if(patched.put(group,true)!=null)return
                val fs=fields(group.javaClass)
                val contract=fs.firstOrNull {ActivityResultContract::class.java.isAssignableFrom(it.type)}?.get(group)
                if(contract !is ActivityResultContracts.CreateDocument)return
                if(fields(contract.javaClass).none {it.type==String::class.java && it.get(contract)=="image/png"})return
                // A recreated Activity can receive its result before Compose registers the callback.
                @Suppress("DEPRECATION") val queued=(pending?.get(registry) as? Bundle)?.getParcelable<ActivityResult>(key)
                if(queued?.resultCode==Activity.RESULT_OK)queued.data?.data?.let {result.set(it)}
                val callback=fs.first {ActivityResultCallback::class.java.isAssignableFrom(it.type)}
                @Suppress("UNCHECKED_CAST") val previous=callback.get(group) as ActivityResultCallback<Uri?>
                callback.set(group,ActivityResultCallback<Uri?> {uri->
                    if(uri!=null)result.set(uri)
                    previous.onActivityResult(uri)
                })
                restores+={callback.set(group,previous)}
            }
            original.toList().forEach {(key,value)->patch(key,value)}
            val observing=object:MutableMap<String,Any> by original {
                override fun put(key:String,value:Any):Any? {patch(key,value);return original.put(key,value)}
            }
            callbackMap.set(registry,observing)
            restores+={callbackMap.set(registry,original)}
        }
        val observer=object:Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(a:Activity,state:Bundle?) {if(a is MainActivity)attach(a)}
            override fun onActivityStarted(a:Activity) {}
            override fun onActivityResumed(a:Activity) {}
            override fun onActivityPaused(a:Activity) {}
            override fun onActivityStopped(a:Activity) {}
            override fun onActivitySaveInstanceState(a:Activity,b:Bundle) {}
            override fun onActivityDestroyed(a:Activity) {}
        }
        app.registerActivityLifecycleCallbacks(observer)
        attach(activity)
        return AutoCloseable {
            app.unregisterActivityLifecycleCallbacks(observer)
            restores.asReversed().forEach {it()}
        }
    }
}
