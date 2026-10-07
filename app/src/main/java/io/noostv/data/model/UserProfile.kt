package io.noostv.data.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Profil utilisateur permettant la gestion multi-profils sur un même compte IPTV.
 * Chaque profil dispose de ses propres favoris (chaînes, films, séries),
 * filtres de catégories, ainsi que de sa couleur et de son icône de style Netflix / Prime.
 */
data class UserProfile(
    val id: String,
    val name: String,
    val avatarColorHex: Long = 0xFF3888FF,
    val avatarIcon: String = "Person",
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

        data class AvatarIconOption(
            val id: String,
            val name: String,
            val icon: ImageVector
        )

        val AVATAR_ICONS = listOf(
            AvatarIconOption("Person", "Classique", Icons.Default.Person),
            AvatarIconOption("Face", "Sourire", Icons.Default.Face),
            AvatarIconOption("RocketLaunch", "Astronaute", Icons.Default.RocketLaunch),
            AvatarIconOption("Movie", "Cinéphile", Icons.Default.Movie),
            AvatarIconOption("SportsEsports", "Gamer", Icons.Default.SportsEsports),
            AvatarIconOption("Pets", "Animaux", Icons.Default.Pets),
            AvatarIconOption("LocalFireDepartment", "Flamme", Icons.Default.LocalFireDepartment),
            AvatarIconOption("Star", "Étoile", Icons.Default.Star),
            AvatarIconOption("ChildCare", "Enfants", Icons.Default.ChildCare),
            AvatarIconOption("SmartToy", "Robot", Icons.Default.SmartToy)
        )

        fun getAvatarIconVector(id: String): ImageVector {
            return AVATAR_ICONS.firstOrNull { it.id == id }?.icon ?: Icons.Default.Person
        }

        val DEFAULT_PROFILE = UserProfile(
            id = "profile_default",
            name = "Principal",
            avatarColorHex = 0xFF3888FF,
            avatarIcon = "Person"
        )
    }
}
