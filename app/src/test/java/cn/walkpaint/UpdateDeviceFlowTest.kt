package cn.walkpaint
import org.json.JSONObject
import org.junit.Test
import org.junit.Assert.*

class UpdateDeviceFlowTest {
    private fun response()=JSONObject().put("verification_uri",PrivateUpdatePolicy.VERIFY).put("user_code","ABCD-EFGH")
        .put("device_code","a".repeat(40)).put("expires_in",900).put("interval",5)
    @Test fun onlyOfficialGitHubDevicePageCanReceiveACode() {
        assertEquals(900100,UpdateDeviceGrant.decode(response(),100).expiresAt)
        assertThrows(UpdateFailure::class.java) { UpdateDeviceGrant.decode(response().put("verification_uri","https://evil.example"),0) }
    }
    @Test fun malformedCodesOrUnboundedSessionsAreRejected() {
        listOf(response().put("user_code","paste-a-token"),response().put("device_code","short"),response().put("expires_in",86400),
            response().put("interval",0)).forEach { assertThrows(UpdateFailure::class.java) { UpdateDeviceGrant.decode(it,0) } }
    }
    @Test fun pendingNeverProducesAnAuthorizationAndSlowDownOnlySlowsPolling() {
        assertEquals(5,(UpdatePoll.decode(JSONObject().put("error","authorization_pending"),5,0) as UpdatePoll.Pending).nextInterval)
        assertEquals(10,(UpdatePoll.decode(JSONObject().put("error","slow_down").put("interval",1),5,0) as UpdatePoll.Pending).nextInterval)
        assertEquals(20,(UpdatePoll.decode(JSONObject().put("error","slow_down").put("interval",20),5,0) as UpdatePoll.Pending).nextInterval)
    }
    @Test fun denialAndExpiryStopInsteadOfLoopingOrPretendingSuccess() {
        listOf("access_denied","expired_token","device_flow_disabled").forEach {
            assertThrows(UpdateFailure::class.java) { UpdatePoll.decode(JSONObject().put("error",it),5,0) }
        }
    }
    @Test fun deviceGrantDoesNotPrintItsPrivateCode() {
        assertFalse(UpdateDeviceGrant.decode(response(),0).toString().contains("a".repeat(40)))
    }
}
