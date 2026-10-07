package io.noostv.core.localization

/**
 * Langues supportées par l'interface NoosTV avec code ISO, noms et émoji de drapeau.
 */
enum class AppLanguage(
    val code: String,
    val displayName: String,
    val flagEmoji: String,
    val nativeName: String
) {
    FRENCH("fr", "Français", "🇫🇷", "Français"),
    ENGLISH("en", "English", "🇬🇧", "English"),
    SPANISH("es", "Espagnol", "🇪🇸", "Español"),
    GERMAN("de", "Allemand", "🇩🇪", "Deutsch"),
    ITALIAN("it", "Italien", "🇮🇹", "Italiano"),
    ARABIC("ar", "Arabe", "🇸🇦", "العربية"),
    PORTUGUESE("pt", "Portugais", "🇵🇹", "Português");

    companion object {
        fun fromCode(code: String?): AppLanguage {
            return entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: FRENCH
        }
    }
}
