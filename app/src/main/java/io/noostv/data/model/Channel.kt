package io.noostv.data.model

/**
 * Modèle de chaîne de télévision en direct (Live IPTV)
 */
data class Channel(
    val id: String,
    val num: Int? = null,
    val name: String,
    val streamUrl: String,
    val logoUrl: String? = null,
    val categoryId: String = "general",
    val categoryName: String = "Généraliste",
    val isHdr: Boolean = false,
    val resolution: String = "1080p", // 1080p, 4K UHD, 720p
    val videoCodec: String = "h264", // h264, hevc, av1
    val epgChannelId: String? = null,
    val isFavorite: Boolean = false,
    val hasCatchup: Boolean = false,
    val catchupDays: Int = 0
)

/**
 * Type de catégorie de contenu
 */
enum class CategoryType {
    LIVE,
    VOD,
    SERIES
}

/**
 * Catégorie de flux IPTV ou VOD
 */
data class Category(
    val id: String,
    val name: String,
    val type: CategoryType = CategoryType.LIVE
)
