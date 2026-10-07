package io.noostv.core.storage

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.noostv.core.security.CryptoManager
import io.noostv.data.model.UserProfile
import java.util.UUID

/**
 * Gestionnaire de persistance sécurisée des identifiants IPTV (Xtream Codes API),
 * de session utilisateur et du multi-profils (favoris et filtres de catégories isolés par profil).
 * Tous les identifiants sensibles sont chiffrés avec AES-256-GCM via l'Android KeyStore.
 */
class SessionManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("noostv_session", Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_IS_PREMIUM = "is_premium"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"
        private const val KEY_PLAYLIST_NAME = "playlist_name"
        private const val KEY_UPDATE_CHANNEL = "update_channel"
        private const val KEY_APP_LANGUAGE = "app_language"
        private const val KEY_GITHUB_TOKEN = "github_token"
        private val DEFAULT_TOKEN: String
            get() = runCatching { io.noostv.BuildConfig.GITHUB_TOKEN }.getOrDefault("")

        private const val KEY_PROFILES_JSON = "profiles_json"
        private const val KEY_ACTIVE_PROFILE_ID = "active_profile_id"
    }

    var appLanguage: String
        get() = prefs.getString(KEY_APP_LANGUAGE, "fr") ?: "fr"
        set(value) = prefs.edit().putString(KEY_APP_LANGUAGE, value).apply()

    var updateChannel: String
        get() = prefs.getString(KEY_UPDATE_CHANNEL, "stable") ?: "stable"
        set(value) = prefs.edit().putString(KEY_UPDATE_CHANNEL, value).apply()

    var githubToken: String
        get() {
            val raw = prefs.getString(KEY_GITHUB_TOKEN, null)
            if (raw != null) {
                val decrypted = CryptoManager.decrypt(raw)
                if (decrypted.isNotBlank()) return decrypted
            }
            return DEFAULT_TOKEN
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

    // ==================== GESTION MULTI-PROFILS ====================

    fun getProfiles(): List<UserProfile> {
        val json = prefs.getString(KEY_PROFILES_JSON, null)
        if (json.isNullOrBlank()) {
            // Migration rétrocompatible : créer le profil Principal avec les favoris existants
            val legacyChannels = prefs.getStringSet("favorite_channel_ids", emptySet()) ?: emptySet()
            val legacyMovies = prefs.getStringSet("favorite_movie_ids", emptySet()) ?: emptySet()
            val legacySeries = prefs.getStringSet("favorite_series_ids", emptySet()) ?: emptySet()

            val defaultProf = UserProfile(
                id = "profile_default",
                name = username.takeIf { it.isNotBlank() } ?: "Principal",
                avatarColorHex = 0xFF3888FF,
                avatarIcon = "Person",
                favoriteChannelIds = legacyChannels,
                favoriteMovieIds = legacyMovies,
                favoriteSeriesIds = legacySeries
            )
            val list = listOf(defaultProf)
            saveProfilesList(list)
            setActiveProfileId("profile_default")
            return list
        }
        return try {
            val type = object : TypeToken<List<UserProfile>>() {}.type
            gson.fromJson<List<UserProfile>>(json, type) ?: listOf(UserProfile.DEFAULT_PROFILE)
        } catch (e: Exception) {
            listOf(UserProfile.DEFAULT_PROFILE)
        }
    }

    private fun saveProfilesList(list: List<UserProfile>) {
        val json = gson.toJson(list)
        prefs.edit().putString(KEY_PROFILES_JSON, json).apply()
    }

    fun getActiveProfileId(): String {
        return prefs.getString(KEY_ACTIVE_PROFILE_ID, "profile_default") ?: "profile_default"
    }

    fun setActiveProfileId(id: String) {
        prefs.edit().putString(KEY_ACTIVE_PROFILE_ID, id).apply()
    }

    fun getActiveProfile(): UserProfile {
        val list = getProfiles()
        val activeId = getActiveProfileId()
        return list.firstOrNull { it.id == activeId } ?: list.firstOrNull() ?: UserProfile.DEFAULT_PROFILE
    }

    fun createProfile(name: String, colorHex: Long = 0xFF3888FF, icon: String = "Person"): UserProfile {
        val newProfile = UserProfile(
            id = "profile_" + UUID.randomUUID().toString().take(8),
            name = name.ifBlank { "Nouveau Profil" },
            avatarColorHex = colorHex,
            avatarIcon = icon
        )
        val current = getProfiles().toMutableList()
        current.add(newProfile)
        saveProfilesList(current)
        setActiveProfileId(newProfile.id)
        return newProfile
    }

    fun updateProfile(id: String, name: String, colorHex: Long, icon: String): UserProfile? {
        val current = getProfiles().toMutableList()
        val index = current.indexOfFirst { it.id == id }
        if (index != -1) {
            val old = current[index]
            val updated = old.copy(
                name = name.ifBlank { old.name },
                avatarColorHex = colorHex,
                avatarIcon = icon
            )
            current[index] = updated
            saveProfilesList(current)
            return updated
        }
        return null
    }

    fun deleteProfile(id: String): Boolean {
        val current = getProfiles().toMutableList()
        if (current.size <= 1) return false // Ne pas supprimer le dernier profil
        val removed = current.removeAll { it.id == id }
        if (removed) {
            saveProfilesList(current)
            if (getActiveProfileId() == id) {
                setActiveProfileId(current.first().id)
            }
        }
        return removed
    }

    fun updateActiveProfile(transform: (UserProfile) -> UserProfile) {
        val current = getProfiles().toMutableList()
        val activeId = getActiveProfileId()
        val index = current.indexOfFirst { it.id == activeId }
        if (index != -1) {
            current[index] = transform(current[index])
            saveProfilesList(current)
        }
    }

    // ==================== FAVORIS PAR PROFIL ====================

    fun getFavoriteChannelIds(): Set<String> = getActiveProfile().favoriteChannelIds

    fun toggleFavoriteChannel(id: String): Boolean {
        var isFav = false
        updateActiveProfile { prof ->
            val set = prof.favoriteChannelIds.toMutableSet()
            if (set.contains(id)) {
                set.remove(id)
                isFav = false
            } else {
                set.add(id)
                isFav = true
            }
            prof.copy(favoriteChannelIds = set)
        }
        return isFav
    }

    fun isFavoriteChannel(id: String): Boolean = getFavoriteChannelIds().contains(id)

    fun getFavoriteMovieIds(): Set<String> = getActiveProfile().favoriteMovieIds

    fun toggleFavoriteMovie(id: String): Boolean {
        var isFav = false
        updateActiveProfile { prof ->
            val set = prof.favoriteMovieIds.toMutableSet()
            if (set.contains(id)) {
                set.remove(id)
                isFav = false
            } else {
                set.add(id)
                isFav = true
            }
            prof.copy(favoriteMovieIds = set)
        }
        return isFav
    }

    fun isFavoriteMovie(id: String): Boolean = getFavoriteMovieIds().contains(id)

    fun getFavoriteSeriesIds(): Set<String> = getActiveProfile().favoriteSeriesIds

    fun toggleFavoriteSeries(id: String): Boolean {
        var isFav = false
        updateActiveProfile { prof ->
            val set = prof.favoriteSeriesIds.toMutableSet()
            if (set.contains(id)) {
                set.remove(id)
                isFav = false
            } else {
                set.add(id)
                isFav = true
            }
            prof.copy(favoriteSeriesIds = set)
        }
        return isFav
    }

    fun isFavoriteSeries(id: String): Boolean = getFavoriteSeriesIds().contains(id)

    // ==================== FILTRES DE CATÉGORIES PAR PROFIL ====================

    fun getHiddenVodCategoryIds(): Set<String> = getActiveProfile().hiddenVodCategoryIds

    fun isVodCategoryVisible(categoryId: String): Boolean = !getHiddenVodCategoryIds().contains(categoryId)

    fun toggleVodCategoryVisibility(categoryId: String): Boolean {
        var isNowVisible = false
        updateActiveProfile { prof ->
            val set = prof.hiddenVodCategoryIds.toMutableSet()
            if (set.contains(categoryId)) {
                set.remove(categoryId)
                isNowVisible = true
            } else {
                set.add(categoryId)
                isNowVisible = false
            }
            prof.copy(hiddenVodCategoryIds = set)
        }
        return isNowVisible
    }

    fun setVodCategoryVisibility(categoryId: String, isVisible: Boolean) {
        updateActiveProfile { prof ->
            val set = prof.hiddenVodCategoryIds.toMutableSet()
            if (isVisible) set.remove(categoryId) else set.add(categoryId)
            prof.copy(hiddenVodCategoryIds = set)
        }
    }

    fun showAllVodCategories() {
        updateActiveProfile { prof ->
            prof.copy(hiddenVodCategoryIds = emptySet())
        }
    }

    fun getHiddenSeriesCategoryIds(): Set<String> = getActiveProfile().hiddenSeriesCategoryIds

    fun isSeriesCategoryVisible(categoryId: String): Boolean = !getHiddenSeriesCategoryIds().contains(categoryId)

    fun toggleSeriesCategoryVisibility(categoryId: String): Boolean {
        var isNowVisible = false
        updateActiveProfile { prof ->
            val set = prof.hiddenSeriesCategoryIds.toMutableSet()
            if (set.contains(categoryId)) {
                set.remove(categoryId)
                isNowVisible = true
            } else {
                set.add(categoryId)
                isNowVisible = false
            }
            prof.copy(hiddenSeriesCategoryIds = set)
        }
        return isNowVisible
    }

    fun setSeriesCategoryVisibility(categoryId: String, isVisible: Boolean) {
        updateActiveProfile { prof ->
            val set = prof.hiddenSeriesCategoryIds.toMutableSet()
            if (isVisible) set.remove(categoryId) else set.add(categoryId)
            prof.copy(hiddenSeriesCategoryIds = set)
        }
    }

    fun showAllSeriesCategories() {
        updateActiveProfile { prof ->
            prof.copy(hiddenSeriesCategoryIds = emptySet())
        }
    }

    fun logout() {
        prefs.edit().clear().apply()
    }
}
