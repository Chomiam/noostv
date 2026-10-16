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

    private fun writeEncryptedJson(file: File, src: Any, typeOfSrc: java.lang.reflect.Type? = null) {
        val bos = java.io.ByteArrayOutputStream(16_384)
        java.util.zip.GZIPOutputStream(bos).use { gos ->
            java.io.OutputStreamWriter(gos, Charsets.UTF_8).use { writer ->
                if (typeOfSrc != null) {
                    gson.toJson(src, typeOfSrc, writer)
                } else {
                    gson.toJson(src, writer)
                }
            }
        }
        val encrypted = CryptoManager.encryptBytes(bos.toByteArray())
        file.writeBytes(encrypted)
    }

    private fun <T> readEncryptedJson(file: File, typeOfT: java.lang.reflect.Type): T? {
        if (!file.exists() || file.length() == 0L) return null
        val encrypted = file.readBytes()
        val decrypted = CryptoManager.decryptBytes(encrypted)
        if (decrypted.isEmpty()) return null
        val isGzip = decrypted.size >= 2 && decrypted[0] == 0x1f.toByte() && decrypted[1] == 0x8b.toByte()
        val inputStream = if (isGzip) {
            java.util.zip.GZIPInputStream(java.io.ByteArrayInputStream(decrypted))
        } else {
            java.io.ByteArrayInputStream(decrypted)
        }
        return inputStream.use { stream ->
            java.io.InputStreamReader(stream, Charsets.UTF_8).use { reader ->
                gson.fromJson<T>(reader, typeOfT)
            }
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
            writeEncryptedJson(file, payload)
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
            readEncryptedJson<CachedLiveCatalog>(file, CachedLiveCatalog::class.java)
        }.getOrNull()
    }

    // ==================== VOD CATEGORIES & MOVIES ====================

    fun saveVodCategories(serverUrl: String, username: String, categories: List<Category>) {
        if (serverUrl.isBlank() || categories.isEmpty()) return
        runCatching {
            val dir = getAccountDir(serverUrl, username)
            val file = File(dir, "vod_categories.enc")
            writeEncryptedJson(file, categories)
        }
    }

    fun loadVodCategories(serverUrl: String, username: String): List<Category>? {
        if (serverUrl.isBlank()) return null
        return runCatching {
            val dir = getAccountDir(serverUrl, username)
            val file = File(dir, "vod_categories.enc")
            val type = object : TypeToken<List<Category>>() {}.type
            readEncryptedJson<List<Category>>(file, type)
        }.getOrNull()
    }

    fun saveVodMovies(serverUrl: String, username: String, categoryId: String, movies: List<VodMovie>) {
        if (serverUrl.isBlank() || movies.isEmpty()) return
        runCatching {
            val dir = getAccountDir(serverUrl, username)
            val safeCat = sanitizeCatId(categoryId)
            val file = File(dir, "vod_${safeCat}.enc")
            writeEncryptedJson(file, movies)
        }
    }

    fun loadVodMovies(serverUrl: String, username: String, categoryId: String): List<VodMovie>? {
        if (serverUrl.isBlank()) return null
        return runCatching {
            val dir = getAccountDir(serverUrl, username)
            val safeCat = sanitizeCatId(categoryId)
            val file = File(dir, "vod_${safeCat}.enc")
            val type = object : TypeToken<List<VodMovie>>() {}.type
            readEncryptedJson<List<VodMovie>>(file, type)
        }.getOrNull()
    }

    // ==================== SERIES CATEGORIES & ITEMS ====================

    fun saveSeriesCategories(serverUrl: String, username: String, categories: List<Category>) {
        if (serverUrl.isBlank() || categories.isEmpty()) return
        runCatching {
            val dir = getAccountDir(serverUrl, username)
            val file = File(dir, "series_categories.enc")
            writeEncryptedJson(file, categories)
        }
    }

    fun loadSeriesCategories(serverUrl: String, username: String): List<Category>? {
        if (serverUrl.isBlank()) return null
        return runCatching {
            val dir = getAccountDir(serverUrl, username)
            val file = File(dir, "series_categories.enc")
            val type = object : TypeToken<List<Category>>() {}.type
            readEncryptedJson<List<Category>>(file, type)
        }.getOrNull()
    }

    fun saveSeries(serverUrl: String, username: String, categoryId: String, seriesList: List<Series>) {
        if (serverUrl.isBlank() || seriesList.isEmpty()) return
        runCatching {
            val dir = getAccountDir(serverUrl, username)
            val safeCat = sanitizeCatId(categoryId)
            val file = File(dir, "series_${safeCat}.enc")
            writeEncryptedJson(file, seriesList)
        }
    }

    fun loadSeries(serverUrl: String, username: String, categoryId: String): List<Series>? {
        if (serverUrl.isBlank()) return null
        return runCatching {
            val dir = getAccountDir(serverUrl, username)
            val safeCat = sanitizeCatId(categoryId)
            val file = File(dir, "series_${safeCat}.enc")
            val type = object : TypeToken<List<Series>>() {}.type
            readEncryptedJson<List<Series>>(file, type)
        }.getOrNull()
    }

    // ==================== FAVORIS VOD & SÉRIES PAR PROFIL ====================

    fun saveFavoriteMovies(serverUrl: String, username: String, profileId: String, movies: List<VodMovie>) {
        if (serverUrl.isBlank() || profileId.isBlank()) return
        runCatching {
            val dir = getAccountDir(serverUrl, username)
            val safeProf = sanitizeCatId(profileId)
            val file = File(dir, "fav_movies_${safeProf}.enc")
            writeEncryptedJson(file, movies)
        }
    }

    fun loadFavoriteMovies(serverUrl: String, username: String, profileId: String): List<VodMovie> {
        if (serverUrl.isBlank() || profileId.isBlank()) return emptyList()
        return runCatching {
            val dir = getAccountDir(serverUrl, username)
            val safeProf = sanitizeCatId(profileId)
            val file = File(dir, "fav_movies_${safeProf}.enc")
            val type = object : TypeToken<List<VodMovie>>() {}.type
            readEncryptedJson<List<VodMovie>>(file, type) ?: emptyList()
        }.getOrDefault(emptyList())
    }

    fun saveFavoriteSeries(serverUrl: String, username: String, profileId: String, seriesList: List<Series>) {
        if (serverUrl.isBlank() || profileId.isBlank()) return
        runCatching {
            val dir = getAccountDir(serverUrl, username)
            val safeProf = sanitizeCatId(profileId)
            val file = File(dir, "fav_series_${safeProf}.enc")
            // Stocker uniquement les métadonnées de présentation de la série
            val trimmed = seriesList.map { s ->
                s.copy(
                    seasons = s.seasons.map { season ->
                        season.copy(
                            episodes = season.episodes.map { ep ->
                                ep.copy(plot = null)
                            }
                        )
                    }
                )
            }
            writeEncryptedJson(file, trimmed)
        }
    }

    fun loadFavoriteSeries(serverUrl: String, username: String, profileId: String): List<Series> {
        if (serverUrl.isBlank() || profileId.isBlank()) return emptyList()
        return runCatching {
            val dir = getAccountDir(serverUrl, username)
            val safeProf = sanitizeCatId(profileId)
            val file = File(dir, "fav_series_${safeProf}.enc")
            val type = object : TypeToken<List<Series>>() {}.type
            readEncryptedJson<List<Series>>(file, type) ?: emptyList()
        }.getOrDefault(emptyList())
    }

    fun findVodMoviesByIds(serverUrl: String, username: String, targetIds: Set<String>): List<VodMovie> {
        if (serverUrl.isBlank() || targetIds.isEmpty()) return emptyList()
        val dir = getAccountDir(serverUrl, username)
        val files = dir.listFiles()?.filter {
            it.name.startsWith("vod_") && it.name.endsWith(".enc") && !it.name.startsWith("vod_categories")
        } ?: return emptyList()
        val found = mutableListOf<VodMovie>()
        val remaining = targetIds.toMutableSet()

        val type = object : TypeToken<List<VodMovie>>() {}.type
        for (file in files) {
            if (remaining.isEmpty()) break
            runCatching {
                val list: List<VodMovie>? = readEncryptedJson(file, type)
                list?.forEach { movie ->
                    if (remaining.contains(movie.id)) {
                        found.add(movie)
                        remaining.remove(movie.id)
                    }
                }
            }
        }
        return found
    }

    fun findSeriesByIds(serverUrl: String, username: String, targetIds: Set<String>): List<Series> {
        if (serverUrl.isBlank() || targetIds.isEmpty()) return emptyList()
        val dir = getAccountDir(serverUrl, username)
        val files = dir.listFiles()?.filter {
            it.name.startsWith("series_") && it.name.endsWith(".enc") && !it.name.startsWith("series_categories")
        } ?: return emptyList()
        val found = mutableListOf<Series>()
        val remaining = targetIds.toMutableSet()

        val type = object : TypeToken<List<Series>>() {}.type
        for (file in files) {
            if (remaining.isEmpty()) break
            runCatching {
                val list: List<Series>? = readEncryptedJson(file, type)
                list?.forEach { ser ->
                    if (remaining.contains(ser.id)) {
                        found.add(ser)
                        remaining.remove(ser.id)
                    }
                }
            }
        }
        return found
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
