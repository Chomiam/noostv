package io.noostv.data.model

import java.util.UUID

/**
 * Modèle représentant un compte IPTV précédemment connecté sur l'appareil.
 * Stocké exclusivement en local et chiffré en AES-256-GCM via l'Android KeyStore.
 */
data class SavedAccount(
    val id: String = UUID.randomUUID().toString(),
    val serverUrl: String,
    val username: String,
    val password: String,
    val playlistName: String = "Mon Abonnement IPTV",
    val lastUsedTimestamp: Long = System.currentTimeMillis()
)
