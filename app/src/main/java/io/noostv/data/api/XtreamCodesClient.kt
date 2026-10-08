package io.noostv.data.api

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.noostv.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.ConnectionSpec
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
        .connectionPool(ConnectionPool(8, 5, TimeUnit.MINUTES))
        .retryOnConnectionFailure(true)
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .connectionSpecs(listOf(ConnectionSpec.MODERN_TLS, ConnectionSpec.COMPATIBLE_TLS, ConnectionSpec.CLEARTEXT))
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", USER_AGENT)
                .build()
            chain.proceed(request)
        }
        .build(),
    private val gson: Gson = Gson()
) {

    companion object {
        const val USER_AGENT = "NoosTV/1.2.6 (Android TV; ExoPlayer)"

        /**
         * Masque les identifiants et mots de passe dans les URLs pour éviter toute fuite dans les logs.
         */
        fun sanitizeUrl(url: String): String {
            return url.replace(Regex("password=[^&\\s]+"), "password=***")
                .replace(Regex("/(live|movie|series)/([^/]+)/([^/]+)/"), "/$1/$2/***/")
        }
    }

    /**
     * Lit et désérialise le JSON directement depuis le flux réseau sans allouer de String intermédiaire géante
     */
    private inline fun <T> parseJsonStreaming(response: okhttp3.Response, type: java.lang.reflect.Type): T? {
        val responseBody = response.body ?: return null
        return runCatching {
            responseBody.charStream().use { reader ->
                gson.fromJson<T>(reader, type)
            }
        }.getOrNull()
    }

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

            val type = object : TypeToken<Map<String, Any>>() {}.type
            val jsonMap: Map<String, Any> = parseJsonStreaming(response, type)
                ?: return@withContext Result.failure(Exception("Réponse vide ou invalide"))

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
            val type = object : TypeToken<List<Map<String, Any>>>() {}.type
            val rawList: List<Map<String, Any>> = parseJsonStreaming(response, type)
                ?: return@withContext Result.failure(Exception("Réponse vide ou invalide"))

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
     * Récupère les films VOD d'une catégorie (ou de l'ensemble du catalogue)
     */
    suspend fun getVodStreams(
        serverUrl: String,
        username: String,
        password: String,
        categoryId: String? = null,
        limit: Int? = null
    ): Result<List<VodMovie>> = withContext(Dispatchers.IO) {
        val cleanServer = serverUrl.trimEnd('/')
        val url = buildString {
            append("$cleanServer/player_api.php?username=$username&password=$password&action=get_vod_streams")
            if (!categoryId.isNullOrBlank() && categoryId != "Toutes" && categoryId != "all") {
                append("&category_id=$categoryId")
            }
        }

        try {
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val type = object : TypeToken<List<Map<String, Any>>>() {}.type
            val rawList: List<Map<String, Any>> = parseJsonStreaming(response, type)
                ?: return@withContext Result.failure(Exception("Réponse vide ou invalide"))

            val listToProcess = if (limit != null && limit > 0) rawList.take(limit) else rawList

            val movies = listToProcess.mapNotNull { item ->
                val streamId = item["stream_id"]?.toString()?.substringBefore(".") ?: return@mapNotNull null
                val name = item["name"]?.toString() ?: "Film $streamId"
                val poster = item["stream_icon"]?.toString()
                val rating = (item["rating"]?.toString()?.toFloatOrNull()) ?: 0f
                val year = (item["year"] as? Number)?.toInt() ?: item["year"]?.toString()?.toIntOrNull()
                val plot = item["plot"]?.toString()
                val ext = item["container_extension"]?.toString() ?: "mp4"
                val catId = item["category_id"]?.toString() ?: categoryId ?: "movies_all"

                val genreStr = item["genre"]?.toString()
                val genres = genreStr?.split(",", "/", ";")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
                val castStr = item["cast"]?.toString()
                val cast = castStr?.split(",", ";")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
                val director = item["director"]?.toString()
                val backdrops = item["backdrop_path"] as? List<*>
                val backdropUrl = backdrops?.firstOrNull()?.toString() ?: poster
                val duration = (item["duration"] as? Number)?.toInt() ?: item["duration"]?.toString()?.toIntOrNull()

                val isHdr = name.contains("HDR", ignoreCase = true) || name.contains("Dolby Vision", ignoreCase = true)
                val hdrFormat = if (name.contains("Dolby Vision", ignoreCase = true)) "Dolby Vision" else if (isHdr) "HDR10" else null
                val resolution = if (name.contains("4K", ignoreCase = true) || name.contains("UHD", ignoreCase = true)) "4K UHD" else "1080p"
                val streamUrl = buildVodStreamUrl(cleanServer, username, password, streamId, ext)

                VodMovie(
                    id = streamId,
                    title = name,
                    streamUrl = streamUrl,
                    posterUrl = poster,
                    backdropUrl = backdropUrl,
                    rating = rating,
                    releaseYear = year,
                    durationMinutes = duration,
                    plot = plot,
                    genres = genres,
                    cast = cast,
                    director = director,
                    isHdr = isHdr,
                    hdrFormat = hdrFormat,
                    resolution = resolution,
                    videoCodec = if (isHdr) "hevc" else "h264",
                    categoryId = catId,
                    containerExtension = ext
                )
            }

            Result.success(movies)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Récupère les métadonnées complètes d'un film VOD (action=get_vod_info)
     */
    suspend fun getVodInfo(
        serverUrl: String,
        username: String,
        password: String,
        vodId: String
    ): Result<VodMovie> = withContext(Dispatchers.IO) {
        val cleanServer = serverUrl.trimEnd('/')
        val url = "$cleanServer/player_api.php?username=$username&password=$password&action=get_vod_info&vod_id=$vodId"

        try {
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val type = object : TypeToken<Map<String, Any>>() {}.type
            val jsonMap: Map<String, Any> = parseJsonStreaming(response, type)
                ?: return@withContext Result.failure(Exception("Réponse vide ou invalide"))

            @Suppress("UNCHECKED_CAST")
            val info = jsonMap["info"] as? Map<String, Any> ?: emptyMap()
            @Suppress("UNCHECKED_CAST")
            val movieData = jsonMap["movie_data"] as? Map<String, Any> ?: emptyMap()

            val title = (info["name"] ?: movieData["name"])?.toString() ?: "Film $vodId"
            val poster = (info["movie_image"] ?: info["cover_big"])?.toString()
            val plot = (info["plot"] ?: info["description"])?.toString()
            val castStr = (info["cast"] ?: info["actors"])?.toString()
            val castList = castStr?.split(",", ";")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
            val director = info["director"]?.toString()
            val genreStr = info["genre"]?.toString()
            val genreList = genreStr?.split(",", "/", ";")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()

            val releaseDate = (info["release_date"] ?: info["releasedate"])?.toString()
            val year = releaseDate?.take(4)?.toIntOrNull()

            val durationSecs = (info["duration_secs"] as? Number)?.toInt()
                ?: info["duration_secs"]?.toString()?.toIntOrNull()
            val durationMins = if (durationSecs != null && durationSecs > 0) {
                durationSecs / 60
            } else {
                val durStr = info["duration"]?.toString()
                if (durStr != null && durStr.contains(":")) {
                    val parts = durStr.split(":")
                    if (parts.size >= 2) {
                        val h = parts[0].trim().toIntOrNull() ?: 0
                        val m = parts[1].trim().toIntOrNull() ?: 0
                        h * 60 + m
                    } else null
                } else null
            }

            val rating = (info["rating"]?.toString()?.toFloatOrNull())
                ?: (info["rating_5based"]?.toString()?.toFloatOrNull()?.let { it * 2 })
                ?: 0f

            @Suppress("UNCHECKED_CAST")
            val backdrops = info["backdrop_path"] as? List<*>
            val backdropUrl = backdrops?.firstOrNull()?.toString() ?: poster

            val ext = movieData["container_extension"]?.toString()
                ?: info["container_extension"]?.toString()
                ?: "mp4"

            val streamUrl = buildVodStreamUrl(cleanServer, username, password, vodId, ext)

            val isHdr = title.contains("HDR", ignoreCase = true) || title.contains("Dolby Vision", ignoreCase = true)
            val hdrFormat = if (title.contains("Dolby Vision", ignoreCase = true)) "Dolby Vision" else if (isHdr) "HDR10" else null
            val resolution = if (title.contains("4K", ignoreCase = true) || title.contains("UHD", ignoreCase = true)) "4K UHD" else "1080p"

            val movie = VodMovie(
                id = vodId,
                title = title,
                streamUrl = streamUrl,
                posterUrl = poster,
                backdropUrl = backdropUrl,
                rating = rating,
                releaseYear = year,
                durationMinutes = durationMins,
                plot = plot,
                genres = genreList,
                cast = castList,
                director = director,
                isHdr = isHdr,
                hdrFormat = hdrFormat,
                resolution = resolution,
                videoCodec = if (isHdr) "hevc" else "h264",
                containerExtension = ext
            )

            Result.success(movie)
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
     * Récupère les séries (avec support du catalogue complet sans limite artificielle)
     */
    suspend fun getSeriesStreams(
        serverUrl: String,
        username: String,
        password: String,
        categoryId: String? = null,
        limit: Int? = null
    ): Result<List<Series>> = withContext(Dispatchers.IO) {
        val cleanServer = serverUrl.trimEnd('/')
        val url = buildString {
            append("$cleanServer/player_api.php?username=$username&password=$password&action=get_series")
            if (!categoryId.isNullOrBlank() && categoryId != "Toutes" && categoryId != "all") {
                append("&category_id=$categoryId")
            }
        }

        try {
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val type = object : TypeToken<List<Map<String, Any>>>() {}.type
            val rawList: List<Map<String, Any>> = parseJsonStreaming(response, type)
                ?: return@withContext Result.failure(Exception("Réponse vide ou invalide"))

            val listToProcess = if (limit != null && limit > 0) rawList.take(limit) else rawList

            val series = listToProcess.mapNotNull { item ->
                val seriesId = item["series_id"]?.toString()?.substringBefore(".") ?: return@mapNotNull null
                val name = item["name"]?.toString() ?: "Série $seriesId"
                val cover = item["cover"]?.toString()
                val plot = item["plot"]?.toString()
                val rating = (item["rating"]?.toString()?.toFloatOrNull()) ?: 0f
                val year = item["releaseDate"]?.toString()?.toIntOrNull() ?: item["release_date"]?.toString()?.toIntOrNull()
                val catId = item["category_id"]?.toString() ?: categoryId ?: "series_all"

                val genreStr = item["genre"]?.toString()
                val genres = genreStr?.split(",", "/", ";")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
                val castStr = item["cast"]?.toString()
                val cast = castStr?.split(",", ";")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
                val backdrops = item["backdrop_path"] as? List<*>
                val backdropUrl = backdrops?.firstOrNull()?.toString() ?: cover

                Series(
                    id = seriesId,
                    title = name,
                    posterUrl = cover,
                    backdropUrl = backdropUrl,
                    rating = rating,
                    releaseYear = year,
                    plot = plot,
                    genres = genres,
                    cast = cast,
                    categoryId = catId,
                    seasons = emptyList()
                )
            }

            Result.success(series)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Récupère les métadonnées complètes d'une série avec toutes les saisons et tous les épisodes (action=get_series_info)
     */
    suspend fun getSeriesInfo(
        serverUrl: String,
        username: String,
        password: String,
        seriesId: String
    ): Result<Series> = withContext(Dispatchers.IO) {
        val cleanServer = serverUrl.trimEnd('/')
        val url = "$cleanServer/player_api.php?username=$username&password=$password&action=get_series_info&series_id=$seriesId"

        try {
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val type = object : TypeToken<Map<String, Any>>() {}.type
            val jsonMap: Map<String, Any> = parseJsonStreaming(response, type)
                ?: return@withContext Result.failure(Exception("Réponse vide ou invalide"))

            @Suppress("UNCHECKED_CAST")
            val info = jsonMap["info"] as? Map<String, Any> ?: emptyMap()

            // 1. Parser les saisons tolérant (supporte List ou Map d'objets)
            val rawSeasonsList: List<Map<String, Any>> = when (val s = jsonMap["seasons"]) {
                is List<*> -> s.filterIsInstance<Map<String, Any>>()
                is Map<*, *> -> s.values.filterIsInstance<Map<String, Any>>()
                else -> emptyList()
            }

            // 2. Parser les épisodes tolérant (supporte Map<String, List>, Map<String, Map>, ou List à plat)
            val episodesBySeasonKey = mutableMapOf<String, MutableList<Map<String, Any>>>()

            when (val rawEpisodes = jsonMap["episodes"]) {
                is Map<*, *> -> {
                    for ((k, v) in rawEpisodes) {
                        val keyStr = k?.toString() ?: continue
                        val epList = when (v) {
                            is List<*> -> v.filterIsInstance<Map<String, Any>>()
                            is Map<*, *> -> v.values.filterIsInstance<Map<String, Any>>()
                            else -> emptyList()
                        }
                        if (epList.isNotEmpty()) {
                            episodesBySeasonKey.getOrPut(keyStr) { mutableListOf() }.addAll(epList)
                        }
                    }
                }
                is List<*> -> {
                    for (item in rawEpisodes.filterIsInstance<Map<String, Any>>()) {
                        val sNum = (item["season"] as? Number)?.toInt()
                            ?: (item["season_num"] as? Number)?.toInt()
                            ?: item["season"]?.toString()?.filter { it.isDigit() }?.toIntOrNull()
                            ?: 1
                        episodesBySeasonKey.getOrPut(sNum.toString()) { mutableListOf() }.add(item)
                    }
                }
            }

            val title = info["name"]?.toString() ?: "Série $seriesId"
            val poster = info["cover"]?.toString()
            val plot = info["plot"]?.toString()
            val castStr = info["cast"]?.toString()
            val castList = castStr?.split(",", ";")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
            val genreStr = info["genre"]?.toString()
            val genreList = genreStr?.split(",", "/", ";")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
            val rating = (info["rating"]?.toString()?.toFloatOrNull()) ?: 0f
            val releaseDate = (info["releaseDate"] ?: info["release_date"])?.toString()
            val year = releaseDate?.take(4)?.toIntOrNull()

            @Suppress("UNCHECKED_CAST")
            val backdrops = info["backdrop_path"] as? List<*>
            val backdropUrl = backdrops?.firstOrNull()?.toString() ?: poster

            // Map des métadonnées de saison indexée par numéro
            val metaByNumber = mutableMapOf<Int, Map<String, Any>>()
            for (sMeta in rawSeasonsList) {
                val num = (sMeta["season_number"] as? Number)?.toInt()
                    ?: sMeta["season_number"]?.toString()?.filter { it.isDigit() }?.toIntOrNull()
                if (num != null) {
                    metaByNumber[num] = sMeta
                }
            }

            // Normalisation des épisodes par numéro de saison
            val episodesBySeasonNumber = mutableMapOf<Int, MutableList<Map<String, Any>>>()
            for ((key, eps) in episodesBySeasonKey) {
                val num = key.filter { it.isDigit() }.toIntOrNull()
                    ?: (eps.firstOrNull()?.get("season") as? Number)?.toInt()
                    ?: eps.firstOrNull()?.get("season")?.toString()?.filter { it.isDigit() }?.toIntOrNull()
                    ?: 1
                episodesBySeasonNumber.getOrPut(num) { mutableListOf() }.addAll(eps)
            }

            val allSeasonNumbers = (metaByNumber.keys + episodesBySeasonNumber.keys).distinct().sorted()
            val finalSeasonNumbers = if (allSeasonNumbers.isEmpty() && episodesBySeasonKey.isNotEmpty()) listOf(1) else allSeasonNumbers

            val seasons = mutableListOf<Season>()

            for (sNum in finalSeasonNumbers) {
                val sMeta = metaByNumber[sNum]
                val rawName = sMeta?.get("name")?.toString()
                val sName = if (!rawName.isNullOrBlank()) rawName else "Saison $sNum"

                val epListRaw = episodesBySeasonNumber[sNum]
                    ?: episodesBySeasonKey[sNum.toString()]
                    ?: emptyList()

                val episodes = epListRaw.mapNotNull { epItem ->
                    val epId = (epItem["id"] ?: epItem["episode_id"] ?: epItem["stream_id"])
                        ?.toString()?.substringBefore(".") ?: return@mapNotNull null

                    val epNum = (epItem["episode_num"] as? Number)?.toInt()
                        ?: epItem["episode_num"]?.toString()?.filter { it.isDigit() }?.toIntOrNull()
                        ?: (epItem["episode"] as? Number)?.toInt()
                        ?: (epItem["episode"]?.toString()?.filter { it.isDigit() }?.toIntOrNull())
                        ?: 1

                    @Suppress("UNCHECKED_CAST")
                    val epInfo = epItem["info"] as? Map<String, Any> ?: emptyMap()

                    val rawTitle = epItem["title"]?.toString()?.takeIf { it.isNotBlank() }
                        ?: epInfo["title"]?.toString()?.takeIf { it.isNotBlank() }
                        ?: epItem["name"]?.toString()?.takeIf { it.isNotBlank() }
                    val epTitle = rawTitle?.let { maybeDecodeBase64(it) } ?: "Épisode $epNum"

                    val ext = (epItem["container_extension"] ?: epInfo["container_extension"] ?: "mp4")
                        .toString().replace(".", "").ifBlank { "mp4" }

                    val epPlot = (epInfo["plot"] ?: epItem["plot"])?.toString()
                    val epRating = (epInfo["rating"]?.toString()?.toFloatOrNull())
                        ?: (epItem["rating"]?.toString()?.toFloatOrNull())
                        ?: 0f

                    val epDurationSecs = (epInfo["duration_secs"] as? Number)?.toInt()
                        ?: epInfo["duration_secs"]?.toString()?.toIntOrNull()
                        ?: (epItem["duration_secs"] as? Number)?.toInt()
                        ?: epItem["duration_secs"]?.toString()?.toIntOrNull()
                    val epDurationMins = if (epDurationSecs != null && epDurationSecs > 0) epDurationSecs / 60 else null

                    val epThumb = (epInfo["movie_image"] ?: epItem["movie_image"] ?: epInfo["cover"] ?: epItem["cover"])?.toString()

                    val streamUrl = buildSeriesStreamUrl(cleanServer, username, password, epId, ext)

                    Episode(
                        id = epId,
                        seriesId = seriesId,
                        seasonNumber = sNum,
                        episodeNumber = epNum,
                        title = epTitle,
                        streamUrl = streamUrl,
                        containerExtension = ext,
                        thumbnailUrl = epThumb,
                        durationMinutes = epDurationMins,
                        plot = epPlot,
                        rating = epRating
                    )
                }.distinctBy { it.id }.sortedBy { it.episodeNumber }

                if (episodes.isNotEmpty() || sMeta != null) {
                    seasons.add(
                        Season(
                            seasonNumber = sNum,
                            name = sName,
                            episodeCount = if (episodes.isNotEmpty()) episodes.size else (sMeta?.get("episode_count") as? Number)?.toInt() ?: 0,
                            episodes = episodes
                        )
                    )
                }
            }

            val series = Series(
                id = seriesId,
                title = title,
                posterUrl = poster,
                backdropUrl = backdropUrl,
                rating = rating,
                releaseYear = year,
                plot = plot,
                genres = genreList,
                cast = castList,
                seasons = seasons
            )

            Result.success(series)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Récupère l'EPG court d'une chaîne avec décodage Base64, support des dates et fallback get_simple_data_table
     */
    suspend fun getShortEpg(
        serverUrl: String,
        username: String,
        password: String,
        streamId: String
    ): Result<List<EpgProgram>> = withContext(Dispatchers.IO) {
        val cleanServer = serverUrl.trimEnd('/')
        // 1. Essai avec get_short_epg
        val shortEpgResult = executeEpgRequest("$cleanServer/player_api.php?username=$username&password=$password&action=get_short_epg&stream_id=$streamId", streamId)
        if (shortEpgResult.isSuccess && shortEpgResult.getOrNull()?.isNotEmpty() == true) {
            return@withContext shortEpgResult
        }

        // 2. Repli vers get_simple_data_table si get_short_epg est vide
        val simpleTableResult = executeEpgRequest("$cleanServer/player_api.php?username=$username&password=$password&action=get_simple_data_table&stream_id=$streamId", streamId)
        if (simpleTableResult.isSuccess && simpleTableResult.getOrNull()?.isNotEmpty() == true) {
            return@withContext simpleTableResult
        }

        shortEpgResult
    }

    private fun executeEpgRequest(url: String, streamId: String): Result<List<EpgProgram>> {
        return try {
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val type = object : TypeToken<Map<String, Any>>() {}.type
            val jsonMap: Map<String, Any> = parseJsonStreaming(response, type)
                ?: return Result.success(emptyList())

            @Suppress("UNCHECKED_CAST")
            val listings = jsonMap["epg_listings"] as? List<Map<String, Any>> ?: return Result.success(emptyList())

            val now = System.currentTimeMillis()
            val programs = listings.mapNotNull { item ->
                val rawTitle = item["title"]?.toString() ?: return@mapNotNull null
                val rawDesc = item["description"]?.toString()
                var startMs = parseEpgEpochMs(item, "start_timestamp", "start")
                var stopMs = parseEpgEpochMs(item, "stop_timestamp", "end").takeIf { it > 0 }
                    ?: parseEpgEpochMs(item, "stop_timestamp", "stop")
                val isNowPlaying = (item["now_playing"] as? Number)?.toInt() == 1 || item["now_playing"]?.toString() == "1"

                if (startMs == 0L && stopMs == 0L && isNowPlaying) {
                    startMs = now - (15 * 60 * 1000L)
                    stopMs = now + (45 * 60 * 1000L)
                }

                val hasArchive = (item["has_archive"] as? Number)?.toInt() ?: 0
                val icon = item["icon"]?.toString()
                    ?: item["image"]?.toString()
                    ?: item["cover"]?.toString()
                    ?: item["poster"]?.toString()

                val title = maybeDecodeBase64(rawTitle)
                val desc = rawDesc?.let { maybeDecodeBase64(it) }

                EpgProgram(
                    id = item["id"]?.toString() ?: UUID.randomUUID().toString(),
                    channelId = streamId,
                    title = title,
                    description = desc,
                    startEpochMs = startMs,
                    stopEpochMs = stopMs,
                    iconUrl = icon,
                    hasCatchup = hasArchive > 0
                )
            }

            Result.success(programs)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseEpgEpochMs(item: Map<String, Any>, tsKey: String, dateKey: String): Long {
        val rawTs = item[tsKey]?.toString()?.trim()?.toLongOrNull()
        if (rawTs != null && rawTs > 0) {
            return if (rawTs > 10_000_000_000L) rawTs else rawTs * 1000L
        }
        val dateStr = item[dateKey]?.toString()?.trim()
        if (!dateStr.isNullOrBlank()) {
            val formats = listOf(
                java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US),
                java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US),
                java.text.SimpleDateFormat("yyyyMMddHHmmss", java.util.Locale.US)
            )
            for (format in formats) {
                try {
                    val d = format.parse(dateStr)
                    if (d != null) return d.time
                } catch (_: Exception) {}
            }
        }
        return 0L
    }

    private fun maybeDecodeBase64(input: String): String {
        val trimmed = input.trim()
        if (trimmed.isBlank() || trimmed.contains(" ")) {
            return trimmed
        }
        return try {
            val decodedBytes = java.util.Base64.getDecoder().decode(trimmed)
            val decodedStr = String(decodedBytes, Charsets.UTF_8).trim()
            val isReadable = decodedStr.isNotEmpty() && decodedStr.none { 
                Character.isISOControl(it) && it != '\n' && it != '\r' && it != '\t' 
            }
            if (isReadable) decodedStr else trimmed
        } catch (_: Exception) {
            trimmed
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
            val type = object : TypeToken<List<Map<String, Any>>>() {}.type
            val rawList: List<Map<String, Any>> = parseJsonStreaming(response, type)
                ?: return Result.failure(Exception("Réponse vide ou invalide"))

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
