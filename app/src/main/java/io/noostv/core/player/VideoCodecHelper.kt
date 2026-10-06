package io.noostv.core.player

import android.content.Context
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build

/**
 * Informations sur les capacités matérielles de décodage vidéo (AV1, HEVC, H264, VP9)
 */
data class CodecCapabilities(
    val hasHardwareAv1: Boolean,
    val hasHardwareHevc: Boolean,
    val hasHardwareVp9: Boolean,
    val maxSupportedResolution: String,
    val isGoogleTvStreamer: Boolean,
    val isMiBox: Boolean
)

/**
 * Utilitaire d'analyse et d'optimisation des codecs matériels pour Google TV Streamer et Xiaomi Mi Box.
 */
object VideoCodecHelper {

    fun detectCapabilities(context: Context): CodecCapabilities {
        val model = Build.MODEL.lowercase()
        val manufacturer = Build.MANUFACTURER.lowercase()

        val isGoogleTvStreamer = model.contains("streamer") || model.contains("google tv")
        val isMiBox = model.contains("mibox") || model.contains("mi box") || manufacturer.contains("xiaomi")

        var hasHwAv1 = false
        var hasHwHevc = false
        var hasHwVp9 = false

        try {
            val codecList = MediaCodecList(MediaCodecList.ALL_CODECS)
            for (info in codecList.codecInfos) {
                if (info.isEncoder) continue

                val isHardware = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    info.isHardwareAccelerated
                } else {
                    !info.name.startsWith("omx.google.") && !info.name.startsWith("c2.android.")
                }

                if (isHardware) {
                    for (type in info.supportedTypes) {
                        when (type.lowercase()) {
                            MediaFormat.MIMETYPE_VIDEO_AV1.lowercase() -> hasHwAv1 = true
                            MediaFormat.MIMETYPE_VIDEO_HEVC.lowercase() -> hasHwHevc = true
                            MediaFormat.MIMETYPE_VIDEO_VP9.lowercase() -> hasHwVp9 = true
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Fallback basé sur l'appareil
            if (isGoogleTvStreamer) {
                hasHwAv1 = true
                hasHwHevc = true
            }
            if (isMiBox) {
                hasHwHevc = true
            }
        }

        val maxRes = if (isGoogleTvStreamer || hasHwAv1 || hasHwHevc) "4K UHD (2160p)" else "Full HD (1080p)"

        return CodecCapabilities(
            hasHardwareAv1 = hasHwAv1,
            hasHardwareHevc = hasHwHevc,
            hasHardwareVp9 = hasHwVp9,
            maxSupportedResolution = maxRes,
            isGoogleTvStreamer = isGoogleTvStreamer,
            isMiBox = isMiBox
        )
    }
}
