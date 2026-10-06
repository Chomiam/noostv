package io.noostv.core.storage

import android.content.Context
import android.content.SharedPreferences

/**
 * Gestionnaire de persistance des identifiants IPTV (Xtream Codes API) et de session utilisateur.
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
    }

    var isPremium: Boolean
        get() = prefs.getBoolean(KEY_IS_PREMIUM, false)
        set(value) = prefs.edit().putBoolean(KEY_IS_PREMIUM, value).apply()

    var isLoggedIn: Boolean
        get() = prefs.getBoolean(KEY_IS_LOGGED_IN, false)
        set(value) = prefs.edit().putBoolean(KEY_IS_LOGGED_IN, value).apply()

    var serverUrl: String
        get() = prefs.getString(KEY_SERVER_URL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_SERVER_URL, value).apply()

    var username: String
        get() = prefs.getString(KEY_USERNAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_USERNAME, value).apply()

    var password: String
        get() = prefs.getString(KEY_PASSWORD, "") ?: ""
        set(value) = prefs.edit().putString(KEY_PASSWORD, value).apply()

    var playlistName: String
        get() = prefs.getString(KEY_PLAYLIST_NAME, "Mon Abonnement IPTV") ?: "Mon Abonnement IPTV"
        set(value) = prefs.edit().putString(KEY_PLAYLIST_NAME, value).apply()

    fun saveCredentials(server: String, user: String, pass: String, name: String = "Mon Abonnement IPTV") {
        prefs.edit()
            .putBoolean(KEY_IS_LOGGED_IN, true)
            .putString(KEY_SERVER_URL, server)
            .putString(KEY_USERNAME, user)
            .putString(KEY_PASSWORD, pass)
            .putString(KEY_PLAYLIST_NAME, name)
            .apply()
    }

    fun logout() {
        prefs.edit().clear().apply()
    }
}
