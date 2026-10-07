package io.noostv.data.model

/**
 * Épisode de série TV
 */
data class Episode(
    val id: String,
    val seriesId: String,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val title: String,
    val streamUrl: String,
    val containerExtension: String = "mp4",
    val thumbnailUrl: String? = null,
    val durationMinutes: Int? = null,
    val plot: String? = null,
    val rating: Float = 0.0f,
    val playbackPositionMs: Long = 0L,
    val isWatched: Boolean = false
)

/**
 * Saison d'une série TV
 */
data class Season(
    val seasonNumber: Int,
    val name: String,
    val episodeCount: Int,
    val episodes: List<Episode> = emptyList()
)

/**
 * Série TV complète
 */
data class Series(
    val id: String,
    val title: String,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val rating: Float = 0.0f,
    val releaseYear: Int? = null,
    val plot: String? = null,
    val genres: List<String> = emptyList(),
    val cast: List<String> = emptyList(),
    val categoryId: String = "series_all",
    val seasons: List<Season> = emptyList(),
    val isFavorite: Boolean = false
)
