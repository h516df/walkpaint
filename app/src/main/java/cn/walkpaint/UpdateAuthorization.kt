package cn.walkpaint

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Never a data class: credentials must not be exposed by generated toString(). */
internal class UpdateAuthorization(val access:String,val refresh:String,val expires:Long,val refreshExpires:Long) {
    fun encode()=JSONObject().put("access",access).put("refresh",refresh).put("expires",expires).put("refreshExpires",refreshExpires).toString()
    companion object {
        fun response(j:JSONObject,now:Long):UpdateAuthorization {
            val access=j.getString("access_token");val refresh=j.getString("refresh_token")
            val expiry=j.getLong("expires_in");val refreshExpiry=j.getLong("refresh_token_expires_in")
            updateRequire(access.matches(Regex("ghu_[A-Za-z0-9]{20,255}")) && refresh.matches(Regex("ghr_[A-Za-z0-9]{20,255}")))
            updateRequire(j.getString("token_type")=="bearer" && j.optString("scope").isEmpty())
            updateRequire(expiry in 60..28800 && refreshExpiry in expiry..15897600)
            return UpdateAuthorization(access,refresh,now+expiry*1000,now+refreshExpiry*1000)
        }
    }
}
internal class UpdateTokenStore(context:Context,namespace:String="private_update_authorization") {
    private val prefs=context.getSharedPreferences(namespace,Context.MODE_PRIVATE)
    private val alias=if(namespace=="private_update_authorization") "walkpaint-update-v1" else namespace
    private fun key():SecretKey {
        val store=KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias,null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun save(token:UpdateAuthorization) {
        val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key())
        cipher.updateAAD(BuildConfig.UPDATE_CLIENT_ID.toByteArray(Charsets.UTF_8))
        val encrypted=cipher.doFinal(token.encode().toByteArray(Charsets.UTF_8))
        updateRequire(prefs.edit().putString("iv",Base64.encodeToString(cipher.iv,Base64.NO_WRAP))
            .putString("cipher",Base64.encodeToString(encrypted,Base64.NO_WRAP)).commit(),"无法保存授权，请重试")
    }
    fun load():UpdateAuthorization? {
        val encrypted=prefs.getString("cipher",null) ?: return null
        return try {
            val iv=Base64.decode(prefs.getString("iv",null),Base64.NO_WRAP)
            val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,iv))
            cipher.updateAAD(BuildConfig.UPDATE_CLIENT_ID.toByteArray(Charsets.UTF_8))
            val j=JSONObject(String(cipher.doFinal(Base64.decode(encrypted,Base64.NO_WRAP)),Charsets.UTF_8))
            UpdateAuthorization(j.getString("access"),j.getString("refresh"),j.getLong("expires"),j.getLong("refreshExpires"))
        } catch(_:Exception) { clear();null }
    }
    fun clear() { prefs.edit().clear().commit();runCatching { KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(alias) } }
}
