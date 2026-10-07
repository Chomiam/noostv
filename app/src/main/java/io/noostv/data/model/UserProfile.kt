package io.noostv.data.model

/**
 * Profil utilisateur permettant la gestion multi-profils sur un même compte IPTV.
 * Chaque profil dispose de ses propres favoris (chaînes, films, séries)
 * et de ses filtres personnalisés de catégories affichées.
 */
data class UserProfile(
    val id: String,
    val name: String,
    val avatarColorHex: Long = 0xFF3888FF,
    val avatarIcon: String = "Person", // Person, Face, Movie, Sports, Child
    val favoriteChannelIds: Set<String> = emptySet(),
    val favoriteMovieIds: Set<String> = emptySet(),
    val favoriteSeriesIds: Set<String> = emptySet(),
    val hiddenVodCategoryIds: Set<String> = emptySet(),
    val hiddenSeriesCategoryIds: Set<String> = emptySet()
) {
    companion object {
        val AVATAR_COLORS = listOf(
            0xFF3888FF, // Bleu NOOS
            0xFF8B5CF6, // Violet Lavande
            0xFF10B981, // Émeraude
            0xFFF59E0B, // Ambre
            0xFFEC4899, // Rose vif
            0xFF06B6D4, // Cyan
            0xFFEF4444  // Rouge corail
        )

        val DEFAULT_PROFILE = UserProfile(
            id = "profile_default",
            name = "Principal",
            avatarColorHex = 0xFF3888FF,
            avatarIcon = "Person"
        )
    }
}
