package io.noostv.data.model

/**
 * Modèle de film VOD (Vidéo à la demande)
 */
data class VodMovie(
    val id: String,
    val title: String,
    val streamUrl: String,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val rating: Float = 0.0f,
    val releaseYear: Int? = null,
    val durationMinutes: Int? = null,
    val plot: String? = null,
    val genres: List<String> = emptyList(),
    val cast: List<String> = emptyList(),
    val director: String? = null,
    val isHdr: Boolean = false,
    val hdrFormat: String? = null, // HDR10, Dolby Vision, HLG
    val resolution: String = "1080p", // 1080p, 4K
    val videoCodec: String = "hevc",
    val audioCodec: String = "ac3",
    val categoryId: String = "movies_all",
    val playbackPositionMs: Long = 0L,
    val isFavorite: Boolean = false
) {
    val durationFormatted: String
        get() = durationMinutes?.let {
            val hours = it / 60
            val mins = it % 60
            if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
        } ?: "Durée inconnue"
}
