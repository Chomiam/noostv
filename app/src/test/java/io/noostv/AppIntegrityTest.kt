package io.noostv

import io.noostv.data.api.XtreamCodesClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppIntegrityTest {

    @Test
    fun testSanitizeUrlMasksPasswords() {
        val rawUrl = "http://iptv.server:8080/player_api.php?username=alice&password=SuperSecretPassword123&action=get_live_streams"
        val sanitized = XtreamCodesClient.sanitizeUrl(rawUrl)

        assertFalse("Le mot de passe ne doit pas apparaître dans l'URL nettoyée", sanitized.contains("SuperSecretPassword123"))
        assertTrue("Le mot de passe doit être masqué", sanitized.contains("password=***"))
        assertTrue("Le nom d'utilisateur doit être préservé", sanitized.contains("username=alice"))
    }

    @Test
    fun testSanitizeUrlMasksStreamCredentials() {
        val streamUrl = "http://iptv.server:8080/live/alice/TopSecretPass/4521.ts"
        val sanitized = XtreamCodesClient.sanitizeUrl(streamUrl)

        assertFalse("Le token du stream ne doit pas être visible", sanitized.contains("TopSecretPass"))
        assertTrue("Le token doit être masqué", sanitized.contains("/live/alice/***/4521.ts"))
    }

    @Test
    fun testSanitizeUrlHandlesCleanUrls() {
        val cleanUrl = "https://api.github.com/repos/Chomiam/noostv/releases"
        val sanitized = XtreamCodesClient.sanitizeUrl(cleanUrl)

        assertEquals(cleanUrl, sanitized)
    }
}
