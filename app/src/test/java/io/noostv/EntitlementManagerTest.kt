package io.noostv

import io.noostv.core.entitlement.EntitlementManager
import io.noostv.core.entitlement.Feature
import io.noostv.core.entitlement.SubscriptionTier
import io.noostv.core.entitlement.UserSubscription
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class EntitlementManagerTest {

    private lateinit var entitlementManager: EntitlementManager

    @Before
    fun setUp() {
        entitlementManager = EntitlementManager()
    }

    @Test
    fun `default user should be on FREE tier and denied 4K HDR and multi-screens`() {
        val currentSub = entitlementManager.subscription.value
        assertEquals(SubscriptionTier.FREE, currentSub.tier)
        assertFalse(currentSub.isPremium)

        // 4K HDR bloqué en standard gratuit
        assertFalse(entitlementManager.isFeatureAllowed(Feature.HDR_4K_STREAMING))
        assertFalse(entitlementManager.isFeatureAllowed(Feature.MULTI_SCREEN))
        assertFalse(entitlementManager.isFeatureAllowed(Feature.CATCHUP_REPLAY))

        // Limite à 1 écran max
        assertTrue(entitlementManager.canStartPlaybackOnScreen(1))
        assertFalse(entitlementManager.canStartPlaybackOnScreen(2))
    }

    @Test
    fun `upgradeToPremium should unlock 4K HDR and allow up to 4 screens`() {
        entitlementManager.upgradeToPremium("user_vip_123")
        val currentSub = entitlementManager.subscription.value

        assertEquals(SubscriptionTier.PREMIUM_VIP, currentSub.tier)
        assertTrue(currentSub.isPremium)

        // Fonctionnalités débloquées
        assertTrue(entitlementManager.isFeatureAllowed(Feature.HDR_4K_STREAMING))
        assertTrue(entitlementManager.isFeatureAllowed(Feature.MULTI_SCREEN))
        assertTrue(entitlementManager.isFeatureAllowed(Feature.CATCHUP_REPLAY))
        assertTrue(entitlementManager.isFeatureAllowed(Feature.AUDIO_PASSTHROUGH))

        // Jusqu'à 4 écrans simultanés
        assertTrue(entitlementManager.canStartPlaybackOnScreen(4))
        assertFalse(entitlementManager.canStartPlaybackOnScreen(5))
    }

    @Test
    fun `expired subscription should be denied all features`() {
        val expiredSub = UserSubscription(
            userId = "exp_user",
            tier = SubscriptionTier.PREMIUM_VIP,
            expiresAtEpochMs = System.currentTimeMillis() - 10000 // Expiré il y a 10s
        )
        entitlementManager.updateSubscription(expiredSub)

        assertTrue(expiredSub.isExpired)
        assertFalse(entitlementManager.isFeatureAllowed(Feature.HDR_4K_STREAMING))
    }
}
