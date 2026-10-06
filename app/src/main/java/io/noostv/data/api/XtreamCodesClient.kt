package io.noostv.data.api

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.noostv.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
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
 * Client universel pour l'API Xtream Codes v2 / IPTV SaaS.
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
     * Récupère les catégories de Séries
     */
    suspend fun getSeriesCategories(
        serverUrl: String,
        username: String,
        password: String
    ): Result<List<Category>> = withContext(Dispatchers.IO) {
        fetchCategories(serverUrl, username, password, "get_series_categories", CategoryType.SERIES)
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
