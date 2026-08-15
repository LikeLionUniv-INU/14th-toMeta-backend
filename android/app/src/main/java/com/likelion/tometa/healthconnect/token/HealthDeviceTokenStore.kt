package com.likelion.tometa.healthconnect.token

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class HealthDeviceTokenStore(
    context: Context
) {

    private val preferences =
        context.applicationContext.getSharedPreferences(
            PREFERENCES_NAME,
            Context.MODE_PRIVATE
        )

    suspend fun saveToken(
        token: String
    ) = withContext(Dispatchers.IO) {

        require(token.isNotBlank()) {
            "healthDeviceToken이 비어 있습니다."
        }

        val secretKey =
            getOrCreateSecretKey()

        val cipher =
            Cipher.getInstance(
                TRANSFORMATION
            )

        cipher.init(
            Cipher.ENCRYPT_MODE,
            secretKey
        )

        val encryptedToken =
            cipher.doFinal(
                token.toByteArray(
                    Charsets.UTF_8
                )
            )

        val saved =
            preferences.edit()
                .putString(
                    ENCRYPTED_TOKEN_KEY,
                    Base64.encodeToString(
                        encryptedToken,
                        Base64.NO_WRAP
                    )
                )
                .putString(
                    IV_KEY,
                    Base64.encodeToString(
                        cipher.iv,
                        Base64.NO_WRAP
                    )
                )
                .commit()

        check(saved) {
            "healthDeviceToken 저장에 실패했습니다."
        }
    }

    suspend fun getToken(): String? =
        withContext(Dispatchers.IO) {

            val encryptedTokenBase64 =
                preferences.getString(
                    ENCRYPTED_TOKEN_KEY,
                    null
                )
                    ?: return@withContext null

            val ivBase64 =
                preferences.getString(
                    IV_KEY,
                    null
                )
                    ?: return@withContext null

            try {

                val secretKey =
                    getOrCreateSecretKey()

                val cipher =
                    Cipher.getInstance(
                        TRANSFORMATION
                    )

                val iv =
                    Base64.decode(
                        ivBase64,
                        Base64.NO_WRAP
                    )

                cipher.init(
                    Cipher.DECRYPT_MODE,
                    secretKey,
                    GCMParameterSpec(
                        GCM_TAG_LENGTH,
                        iv
                    )
                )

                val encryptedToken =
                    Base64.decode(
                        encryptedTokenBase64,
                        Base64.NO_WRAP
                    )

                val decryptedToken =
                    cipher.doFinal(
                        encryptedToken
                    )

                String(
                    decryptedToken,
                    Charsets.UTF_8
                )

            } catch (e: Exception) {
                throw IllegalStateException(
                    "healthDeviceToken 복호화에 실패했습니다.",
                    e
                )
            }
        }

    suspend fun clearToken() =
        withContext(Dispatchers.IO) {

            val cleared =
                preferences.edit()
                    .remove(
                        ENCRYPTED_TOKEN_KEY
                    )
                    .remove(
                        IV_KEY
                    )
                    .commit()

            check(cleared) {
                "healthDeviceToken 삭제에 실패했습니다."
            }
        }

    private fun getOrCreateSecretKey(): SecretKey =
        synchronized(KEY_LOCK) {

            val keyStore =
                KeyStore.getInstance(
                    ANDROID_KEY_STORE
                ).apply {
                    load(null)
                }

            val existingKey =
                keyStore.getKey(
                    KEY_ALIAS,
                    null
                ) as? SecretKey

            if (existingKey != null) {
                return@synchronized existingKey
            }

            val keyGenerator =
                KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES,
                    ANDROID_KEY_STORE
                )

            val keySpec =
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or
                            KeyProperties.PURPOSE_DECRYPT
                )
                    .setKeySize(256)
                    .setBlockModes(
                        KeyProperties.BLOCK_MODE_GCM
                    )
                    .setEncryptionPaddings(
                        KeyProperties.ENCRYPTION_PADDING_NONE
                    )
                    .build()

            keyGenerator.init(
                keySpec
            )

            keyGenerator.generateKey()
        }

    private companion object {

        val KEY_LOCK =
            Any()

        const val ANDROID_KEY_STORE =
            "AndroidKeyStore"

        const val KEY_ALIAS =
            "tometa_health_device_token_key"

        const val TRANSFORMATION =
            "AES/GCM/NoPadding"

        const val GCM_TAG_LENGTH =
            128

        const val PREFERENCES_NAME =
            "com.likelion.tometa.health_device_token"

        const val ENCRYPTED_TOKEN_KEY =
            "encrypted_health_device_token"

        const val IV_KEY =
            "health_device_token_iv"
    }
}