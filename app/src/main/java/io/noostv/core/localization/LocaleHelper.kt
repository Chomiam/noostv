package io.noostv.core.localization

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.staticCompositionLocalOf
import java.util.Locale

/**
 * Fournisseur de composition locale pour les chaînes traduites et la langue active dans Jetpack Compose.
 */
val LocalStrings = staticCompositionLocalOf<AppStrings> { FrenchStrings }
val LocalAppLanguage = staticCompositionLocalOf<AppLanguage> { AppLanguage.FRENCH }

object LocaleHelper {
    /**
     * Applique la locale au niveau du contexte Android et de Locale.setDefault()
     */
    fun setAppLocale(context: Context, language: AppLanguage) {
        val locale = Locale(language.code)
        Locale.setDefault(locale)

        val resources = context.resources
        val config = Configuration(resources.configuration)
        config.setLocale(locale)
        resources.updateConfiguration(config, resources.displayMetrics)
    }
}
