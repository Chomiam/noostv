package io.noostv.data.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Modèle de programme du Guide TV (EPG / XMLTV)
 */
data class EpgProgram(
    val id: String,
    val channelId: String,
    val title: String,
    val description: String? = null,
    val startEpochMs: Long,
    val stopEpochMs: Long,
    val category: String? = null,
    val iconUrl: String? = null,
    val hasCatchup: Boolean = false
) {
    /**
     * Vérifie si le programme est actuellement en cours de diffusion
     */
    fun isLiveNow(currentTimeMs: Long = System.currentTimeMillis()): Boolean {
        return currentTimeMs in startEpochMs until stopEpochMs
    }

    /**
     * Calcule le pourcentage de diffusion écoulé (0.0 à 1.0)
     */
    fun progressFraction(currentTimeMs: Long = System.currentTimeMillis()): Float {
        if (currentTimeMs <= startEpochMs) return 0.0f
        if (currentTimeMs >= stopEpochMs) return 1.0f
        val total = (stopEpochMs - startEpochMs).toFloat()
        val elapsed = (currentTimeMs - startEpochMs).toFloat()
        return (elapsed / total).coerceIn(0.0f, 1.0f)
    }

    /**
     * Formate la plage horaire (ex: 20:50 - 22:30)
     */
    val timeSlotFormatted: String
        get() {
            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
            val start = sdf.format(Date(startEpochMs))
            val stop = sdf.format(Date(stopEpochMs))
            return "$start - $stop"
        }
}
