package io.noostv.data.model

/**
 * Mémorisation d'un point de reprise de lecture (Film ou Épisode de série)
 * Lié au profil utilisateur actif.
 */
data class PlaybackResumePoint(
    val contentId: String,
    val title: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long = System.currentTimeMillis()
) {
    val progressFraction: Float
        get() = if (durationMs > 0L) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f

    val progressPercent: Int
        get() = (progressFraction * 100).toInt()

    val formattedPosition: String
        get() = io.noostv.core.player.formatDuration(positionMs)

    val formattedDuration: String
        get() = io.noostv.core.player.formatDuration(durationMs)
}
