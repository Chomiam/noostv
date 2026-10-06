package io.noostv.core.entitlement

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Gestionnaire des droits d'accès SaaS (Entitlement Engine).
 * Protège les fonctionnalités réservées aux abonnés Premium (4K HDR, Multi-écrans, Replay).
 */
class EntitlementManager(
    initialSubscription: UserSubscription = UserSubscription(
        userId = "guest_user",
        tier = SubscriptionTier.FREE
    )
) {
    private val _subscription = MutableStateFlow(initialSubscription)
    val subscription: StateFlow<UserSubscription> = _subscription.asStateFlow()

    /**
     * Vérifie si une fonctionnalité donnée est autorisée avec le forfait actuel
     */
    fun isFeatureAllowed(feature: Feature): Boolean {
        val current = _subscription.value
        if (current.isExpired) return false

        return when (feature) {
            Feature.AD_FREE,
            Feature.HDR_4K_STREAMING,
            Feature.MULTI_SCREEN,
            Feature.CATCHUP_REPLAY,
            Feature.CLOUD_SYNC,
            Feature.AUDIO_PASSTHROUGH,
            Feature.MULTI_PLAYLISTS -> current.tier == SubscriptionTier.PREMIUM_VIP

            Feature.UNLIMITED_EPG -> current.tier == SubscriptionTier.PREMIUM_VIP
        }
    }

    /**
     * Valide si un nouvel écran peut démarrer la lecture en streaming
     */
    fun canStartPlaybackOnScreen(targetScreenCount: Int): Boolean {
        val current = _subscription.value
        return targetScreenCount <= current.tier.maxScreens
    }

    /**
     * Met à jour le forfait de l'utilisateur (après paiement ou validation de licence)
     */
    fun updateSubscription(newSubscription: UserSubscription) {
        _subscription.value = newSubscription
    }

    /**
     * Active l'abonnement VIP Premium
     */
    fun upgradeToPremium(userId: String = "premium_user", durationDays: Int = 365) {
        val expiry = System.currentTimeMillis() + (durationDays.toLong() * 24 * 3600 * 1000)
        _subscription.value = UserSubscription(
            userId = userId,
            tier = SubscriptionTier.PREMIUM_VIP,
            expiresAtEpochMs = expiry
        )
    }

    /**
     * Rétrograde vers le forfait gratuit
     */
    fun downgradeToFree() {
        _subscription.value = UserSubscription(
            userId = _subscription.value.userId,
            tier = SubscriptionTier.FREE
        )
    }
}
