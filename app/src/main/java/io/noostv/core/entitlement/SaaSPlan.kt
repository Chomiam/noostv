package io.noostv.core.entitlement

/**
 * Tiers d'abonnements NoosTV SaaS
 */
enum class SubscriptionTier(val displayName: String, val maxScreens: Int) {
    FREE("Standard Gratuit", 1),
    PREMIUM_VIP("NoosTV Premium VIP", 4)
}

/**
 * Fonctionnalités contrôlées par le modèle économique SaaS
 */
enum class Feature {
    HDR_4K_STREAMING,
    MULTI_SCREEN,
    CATCHUP_REPLAY,
    CLOUD_SYNC,
    UNLIMITED_EPG,
    AUDIO_PASSTHROUGH,
    MULTI_PLAYLISTS,
    AD_FREE
}

/**
 * Profil utilisateur SaaS et état de souscription
 */
data class UserSubscription(
    val userId: String,
    val tier: SubscriptionTier,
    val activeScreens: Int = 1,
    val expiresAtEpochMs: Long = Long.MAX_VALUE,
    val deviceLinkCode: String? = null
) {
    val isExpired: Boolean
        get() = System.currentTimeMillis() > expiresAtEpochMs

    val isPremium: Boolean
        get() = tier == SubscriptionTier.PREMIUM_VIP && !isExpired
}
