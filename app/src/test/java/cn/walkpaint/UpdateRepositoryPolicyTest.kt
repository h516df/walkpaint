package cn.walkpaint

import org.json.JSONObject
import org.junit.Assert.assertThrows
import org.junit.Test

class UpdateRepositoryPolicyTest {
    private fun upstream()=JSONObject().put("id",PrivateUpdatePolicy.REPO_ID)
        .put("full_name",PrivateUpdatePolicy.REPO).put("private",false)

    @Test fun publicUpstreamIsAcceptedWithoutAnAccount() {
        UpdateRepositoryPolicy.validate(upstream())
    }

    @Test fun unexpectedlyPrivateUpstreamIsNotTreatedAsPublic() {
        assertThrows(UpdateFailure::class.java) {
            UpdateRepositoryPolicy.validate(upstream().put("private",true))
        }
    }

    @Test fun sameNameWithADifferentIdentityIsRejected() {
        assertThrows(UpdateFailure::class.java) {
            UpdateRepositoryPolicy.validate(upstream().put("id",PrivateUpdatePolicy.REPO_ID+1))
        }
    }

    @Test fun aDifferentRepositoryCannotSupplyUpdates() {
        assertThrows(UpdateFailure::class.java) {
            UpdateRepositoryPolicy.validate(upstream().put("full_name","someone/other-app"))
        }
    }
}
