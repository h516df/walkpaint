package cn.walkpaint

import org.json.JSONObject

internal class UpdateDeviceGrant(val userCode:String,val deviceCode:String,val expiresAt:Long,val interval:Int) {
    companion object {
        fun decode(j:JSONObject,elapsedNow:Long):UpdateDeviceGrant {
            if(j.has("error")) throw UpdateFailure(PrivateUpdatePolicy.oauthError(j.getString("error")))
            updateRequire(j.getString("verification_uri")==PrivateUpdatePolicy.VERIFY)
            val code=j.getString("user_code");val device=j.getString("device_code")
            updateRequire(code.matches(Regex("[A-Z0-9]{4}-[A-Z0-9]{4}")) && device.matches(Regex("[a-zA-Z0-9]{40}")))
            val expires=j.getInt("expires_in");val interval=j.getInt("interval")
            updateRequire(expires in 30..900 && interval in 5..60)
            return UpdateDeviceGrant(code,device,elapsedNow+expires*1000L,interval)
        }
    }
}
internal sealed class UpdatePoll {
    class Pending(val nextInterval:Int):UpdatePoll()
    class Approved(val authorization:UpdateAuthorization):UpdatePoll()
    companion object {
        fun decode(j:JSONObject,previousInterval:Int,wallNow:Long):UpdatePoll = when(j.optString("error")) {
            "authorization_pending" -> Pending(previousInterval)
            "slow_down" -> Pending(maxOf(previousInterval+5,j.optInt("interval",previousInterval+5)).coerceAtMost(900))
            "" -> Approved(UpdateAuthorization.response(j,wallNow))
            else -> throw UpdateFailure(PrivateUpdatePolicy.oauthError(j.getString("error")))
        }
    }
}
