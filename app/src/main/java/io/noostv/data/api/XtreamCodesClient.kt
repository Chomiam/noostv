package io.noostv.data.api

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.noostv.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Informations de compte et d'authentification Xtream Codes
 */
data class XtreamAccountInfo(
    val username: String,
    val status: String,
    val expDateEpoch: Long?,
    val isTrial: Boolean,
    val activeConnections: Int,
    val maxConnections: Int,
    val serverUrl: String
)

/**
 * Client universel pour l'API Xtream Codes v2 / Dispatcharr / IPTV SaaS.
 * Gère les flux Live, VOD Films, Séries, EPG court et la construction d'URL de streaming.
 */
class XtreamCodesClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build(),
    private val gson: Gson = Gson()
) {

    /**
     * Authentifie l'utilisateur sur le serveur Xtream Codes
     */
    suspend fun authenticate(
        serverUrl: String,
        username: String,
        password: String
    ): Result<XtreamAccountInfo> = withContext(Dispatchers.IO) {
        val cleanServer = serverUrl.trimEnd('/')
        val url = "$cleanServer/player_api.php?username=$username&password=$password"

        try {
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Erreur HTTP: ${response.code}"))
            }

            val body = response.body?.string() ?: return@withContext Result.failure(Exception("Réponse vide"))
            val type = object : TypeToken<Map<String, Any>>() {}.type
            val jsonMap: Map<String, Any> = gson.fromJson(body, type)

            @Suppress("UNCHECKED_CAST")
            val userInfo = jsonMap["user_info"] as? Map<String, Any>
            if (userInfo == null || userInfo["auth"] == 0.0) {
                return@withContext Result.failure(Exception("Identifiants incorrects ou compte expiré"))
            }

            val status = userInfo["status"] as? String ?: "Active"
            val expDate = (userInfo["exp_date"] as? String)?.toLongOrNull()
            val isTrial = userInfo["is_trial"] == "1"
            val activeCons = (userInfo["active_cons"] as? String)?.toIntOrNull() ?: 0
            val maxCons = (userInfo["max_connections"] as? String)?.toIntOrNull() ?: 1

            Result.success(
                XtreamAccountInfo(
                    username = username,
                    status = status,
                    expDateEpoch = expDate,
                    isTrial = isTrial,
                    activeConnections = activeCons,
                    maxConnections = maxCons,
                    serverUrl = cleanServer
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Récupère les catégories du direct (Live TV)
     */
    suspend fun getLiveCategories(
        serverUrl: String,
        username: String,
        password: String
    ): Result<List<Category>> = withContext(Dispatchers.IO) {
        fetchCategories(serverUrl, username, password, "get_live_categories", CategoryType.LIVE)
    }

    /**
     * Récupère les chaînes en direct d'un serveur Xtream Codes
     */
    suspend fun getLiveStreams(
        serverUrl: String,
        username: String,
        password: String,
        categoryId: String? = null
    ): Result<List<Channel>> = withContext(Dispatchers.IO) {
        val cleanServer = serverUrl.trimEnd('/')
        val url = buildString {
            append("$cleanServer/player_api.php?username=$username&password=$password&action=get_live_streams")
            if (!categoryId.isNullOrBlank()) {
                append("&category_id=$categoryId")
            }
        }

        try {
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext Result.failure(Exception("Réponse vide"))
            val type = object : TypeToken<List<Map<String, Any>>>() {}.type
            val rawList: List<Map<String, Any>> = gson.fromJson(body, type)

            val channels = rawList.mapNotNull { item ->
                val streamId = item["stream_id"]?.toString()?.substringBefore(".") ?: return@mapNotNull null
                val name = item["name"]?.toString() ?: "Chaîne $streamId"
                val logo = item["stream_icon"]?.toString()
                val catId = item["category_id"]?.toString() ?: "general"
                val num = (item["num"] as? Number)?.toInt()
                val epgId = item["epg_channel_id"]?.toString()
                val archive = (item["tv_archive"] as? Number)?.toInt() ?: 0
                val archiveDuration = (item["tv_archive_duration"] as? Number)?.toInt() ?: 0

                val isHdr = name.contains("HDR", ignoreCase = true) || name.contains("Dolby Vision", ignoreCase = true)
                val resolution = when {
                    name.contains("4K", ignoreCase = true) || name.contains("UHD", ignoreCase = true) -> "4K UHD"
                    name.contains("720", ignoreCase = true) -> "720p"
                    name.contains("FHD", ignoreCase = true) || name.contains("1080", ignoreCase = true) || name.contains("HD", ignoreCase = true) -> "1080p"
                    else -> "1080p"
                }
                val codec = when {
                    name.contains("AV1", ignoreCase = true) -> "av1"
                    name.contains("HEVC", ignoreCase = true) || name.contains("H265", ignoreCase = true) || isHdr || resolution == "4K UHD" -> "hevc"
                    else -> "h264"
                }

                val streamUrl = buildLiveStreamUrl(cleanServer, username, password, streamId, "ts")

                Channel(
                    id = streamId,
                    num = num,
                    name = name,
                    streamUrl = streamUrl,
                    logoUrl = logo,
                    categoryId = catId,
                    categoryName = "Catégorie $catId",
                    isHdr = isHdr,
                    resolution = resolution,
                    videoCodec = codec,
                    epgChannelId = epgId,
                    hasCatchup = archive > 0,
                    catchupDays = archiveDuration
                )
            }

            Result.success(channels)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Récupère les catégories VOD Films
     */
    suspend fun getVodCategories(
        serverUrl: String,
        username: String,
        password: String
    ): Result<List<Category>> = withContext(Dispatchers.IO) {
        fetchCategories(serverUrl, username, password, "get_vod_categories", CategoryType.VOD)
    }

    /**
     * Récupère les films VOD (avec limitation configurable pour préserver la RAM)
     */
    suspend fun getVodStreams(
        serverUrl: String,
        username: String,
        password: String,
        categoryId: String? = null,
        limit: Int = 100
    ): Result<List<VodMovie>> = withContext(Dispatchers.IO) {
        val cleanServer = serverUrl.trimEnd('/')
        val url = buildString {
            append("$cleanServer/player_api.php?username=$username&password=$password&action=get_vod_streams")
            if (!categoryId.isNullOrBlank()) {
                append("&category_id=$categoryId")
            }
        }

        try {
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext Result.failure(Exception("Réponse vide"))
            val type = object : TypeToken<List<Map<String, Any>>>() {}.type
            val rawList: List<Map<String, Any>> = gson.fromJson(body, type)

            val movies = rawList.take(limit).mapNotNull { item ->
                val streamId = item["stream_id"]?.toString()?.substringBefore(".") ?: return@mapNotNull null
                val name = item["name"]?.toString() ?: "Film $streamId"
                val poster = item["stream_icon"]?.toString()
                val rating = (item["rating"]?.toString()?.toFloatOrNull()) ?: 0f
                val year = (item["year"] as? Number)?.toInt()
                val plot = item["plot"]?.toString()
                val ext = item["container_extension"]?.toString() ?: "mp4"

                val isHdr = name.contains("HDR", ignoreCase = true) || name.contains("Dolby Vision", ignoreCase = true)
                val hdrFormat = if (name.contains("Dolby Vision", ignoreCase = true)) "Dolby Vision" else if (isHdr) "HDR10" else null
                val resolution = if (name.contains("4K", ignoreCase = true) || name.contains("UHD", ignoreCase = true)) "4K UHD" else "1080p"
                val streamUrl = buildVodStreamUrl(cleanServer, username, password, streamId, ext)

                VodMovie(
                    id = streamId,
                    title = name,
                    streamUrl = streamUrl,
                    posterUrl = poster,
                    rating = rating,
                    releaseYear = year,
                    plot = plot,
                    isHdr = isHdr,
                    hdrFormat = hdrFormat,
                    resolution = resolution,
                    videoCodec = if (isHdr) "hevc" else "h264"
                )
            }

            Result.success(movies)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Récupère les catégories de Séries
     */
    suspend fun getSeriesCategories(
        serverUrl: String,
        username: String,
        password: String
    ): Result<List<Category>> = withContext(Dispatchers.IO) {
        fetchCategories(serverUrl, username, password, "get_series_categories", CategoryType.SERIES)
    }

    /**
     * Récupère les séries
     */
    suspend fun getSeriesStreams(
        serverUrl: String,
        username: String,
        password: String,
        categoryId: String? = null,
        limit: Int = 100
    ): Result<List<Series>> = withContext(Dispatchers.IO) {
        val cleanServer = serverUrl.trimEnd('/')
        val url = buildString {
            append("$cleanServer/player_api.php?username=$username&password=$password&action=get_series")
            if (!categoryId.isNullOrBlank()) {
                append("&category_id=$categoryId")
            }
        }

        try {
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext Result.failure(Exception("Réponse vide"))
            val type = object : TypeToken<List<Map<String, Any>>>() {}.type
            val rawList: List<Map<String, Any>> = gson.fromJson(body, type)

            val series = rawList.take(limit).mapNotNull { item ->
                val seriesId = item["series_id"]?.toString()?.substringBefore(".") ?: return@mapNotNull null
                val name = item["name"]?.toString() ?: "Série $seriesId"
                val cover = item["cover"]?.toString()
                val plot = item["plot"]?.toString()
                val rating = (item["rating"]?.toString()?.toFloatOrNull()) ?: 0f
                val year = item["releaseDate"]?.toString()?.toIntOrNull() ?: item["release_date"]?.toString()?.toIntOrNull()

                Series(
                    id = seriesId,
                    title = name,
                    posterUrl = cover,
                    rating = rating,
                    releaseYear = year,
                    plot = plot
                )
            }

            Result.success(series)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Récupère l'EPG court d'une chaîne avec décodage Base64
     */
    suspend fun getShortEpg(
        serverUrl: String,
        username: String,
        password: String,
        streamId: String
    ): Result<List<EpgProgram>> = withContext(Dispatchers.IO) {
        val cleanServer = serverUrl.trimEnd('/')
        val url = "$cleanServer/player_api.php?username=$username&password=$password&action=get_short_epg&stream_id=$streamId"

        try {
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext Result.failure(Exception("Réponse vide"))
            val type = object : TypeToken<Map<String, Any>>() {}.type
            val jsonMap: Map<String, Any> = gson.fromJson(body, type)

            @Suppress("UNCHECKED_CAST")
            val listings = jsonMap["epg_listings"] as? List<Map<String, Any>> ?: return@withContext Result.success(emptyList())

            val programs = listings.mapNotNull { item ->
                val rawTitle = item["title"]?.toString() ?: return@mapNotNull null
                val rawDesc = item["description"]?.toString()
                val startTs = (item["start_timestamp"]?.toString()?.toLongOrNull()) ?: 0L
                val stopTs = (item["stop_timestamp"]?.toString()?.toLongOrNull()) ?: 0L
                val hasArchive = (item["has_archive"] as? Number)?.toInt() ?: 0

                val title = maybeDecodeBase64(rawTitle)
                val desc = rawDesc?.let { maybeDecodeBase64(it) }

                EpgProgram(
                    id = item["id"]?.toString() ?: UUID.randomUUID().toString(),
                    channelId = streamId,
                    title = title,
                    description = desc,
                    startEpochMs = startTs * 1000,
                    stopEpochMs = stopTs * 1000,
                    hasCatchup = hasArchive > 0
                )
            }

            Result.success(programs)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun maybeDecodeBase64(input: String): String {
        return try {
            val decodedBytes = java.util.Base64.getDecoder().decode(input.trim())
            val decodedStr = String(decodedBytes, Charsets.UTF_8)
            // Si le résultat est un texte lisible, on le conserve
            if (decodedStr.all { it.isLetterOrDigit() || it.isWhitespace() || it in ".,;:!?'\"-()/@#" }) {
                decodedStr
            } else {
                input
            }
        } catch (e: Exception) {
            input
        }
    }

    private fun fetchCategories(
        serverUrl: String,
        username: String,
        password: String,
        action: String,
        categoryType: CategoryType
    ): Result<List<Category>> {
        val cleanServer = serverUrl.trimEnd('/')
        val url = "$cleanServer/player_api.php?username=$username&password=$password&action=$action"

        return try {
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return Result.failure(Exception("Réponse vide"))
            val type = object : TypeToken<List<Map<String, Any>>>() {}.type
            val rawList: List<Map<String, Any>> = gson.fromJson(body, type)

            val categories = rawList.mapNotNull { item ->
                val id = item["category_id"]?.toString() ?: return@mapNotNull null
                val name = item["category_name"]?.toString() ?: "Inconnu"
                Category(id = id, name = name, type = categoryType)
            }
            Result.success(categories)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Construit l'URL directe de streaming pour une chaîne en direct
     */
    fun buildLiveStreamUrl(serverUrl: String, username: String, password: String, streamId: String, extension: String = "ts"): String {
        return "${serverUrl.trimEnd('/')}/live/$username/$password/$streamId.$extension"
    }

    /**
     * Construit l'URL directe de streaming pour un film VOD
     */
    fun buildVodStreamUrl(serverUrl: String, username: String, password: String, streamId: String, extension: String = "mp4"): String {
        return "${serverUrl.trimEnd('/')}/movie/$username/$password/$streamId.$extension"
    }

    /**
     * Construit l'URL directe de streaming pour un épisode de série
     */
    fun buildSeriesStreamUrl(serverUrl: String, username: String, password: String, streamId: String, extension: String = "mp4"): String {
        return "${serverUrl.trimEnd('/')}/series/$username/$password/$streamId.$extension"
    }
}
