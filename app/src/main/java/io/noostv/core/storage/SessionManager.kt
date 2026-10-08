package io.noostv.core.storage

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.noostv.core.security.CryptoManager
import io.noostv.data.model.PlaybackResumePoint
import io.noostv.data.model.SavedAccount
import io.noostv.data.model.UserProfile
import java.io.File
import java.util.UUID

/**
 * Gestionnaire de persistance sécurisée des identifiants IPTV (Xtream Codes API),
 * de session utilisateur et du multi-profils (favoris et filtres de catégories isolés par profil).
 * Tous les identifiants sensibles sont chiffrés avec AES-256-GCM via l'Android KeyStore.
 */
class SessionManager(context: Context) {

    private val appContext: Context = context.applicationContext
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
        private const val KEY_SAVED_ACCOUNTS_ENCRYPTED = "saved_accounts_encrypted"
        private const val KEY_SOUND_EFFECTS_ENABLED = "sound_effects_enabled"
        private const val KEY_SOUND_EFFECTS_VOLUME = "sound_effects_volume"
        private const val DEFAULT_TOKEN: String = ""

        private const val KEY_PROFILES_JSON = "profiles_json"
        private const val KEY_ACTIVE_PROFILE_ID = "active_profile_id"
    }

    var isSoundEffectsEnabled: Boolean
        get() = prefs.getBoolean(KEY_SOUND_EFFECTS_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_SOUND_EFFECTS_ENABLED, value).apply()

    var soundEffectsVolume: Float
        get() = prefs.getFloat(KEY_SOUND_EFFECTS_VOLUME, 0.35f)
        set(value) = prefs.edit().putFloat(KEY_SOUND_EFFECTS_VOLUME, value).apply()

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

        // Sauvegarde chiffrée automatique dans l'historique local pour connexion rapide
        saveAccountToHistory(server, user, pass, name)
    }

    // ==================== GESTION MULTI-PROFILS & PERSISTANCE ULTRA-FIABLE ====================

    private val profilesBackupFile: File
        get() = File(appContext.filesDir, "noostv_profiles_backup.json")

    fun getProfiles(): List<UserProfile> {
        var json = prefs.getString(KEY_PROFILES_JSON, null)

        // Si SharedPreferences est vide, restaurer depuis le fichier de sauvegarde miroir
        if (json.isNullOrBlank() && profilesBackupFile.exists()) {
            try {
                val backupJson = profilesBackupFile.readText()
                if (backupJson.isNotBlank()) {
                    json = backupJson
                    prefs.edit().putString(KEY_PROFILES_JSON, backupJson).commit()
                }
            } catch (e: Exception) {
                // Ignore backup read failure
            }
        }

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

        val type = object : TypeToken<List<UserProfile>>() {}.type
        val parsed: List<UserProfile>? = try {
            gson.fromJson<List<UserProfile>>(json, type)
        } catch (e: Exception) {
            null
        }

        if (!parsed.isNullOrEmpty()) {
            val sanitized = parsed.map { prof ->
                prof.copy(
                    favoriteChannelIds = prof.favoriteChannelIds ?: emptySet(),
                    favoriteMovieIds = prof.favoriteMovieIds ?: emptySet(),
                    favoriteSeriesIds = prof.favoriteSeriesIds ?: emptySet(),
                    hiddenVodCategoryIds = prof.hiddenVodCategoryIds ?: emptySet(),
                    hiddenSeriesCategoryIds = prof.hiddenSeriesCategoryIds ?: emptySet(),
                    hiddenLiveCategoryIds = prof.hiddenLiveCategoryIds ?: emptySet()
                )
            }
            // S'assurer que le fichier miroir est toujours créé sur le disque
            if (!profilesBackupFile.exists()) {
                try {
                    profilesBackupFile.writeText(json)
                } catch (ignored: Exception) {}
            }
            return sanitized
        }

        // Tenter la sauvegarde miroir si le JSON de prefs était altéré
        if (profilesBackupFile.exists()) {
            try {
                val backupJson = profilesBackupFile.readText()
                val backupList: List<UserProfile>? = gson.fromJson(backupJson, type)
                if (!backupList.isNullOrEmpty()) {
                    val sanitizedBackup = backupList.map { prof ->
                        prof.copy(
                            favoriteChannelIds = prof.favoriteChannelIds ?: emptySet(),
                            favoriteMovieIds = prof.favoriteMovieIds ?: emptySet(),
                            favoriteSeriesIds = prof.favoriteSeriesIds ?: emptySet(),
                            hiddenVodCategoryIds = prof.hiddenVodCategoryIds ?: emptySet(),
                            hiddenSeriesCategoryIds = prof.hiddenSeriesCategoryIds ?: emptySet(),
                            hiddenLiveCategoryIds = prof.hiddenLiveCategoryIds ?: emptySet()
                        )
                    }
                    prefs.edit().putString(KEY_PROFILES_JSON, backupJson).commit()
                    return sanitizedBackup
                }
            } catch (ignored: Exception) {}
        }

        val fallback = listOf(UserProfile.DEFAULT_PROFILE)
        saveProfilesList(fallback)
        return fallback
    }

    private fun saveProfilesList(list: List<UserProfile>) {
        val json = gson.toJson(list)
        // 1. Commit synchrone immédiat dans SharedPreferences (résiste aux fermetures brutales de l'app)
        prefs.edit().putString(KEY_PROFILES_JSON, json).commit()

        // 2. Écriture atomique sur fichier interne privé (survit aux mises à jour APK et nettoyages de SharedPreferences)
        try {
            val tempFile = File(appContext.filesDir, "noostv_profiles_backup.json.tmp")
            tempFile.writeText(json)
            if (tempFile.exists()) {
                tempFile.renameTo(profilesBackupFile)
            }
        } catch (e: Exception) {
            // Ne bloque jamais l'application si l'écriture échoue
        }
    }

    fun getActiveProfileId(): String {
        return prefs.getString(KEY_ACTIVE_PROFILE_ID, "profile_default") ?: "profile_default"
    }

    fun setActiveProfileId(id: String) {
        prefs.edit().putString(KEY_ACTIVE_PROFILE_ID, id).commit()
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
        var index = current.indexOfFirst { it.id == activeId }
        if (index == -1 && current.isNotEmpty()) {
            index = 0
            setActiveProfileId(current[0].id)
        }
        if (index != -1) {
            current[index] = transform(current[index])
            saveProfilesList(current)
        }
    }

    // ==================== FAVORIS PAR PROFIL ====================

    fun getFavoriteChannelIds(): Set<String> = getActiveProfile().favoriteChannelIds ?: emptySet()

    fun toggleFavoriteChannel(id: String): Boolean {
        var isFav = false
        updateActiveProfile { prof ->
            val set = (prof.favoriteChannelIds ?: emptySet()).toMutableSet()
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

    fun getFavoriteMovieIds(): Set<String> = getActiveProfile().favoriteMovieIds ?: emptySet()

    fun toggleFavoriteMovie(id: String): Boolean {
        var isFav = false
        updateActiveProfile { prof ->
            val set = (prof.favoriteMovieIds ?: emptySet()).toMutableSet()
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

    fun getFavoriteSeriesIds(): Set<String> = getActiveProfile().favoriteSeriesIds ?: emptySet()

    fun toggleFavoriteSeries(id: String): Boolean {
        var isFav = false
        updateActiveProfile { prof ->
            val set = (prof.favoriteSeriesIds ?: emptySet()).toMutableSet()
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

    fun getHiddenVodCategoryIds(): Set<String> = getActiveProfile().hiddenVodCategoryIds ?: emptySet()

    fun isVodCategoryVisible(categoryId: String): Boolean = !getHiddenVodCategoryIds().contains(categoryId)

    fun toggleVodCategoryVisibility(categoryId: String): Boolean {
        var isNowVisible = false
        updateActiveProfile { prof ->
            val set = (prof.hiddenVodCategoryIds ?: emptySet()).toMutableSet()
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
            val set = (prof.hiddenVodCategoryIds ?: emptySet()).toMutableSet()
            if (isVisible) set.remove(categoryId) else set.add(categoryId)
            prof.copy(hiddenVodCategoryIds = set)
        }
    }

    fun showAllVodCategories() {
        updateActiveProfile { prof ->
            prof.copy(hiddenVodCategoryIds = emptySet())
        }
    }

    fun getHiddenSeriesCategoryIds(): Set<String> = getActiveProfile().hiddenSeriesCategoryIds ?: emptySet()

    fun isSeriesCategoryVisible(categoryId: String): Boolean = !getHiddenSeriesCategoryIds().contains(categoryId)

    fun toggleSeriesCategoryVisibility(categoryId: String): Boolean {
        var isNowVisible = false
        updateActiveProfile { prof ->
            val set = (prof.hiddenSeriesCategoryIds ?: emptySet()).toMutableSet()
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
            val set = (prof.hiddenSeriesCategoryIds ?: emptySet()).toMutableSet()
            if (isVisible) set.remove(categoryId) else set.add(categoryId)
            prof.copy(hiddenSeriesCategoryIds = set)
        }
    }

    fun showAllSeriesCategories() {
        updateActiveProfile { prof ->
            prof.copy(hiddenSeriesCategoryIds = emptySet())
        }
    }

    fun getHiddenLiveCategoryIds(): Set<String> = getActiveProfile().hiddenLiveCategoryIds ?: emptySet()

    fun isLiveCategoryVisible(categoryId: String): Boolean = !getHiddenLiveCategoryIds().contains(categoryId)

    fun toggleLiveCategoryVisibility(categoryId: String): Boolean {
        var isNowVisible = false
        updateActiveProfile { prof ->
            val set = (prof.hiddenLiveCategoryIds ?: emptySet()).toMutableSet()
            if (set.contains(categoryId)) {
                set.remove(categoryId)
                isNowVisible = true
            } else {
                set.add(categoryId)
                isNowVisible = false
            }
            prof.copy(hiddenLiveCategoryIds = set)
        }
        return isNowVisible
    }

    fun setLiveCategoryVisibility(categoryId: String, isVisible: Boolean) {
        updateActiveProfile { prof ->
            val set = (prof.hiddenLiveCategoryIds ?: emptySet()).toMutableSet()
            if (isVisible) set.remove(categoryId) else set.add(categoryId)
            prof.copy(hiddenLiveCategoryIds = set)
        }
    }

    fun showAllLiveCategories() {
        updateActiveProfile { prof ->
            prof.copy(hiddenLiveCategoryIds = emptySet())
        }
    }

    // ==================== HISTORIQUE LOCAL CHIFFRÉ DES IDENTIFIANTS ====================

    private data class StoredEncryptedAccount(
        val id: String,
        val encServerUrl: String,
        val encUsername: String,
        val encPassword: String,
        val playlistName: String,
        val lastUsedTimestamp: Long
    )

    fun getSavedAccounts(): List<SavedAccount> {
        val encryptedBlob = prefs.getString(KEY_SAVED_ACCOUNTS_ENCRYPTED, null)
        if (encryptedBlob.isNullOrBlank()) {
            val s = serverUrl
            val u = username
            val p = password
            if (s.isNotBlank() && u.isNotBlank() && p.isNotBlank()) {
                val legacyAccount = SavedAccount(
                    serverUrl = s,
                    username = u,
                    password = p,
                    playlistName = playlistName
                )
                saveAccountsList(listOf(legacyAccount))
                return listOf(legacyAccount)
            }
            return emptyList()
        }

        val rawJson = CryptoManager.decrypt(encryptedBlob)
        if (rawJson.isBlank()) return emptyList()

        return try {
            val type = object : TypeToken<List<StoredEncryptedAccount>>() {}.type
            val storedList: List<StoredEncryptedAccount> = gson.fromJson(rawJson, type) ?: emptyList()
            storedList.map { stored ->
                SavedAccount(
                    id = stored.id,
                    serverUrl = CryptoManager.decrypt(stored.encServerUrl),
                    username = CryptoManager.decrypt(stored.encUsername),
                    password = CryptoManager.decrypt(stored.encPassword),
                    playlistName = stored.playlistName,
                    lastUsedTimestamp = stored.lastUsedTimestamp
                )
            }.sortedByDescending { it.lastUsedTimestamp }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveAccountsList(list: List<SavedAccount>) {
        val storedList = list.map { acc ->
            StoredEncryptedAccount(
                id = acc.id,
                encServerUrl = CryptoManager.encrypt(acc.serverUrl),
                encUsername = CryptoManager.encrypt(acc.username),
                encPassword = CryptoManager.encrypt(acc.password),
                playlistName = acc.playlistName,
                lastUsedTimestamp = acc.lastUsedTimestamp
            )
        }
        val rawJson = gson.toJson(storedList)
        val encryptedBlob = CryptoManager.encrypt(rawJson)
        prefs.edit().putString(KEY_SAVED_ACCOUNTS_ENCRYPTED, encryptedBlob).apply()
    }

    fun saveAccountToHistory(server: String, user: String, pass: String, name: String = "Mon Abonnement IPTV") {
        val cleanServer = server.trim().trimEnd('/')
        val cleanUser = user.trim()
        val cleanPass = pass.trim()
        if (cleanServer.isBlank() || cleanUser.isBlank() || cleanPass.isBlank()) return

        val currentAccounts = getSavedAccounts().toMutableList()
        val existingIndex = currentAccounts.indexOfFirst {
            it.serverUrl.trim().trimEnd('/').equals(cleanServer, ignoreCase = true) &&
            it.username.trim().equals(cleanUser, ignoreCase = true)
        }

        if (existingIndex != -1) {
            val existing = currentAccounts[existingIndex]
            currentAccounts[existingIndex] = existing.copy(
                serverUrl = cleanServer,
                username = cleanUser,
                password = cleanPass,
                playlistName = name.ifBlank { existing.playlistName },
                lastUsedTimestamp = System.currentTimeMillis()
            )
        } else {
            currentAccounts.add(
                0,
                SavedAccount(
                    serverUrl = cleanServer,
                    username = cleanUser,
                    password = cleanPass,
                    playlistName = name,
                    lastUsedTimestamp = System.currentTimeMillis()
                )
            )
        }

        saveAccountsList(currentAccounts)
    }

    fun removeAccountFromHistory(accountId: String): Boolean {
        val currentAccounts = getSavedAccounts().toMutableList()
        val removed = currentAccounts.removeAll { it.id == accountId }
        if (removed) {
            saveAccountsList(currentAccounts)
        }
        return removed
    }

    fun clearSavedAccounts() {
        prefs.edit().remove(KEY_SAVED_ACCOUNTS_ENCRYPTED).apply()
    }

    // ==================== REPRISE DE LECTURE (PLAYBACK RESUME) ====================

    private val KEY_PLAYBACK_RESUME_PREFIX = "playback_resume_"
    private val playbackResumeBackupFile: File
        get() = File(appContext.filesDir, "noostv_playback_resume.json")

    private fun getResumeStorageKey(profileId: String): String = "$KEY_PLAYBACK_RESUME_PREFIX$profileId"

    /**
     * Récupère le point de reprise de lecture pour un film ou un épisode donné,
     * selon le profil utilisateur actif.
     */
    fun getPlaybackResume(contentId: String): PlaybackResumePoint? {
        if (contentId.isBlank()) return null
        val profileId = getActiveProfileId()
        val all = getAllPlaybackResumesInternal(profileId)
        return all[contentId]
    }

    /**
     * Récupère l'ensemble des points de reprise du profil.
     */
    fun getAllPlaybackResumes(profileId: String = getActiveProfileId()): Map<String, PlaybackResumePoint> {
        return getAllPlaybackResumesInternal(profileId)
    }

    /**
     * Enregistre la progression de lecture d'un contenu.
     * Si la lecture a duré moins de 10 secondes, ou est à moins de 30 secondes de la fin,
     * le point de reprise est automatiquement purgé.
     */
    fun savePlaybackResume(contentId: String, title: String, positionMs: Long, durationMs: Long) {
        if (contentId.isBlank()) return
        val profileId = getActiveProfileId()
        val map = getAllPlaybackResumesInternal(profileId).toMutableMap()

        if (positionMs < 10_000L || (durationMs > 0L && positionMs >= durationMs - 30_000L)) {
            if (map.remove(contentId) != null) {
                savePlaybackResumesInternal(profileId, map)
            }
        } else {
            map[contentId] = PlaybackResumePoint(
                contentId = contentId,
                title = title,
                positionMs = positionMs,
                durationMs = durationMs,
                updatedAt = System.currentTimeMillis()
            )
            savePlaybackResumesInternal(profileId, map)
        }
    }

    /**
     * Supprime manuellement le point de reprise (ex: clic sur "Recommencer").
     */
    fun clearPlaybackResume(contentId: String) {
        if (contentId.isBlank()) return
        val profileId = getActiveProfileId()
        val map = getAllPlaybackResumesInternal(profileId).toMutableMap()
        if (map.remove(contentId) != null) {
            savePlaybackResumesInternal(profileId, map)
        }
    }

    private fun getAllPlaybackResumesInternal(profileId: String): Map<String, PlaybackResumePoint> {
        val key = getResumeStorageKey(profileId)
        val json = prefs.getString(key, null)
        val type = object : TypeToken<Map<String, PlaybackResumePoint>>() {}.type

        if (!json.isNullOrBlank()) {
            try {
                val parsed: Map<String, PlaybackResumePoint>? = gson.fromJson(json, type)
                if (parsed != null) return parsed
            } catch (ignored: Exception) {}
        }

        // Tenter depuis le fichier miroir de secours
        if (playbackResumeBackupFile.exists()) {
            try {
                val backupJson = playbackResumeBackupFile.readText()
                val rootType = object : TypeToken<Map<String, Map<String, PlaybackResumePoint>>>() {}.type
                val rootMap: Map<String, Map<String, PlaybackResumePoint>>? = gson.fromJson(backupJson, rootType)
                val profileMap = rootMap?.get(profileId)
                if (!profileMap.isNullOrEmpty()) {
                    prefs.edit().putString(key, gson.toJson(profileMap)).commit()
                    return profileMap
                }
            } catch (ignored: Exception) {}
        }

        return emptyMap()
    }

    private fun savePlaybackResumesInternal(profileId: String, map: Map<String, PlaybackResumePoint>) {
        val json = gson.toJson(map)
        val key = getResumeStorageKey(profileId)
        prefs.edit().putString(key, json).commit()

        try {
            val rootType = object : TypeToken<Map<String, Map<String, PlaybackResumePoint>>>() {}.type
            val currentRoot: MutableMap<String, Map<String, PlaybackResumePoint>> = if (playbackResumeBackupFile.exists()) {
                runCatching {
                    gson.fromJson<Map<String, Map<String, PlaybackResumePoint>>>(playbackResumeBackupFile.readText(), rootType)?.toMutableMap()
                }.getOrNull() ?: mutableMapOf()
            } else {
                mutableMapOf()
            }
            currentRoot[profileId] = map

            val tempFile = File(appContext.filesDir, "noostv_playback_resume.json.tmp")
            tempFile.writeText(gson.toJson(currentRoot))
            if (tempFile.exists()) {
                tempFile.renameTo(playbackResumeBackupFile)
            }
        } catch (ignored: Exception) {}
    }

    fun logout() {
        prefs.edit()
            .putBoolean(KEY_IS_LOGGED_IN, false)
            .remove(KEY_SERVER_URL)
            .remove(KEY_USERNAME)
            .remove(KEY_PASSWORD)
            .commit()
    }
}
