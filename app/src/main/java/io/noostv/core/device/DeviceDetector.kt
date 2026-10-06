package io.noostv.core.device

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.view.Display
import android.view.WindowManager

/**
 * Type d'appareil cible pour l'adaptation ergonomique de l'interface
 */
enum class DeviceType {
    TELEVISION,
    TABLET,
    PHONE
}

/**
 * Détecteur intelligent de profil matériel (Android TV / Mi Box / Google TV Streamer vs Téléphone / Tablette).
 * Permet à NoosTV d'activer la navigation 10-foot D-Pad ou l'expérience tactile mobile.
 */
class DeviceDetector(private val context: Context) {

    val deviceType: DeviceType by lazy {
        resolveDeviceType()
    }

    val isTv: Boolean
        get() = deviceType == DeviceType.TELEVISION

    val isTouchDevice: Boolean
        get() = context.packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)

    /**
     * Détermine avec précision si l'appareil est une télévision ou un boîtier OTT
     */
    private fun resolveDeviceType(): DeviceType {
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        if (uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION) {
            return DeviceType.TELEVISION
        }

        val pm = context.packageManager
        // Google TV Streamer et Xiaomi Mi Box déclarent obligatoirement FEATURE_LEANBACK ou FEATURE_TELEVISION
        if (pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
            pm.hasSystemFeature("android.hardware.type.television")
        ) {
            return DeviceType.TELEVISION
        }

        // Si ce n'est pas une TV, vérifier si c'est une tablette (écran >= 600dp)
        val config = context.resources.configuration
        return if (config.smallestScreenWidthDp >= 600) {
            DeviceType.TABLET
        } else {
            DeviceType.PHONE
        }
    }

    /**
     * Liste des capacités HDR matérielles du téléviseur ou de l'écran connecté
     */
    fun getSupportedHdrModes(): List<String> {
        val modes = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            val display = wm?.defaultDisplay
            val capabilities = display?.hdrCapabilities
            capabilities?.supportedHdrTypes?.forEach { type ->
                when (type) {
                    Display.HdrCapabilities.HDR_TYPE_HDR10 -> modes.add("HDR10")
                    Display.HdrCapabilities.HDR_TYPE_HLG -> modes.add("HLG")
                    Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION -> modes.add("Dolby Vision")
                    else -> modes.add("HDR_OTHER_$type")
                }
            }
        }
        return modes
    }

    /**
     * Détecte si le téléviseur supporte le décodage matériel HDR
     */
    fun hasHdrDisplay(): Boolean {
        return getSupportedHdrModes().isNotEmpty()
    }
}
