package io.noostv.core.storage

import android.content.Context
import android.content.SharedPreferences
import io.noostv.core.security.CryptoManager

/**
 * Gestionnaire de persistance sécurisée des identifiants IPTV (Xtream Codes API) et de session utilisateur.
 * Tous les identifiants sensibles sont chiffrés avec AES-256-GCM via l'Android KeyStore.
 */
class SessionManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("noostv_session", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_IS_PREMIUM = "is_premium"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"
        private const val KEY_PLAYLIST_NAME = "playlist_name"
        private const val KEY_UPDATE_CHANNEL = "update_channel"
        private const val KEY_GITHUB_TOKEN = "github_token"
        private const val DEFAULT_TOKEN = ""
    }

    var updateChannel: String
        get() = prefs.getString(KEY_UPDATE_CHANNEL, "stable") ?: "stable"
        set(value) = prefs.edit().putString(KEY_UPDATE_CHANNEL, value).apply()

    var githubToken: String
        get() {
            val raw = prefs.getString(KEY_GITHUB_TOKEN, DEFAULT_TOKEN) ?: DEFAULT_TOKEN
            return CryptoManager.decrypt(raw)
        }
        set(value) {
            val enc = CryptoManager.encrypt(value)
            prefs.edit().putString(KEY_GITHUB_TOKEN, enc).apply()
        }

    var isPremium: Boolean
        get() = prefs.getBoolean(KEY_IS_PREMIUM, false)
        set(value) = prefs.edit().putBoolean(KEY_IS_PREMIUM, value).apply()

    var isLoggedIn: Boolean
        get() = prefs.getBoolean(KEY_IS_LOGGED_IN, false)
        set(value) = prefs.edit().putBoolean(KEY_IS_LOGGED_IN, value).apply()

    var serverUrl: String
        get() {
            val raw = prefs.getString(KEY_SERVER_URL, "") ?: ""
            return CryptoManager.decrypt(raw)
        }
        set(value) {
            val enc = CryptoManager.encrypt(value)
            prefs.edit().putString(KEY_SERVER_URL, enc).apply()
        }

    var username: String
        get() {
            val raw = prefs.getString(KEY_USERNAME, "") ?: ""
            return CryptoManager.decrypt(raw)
        }
        set(value) {
            val enc = CryptoManager.encrypt(value)
            prefs.edit().putString(KEY_USERNAME, enc).apply()
        }

    var password: String
        get() {
            val raw = prefs.getString(KEY_PASSWORD, "") ?: ""
            return CryptoManager.decrypt(raw)
        }
        set(value) {
            val enc = CryptoManager.encrypt(value)
            prefs.edit().putString(KEY_PASSWORD, enc).apply()
        }

    fun getMaskedServerUrl(): String = CryptoManager.maskUrl(serverUrl)

    fun getMaskedUsername(): String = CryptoManager.maskIdentifier(username)

    var playlistName: String
        get() = prefs.getString(KEY_PLAYLIST_NAME, "Mon Abonnement IPTV") ?: "Mon Abonnement IPTV"
        set(value) = prefs.edit().putString(KEY_PLAYLIST_NAME, value).apply()

    fun saveCredentials(server: String, user: String, pass: String, name: String = "Mon Abonnement IPTV") {
        prefs.edit()
            .putBoolean(KEY_IS_LOGGED_IN, true)
            .putString(KEY_SERVER_URL, CryptoManager.encrypt(server))
            .putString(KEY_USERNAME, CryptoManager.encrypt(user))
            .putString(KEY_PASSWORD, CryptoManager.encrypt(pass))
            .putString(KEY_PLAYLIST_NAME, name)
            .apply()
    }

    fun getFavoriteChannelIds(): Set<String> = prefs.getStringSet("favorite_channel_ids", emptySet()) ?: emptySet()

    fun toggleFavoriteChannel(id: String): Boolean {
        val current = getFavoriteChannelIds().toMutableSet()
        val isFav = if (current.contains(id)) {
            current.remove(id)
            false
        } else {
            current.add(id)
            true
        }
        prefs.edit().putStringSet("favorite_channel_ids", current).apply()
        return isFav
    }

    fun isFavoriteChannel(id: String): Boolean = getFavoriteChannelIds().contains(id)

    fun getFavoriteMovieIds(): Set<String> = prefs.getStringSet("favorite_movie_ids", emptySet()) ?: emptySet()

    fun toggleFavoriteMovie(id: String): Boolean {
        val current = getFavoriteMovieIds().toMutableSet()
        val isFav = if (current.contains(id)) {
            current.remove(id)
            false
        } else {
            current.add(id)
            true
        }
        prefs.edit().putStringSet("favorite_movie_ids", current).apply()
        return isFav
    }

    fun isFavoriteMovie(id: String): Boolean = getFavoriteMovieIds().contains(id)

    fun getFavoriteSeriesIds(): Set<String> = prefs.getStringSet("favorite_series_ids", emptySet()) ?: emptySet()

    fun toggleFavoriteSeries(id: String): Boolean {
        val current = getFavoriteSeriesIds().toMutableSet()
        val isFav = if (current.contains(id)) {
            current.remove(id)
            false
        } else {
            current.add(id)
            true
        }
        prefs.edit().putStringSet("favorite_series_ids", current).apply()
        return isFav
    }

    fun isFavoriteSeries(id: String): Boolean = getFavoriteSeriesIds().contains(id)

    fun logout() {
        prefs.edit().clear().apply()
    }
}
