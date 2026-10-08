package io.noostv.data.cache

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.noostv.core.security.CryptoManager
import io.noostv.data.model.Category
import io.noostv.data.model.Channel
import io.noostv.data.model.Series
import io.noostv.data.model.VodMovie
import java.io.File
import java.security.MessageDigest

/**
 * Conteneur du catalogue Live (Chaînes + Catégories) en cache
 */
data class CachedLiveCatalog(
    val channels: List<Channel>,
    val categories: List<Category>,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Gestionnaire d'indexation locale chiffrée pour NoosTV.
 * Stocke les métadonnées de catalogue (Live, VOD, Séries) de façon chiffrée au repos
 * avec AES-256-GCM (Android KeyStore) pour garantir la sécurité des identifiants et URLs de streaming.
 * Permet un démarrage instantané (0 ms de chargement réseau) et une navigation hors-ligne fluide.
 */
class EncryptedCatalogStore(
    context: Context,
    private val gson: Gson = Gson()
) {
    private val appContext: Context = context.applicationContext

    private fun getAccountDir(serverUrl: String, username: String): File {
        val key = "${serverUrl.trimEnd('/')}:$username"
        val hash = runCatching {
            val md = MessageDigest.getInstance("SHA-256")
            val bytes = md.digest(key.toByteArray(Charsets.UTF_8))
            bytes.take(8).joinToString("") { "%02x".format(it) }
        }.getOrDefault("default")

        val dir = File(appContext.filesDir, "encrypted_catalog_$hash")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    private fun sanitizeCatId(catId: String): String {
        return catId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(32)
    }

    private fun gzipCompress(data: ByteArray): ByteArray {
        val bos = java.io.ByteArrayOutputStream(data.size / 4)
        java.util.zip.GZIPOutputStream(bos).use { it.write(data) }
        return bos.toByteArray()
    }

    private fun gzipDecompress(compressed: ByteArray): ByteArray {
        val bis = java.io.ByteArrayInputStream(compressed)
        val gis = java.util.zip.GZIPInputStream(bis)
        val bos = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var len: Int
        while (gis.read(buffer).also { len = it } > 0) {
            bos.write(buffer, 0, len)
        }
        return bos.toByteArray()
    }

    private fun compressAndEncrypt(plainBytes: ByteArray): ByteArray {
        val compressed = gzipCompress(plainBytes)
        return CryptoManager.encryptBytes(compressed)
    }

    private fun decryptAndDecompress(encryptedBytes: ByteArray): ByteArray {
        val decrypted = CryptoManager.decryptBytes(encryptedBytes)
        if (decrypted.isEmpty()) return decrypted
        return if (decrypted.size >= 2 && decrypted[0] == 0x1f.toByte() && decrypted[1] == 0x8b.toByte()) {
            runCatching { gzipDecompress(decrypted) }.getOrDefault(decrypted)
        } else {
            decrypted
        }
    }

    fun hasLiveCatalog(serverUrl: String, username: String): Boolean {
        if (serverUrl.isBlank()) return false
        val dir = getAccountDir(serverUrl, username)
        val file = File(dir, "live_catalog.enc")
        if (file.exists() && file.length() > 2_500_000L) {
            runCatching { file.delete() }
            return false
        }
        return file.exists() && file.length() > 0L
    }

    // ==================== LIVE CHANNELS & CATEGORIES ====================

    fun saveLiveCatalog(serverUrl: String, username: String, channels: List<Channel>, categories: List<Category>) {
        if (serverUrl.isBlank() || channels.isEmpty()) return
        runCatching {
            val dir = getAccountDir(serverUrl, username)
            val file = File(dir, "live_catalog.enc")
            val payload = CachedLiveCatalog(channels = channels, categories = categories)
            val json = gson.toJson(payload)
            val encrypted = compressAndEncrypt(json.toByteArray(Charsets.UTF_8))
            file.writeBytes(encrypted)
        }
    }

    fun loadLiveCatalog(serverUrl: String, username: String): CachedLiveCatalog? {
        if (serverUrl.isBlank()) return null
        return runCatching {
            val dir = getAccountDir(serverUrl, username)
            val file = File(dir, "live_catalog.enc")
            if (!file.exists() || file.length() == 0L) return null
            if (file.length() > 2_500_000L) {
                runCatching { file.delete() }
                return null
            }

            val encrypted = file.readBytes()
            val decrypted = decryptAndDecompress(encrypted)
            if (decrypted.isEmpty()) return null

            val json = String(decrypted, Charsets.UTF_8)
            gson.fromJson(json, CachedLiveCatalog::class.java)
        }.getOrNull()
    }

    // ==================== VOD CATEGORIES & MOVIES ====================

    fun saveVodCategories(serverUrl: String, username: String, categories: List<Category>) {
        if (serverUrl.isBlank() || categories.isEmpty()) return
        runCatching {
            val dir = getAccountDir(serverUrl, username)
            val file = File(dir, "vod_categories.enc")
            val json = gson.toJson(categories)
            val encrypted = compressAndEncrypt(json.toByteArray(Charsets.UTF_8))
            file.writeBytes(encrypted)
        }
    }

    fun loadVodCategories(serverUrl: String, username: String): List<Category>? {
        if (serverUrl.isBlank()) return null
        return runCatching {
            val dir = getAccountDir(serverUrl, username)
            val file = File(dir, "vod_categories.enc")
            if (!file.exists() || file.length() == 0L) return null

            val encrypted = file.readBytes()
            val decrypted = decryptAndDecompress(encrypted)
            if (decrypted.isEmpty()) return null

            val json = String(decrypted, Charsets.UTF_8)
            val type = object : TypeToken<List<Category>>() {}.type
            gson.fromJson<List<Category>>(json, type)
        }.getOrNull()
    }

    fun saveVodMovies(serverUrl: String, username: String, categoryId: String, movies: List<VodMovie>) {
        if (serverUrl.isBlank() || movies.isEmpty()) return
        runCatching {
            val dir = getAccountDir(serverUrl, username)
            val safeCat = sanitizeCatId(categoryId)
            val file = File(dir, "vod_${safeCat}.enc")
            val json = gson.toJson(movies)
            val encrypted = compressAndEncrypt(json.toByteArray(Charsets.UTF_8))
            file.writeBytes(encrypted)
        }
    }

    fun loadVodMovies(serverUrl: String, username: String, categoryId: String): List<VodMovie>? {
        if (serverUrl.isBlank()) return null
        return runCatching {
            val dir = getAccountDir(serverUrl, username)
            val safeCat = sanitizeCatId(categoryId)
            val file = File(dir, "vod_${safeCat}.enc")
            if (!file.exists() || file.length() == 0L) return null

            val encrypted = file.readBytes()
            val decrypted = decryptAndDecompress(encrypted)
            if (decrypted.isEmpty()) return null

            val json = String(decrypted, Charsets.UTF_8)
            val type = object : TypeToken<List<VodMovie>>() {}.type
            gson.fromJson<List<VodMovie>>(json, type)
        }.getOrNull()
    }

    // ==================== SERIES CATEGORIES & ITEMS ====================

    fun saveSeriesCategories(serverUrl: String, username: String, categories: List<Category>) {
        if (serverUrl.isBlank() || categories.isEmpty()) return
        runCatching {
            val dir = getAccountDir(serverUrl, username)
            val file = File(dir, "series_categories.enc")
            val json = gson.toJson(categories)
            val encrypted = compressAndEncrypt(json.toByteArray(Charsets.UTF_8))
            file.writeBytes(encrypted)
        }
    }

    fun loadSeriesCategories(serverUrl: String, username: String): List<Category>? {
        if (serverUrl.isBlank()) return null
        return runCatching {
            val dir = getAccountDir(serverUrl, username)
            val file = File(dir, "series_categories.enc")
            if (!file.exists() || file.length() == 0L) return null

            val encrypted = file.readBytes()
            val decrypted = decryptAndDecompress(encrypted)
            if (decrypted.isEmpty()) return null

            val json = String(decrypted, Charsets.UTF_8)
            val type = object : TypeToken<List<Category>>() {}.type
            gson.fromJson<List<Category>>(json, type)
        }.getOrNull()
    }

    fun saveSeries(serverUrl: String, username: String, categoryId: String, seriesList: List<Series>) {
        if (serverUrl.isBlank() || seriesList.isEmpty()) return
        runCatching {
            val dir = getAccountDir(serverUrl, username)
            val safeCat = sanitizeCatId(categoryId)
            val file = File(dir, "series_${safeCat}.enc")
            val json = gson.toJson(seriesList)
            val encrypted = compressAndEncrypt(json.toByteArray(Charsets.UTF_8))
            file.writeBytes(encrypted)
        }
    }

    fun loadSeries(serverUrl: String, username: String, categoryId: String): List<Series>? {
        if (serverUrl.isBlank()) return null
        return runCatching {
            val dir = getAccountDir(serverUrl, username)
            val safeCat = sanitizeCatId(categoryId)
            val file = File(dir, "series_${safeCat}.enc")
            if (!file.exists() || file.length() == 0L) return null

            val encrypted = file.readBytes()
            val decrypted = decryptAndDecompress(encrypted)
            if (decrypted.isEmpty()) return null

            val json = String(decrypted, Charsets.UTF_8)
            val type = object : TypeToken<List<Series>>() {}.type
            gson.fromJson<List<Series>>(json, type)
        }.getOrNull()
    }

    // ==================== NETTOYAGE DU CACHE ====================

    fun clearAccountCache(serverUrl: String, username: String) {
        runCatching {
            val dir = getAccountDir(serverUrl, username)
            dir.deleteRecursively()
        }
    }

    fun clearAllCaches() {
        runCatching {
            val root = appContext.filesDir
            root.listFiles()?.filter { it.isDirectory && it.name.startsWith("encrypted_catalog_") }
                ?.forEach { it.deleteRecursively() }
        }
    }
}
