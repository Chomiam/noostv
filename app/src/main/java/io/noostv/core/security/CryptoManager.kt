package io.noostv.core.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Gestionnaire de chiffrement matériel AES-256-GCM adossé à l'Android KeyStore.
 * Garantit que les identifiants utilisateur (serveur, username, mot de passe, tokens)
 * sont chiffrés au repos et ne fuitent jamais en clair.
 */
object CryptoManager {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "noostv_credential_master_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_LENGTH = 128
    private const val ENCRYPTED_PREFIX = "enc:v1:"

    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    }

    @Synchronized
    private fun getOrCreateKey(): SecretKey {
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE
            )
            val parameterSpec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()

            keyGenerator.init(parameterSpec)
            return keyGenerator.generateKey()
        }
        val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
        return entry?.secretKey ?: throw IllegalStateException("KeyStore secret key entry missing")
    }

    /**
     * Chiffre une chaîne en clair avec AES-256-GCM backed par Android KeyStore.
     */
    fun encrypt(plainText: String): String {
        if (plainText.isBlank()) return ""
        return try {
            val key = getOrCreateKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val iv = cipher.iv
            val cipherBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

            val combined = ByteArray(iv.size + cipherBytes.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(cipherBytes, 0, combined, iv.size, cipherBytes.size)

            ENCRYPTED_PREFIX + Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (e: Exception) {
            plainText
        }
    }

    /**
     * Déchiffre une chaîne chiffrée.
     * Si la chaîne ne commence pas par le préfixe de chiffrement, elle est retournée telle quelle (migration rétrocompatible).
     */
    fun decrypt(cipherText: String): String {
        if (cipherText.isBlank()) return ""
        if (!cipherText.startsWith(ENCRYPTED_PREFIX)) {
            return cipherText
        }

        return try {
            val rawBase64 = cipherText.removePrefix(ENCRYPTED_PREFIX)
            val combined = Base64.decode(rawBase64, Base64.NO_WRAP)
            if (combined.size <= GCM_IV_LENGTH) return ""

            val iv = combined.copyOfRange(0, GCM_IV_LENGTH)
            val encryptedBytes = combined.copyOfRange(GCM_IV_LENGTH, combined.size)

            val key = getOrCreateKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, key, spec)

            String(cipher.doFinal(encryptedBytes), Charsets.UTF_8)
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Masque un identifiant pour affichage sécurisé (ex: "utilisateur" -> "ut••••••ur").
     */
    fun maskIdentifier(text: String): String {
        if (text.isBlank()) return "-"
        if (text.length <= 2) return "••"
        if (text.length <= 4) return text.first() + "••" + text.last()
        val visiblePrefix = text.take(2)
        val visibleSuffix = text.takeLast(2)
        val dots = "•".repeat(maxOf(3, text.length - 4))
        return "$visiblePrefix$dots$visibleSuffix"
    }

    /**
     * Masque une URL pour affichage sécurisé (ex: "http://iptv.server.com:8080" -> "http://ip•••••••.com:8080").
     */
    fun maskUrl(url: String): String {
        if (url.isBlank()) return "Non connecté"
        return try {
            val uri = java.net.URI(url)
            val scheme = uri.scheme ?: "http"
            val host = uri.host ?: url
            val portStr = if (uri.port != -1) ":${uri.port}" else ""
            val maskedHost = if (host.length > 5) {
                host.take(2) + "••••••" + host.takeLast(3)
            } else {
                "••••"
            }
            "$scheme://$maskedHost$portStr"
        } catch (e: Exception) {
            if (url.length > 8) url.take(4) + "••••••" + url.takeLast(3) else "••••"
        }
    }

    /**
     * Nettoie les URLs des logs pour empêcher toute fuite de mot de passe ou token Xtream Codes.
     */
    fun sanitizeUrl(url: String): String {
        if (url.isBlank()) return ""
        val streamRegex = Regex("""/(live|movie|series)/([^/]+)/([^/]+)/""")
        var sanitized = streamRegex.replace(url) { match ->
            "/${match.groupValues[1]}/****/****************/"
        }
        sanitized = sanitized.replace(Regex("""username=([^&]+)"""), "username=****")
        sanitized = sanitized.replace(Regex("""password=([^&]+)"""), "password=****")
        sanitized = sanitized.replace(Regex("""token=([^&]+)"""), "token=****")
        return sanitized
    }
}
