package io.noostv.data.parser

import io.noostv.data.model.Channel
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.util.UUID

/**
 * Analyseur haute performance de listes M3U et M3U8 (M3U Plus).
 * Extrait automatiquement les métadonnées IPTV, logos, tags EPG et détection 4K/HDR.
 */
class M3UParser {

    private val extInfRegex = Regex("""#EXTINF:(?:-?\d+)\s*(.*?),\s*(.*)$""")
    private val attributeRegex = Regex("""([a-zA-Z0-9_-]+)="([^"]*)"""")

    fun parse(inputStream: InputStream): List<Channel> {
        val channels = mutableListOf<Channel>()
        val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
        var currentTvgId: String? = null
        var currentLogo: String? = null
        var currentGroup = "Général"
        var currentName: String? = null
        var catchupDays = 0

        reader.useLines { lines ->
            for (rawLine in lines) {
                val line = rawLine.trim()
                if (line.isEmpty() || line.startsWith("#EXTM3U")) {
                    continue
                }

                if (line.startsWith("#EXTINF:")) {
                    val match = extInfRegex.find(line)
                    if (match != null) {
                        val attributesStr = match.groupValues[1]
                        currentName = match.groupValues[2].trim()

                        // Parser les attributs tvg-id, tvg-logo, group-title...
                        for (attrMatch in attributeRegex.findAll(attributesStr)) {
                            val key = attrMatch.groupValues[1].lowercase()
                            val value = attrMatch.groupValues[2]
                            when (key) {
                                "tvg-id" -> currentTvgId = value
                                "tvg-logo" -> currentLogo = value
                                "group-title" -> currentGroup = value
                                "catchup-days" -> catchupDays = value.toIntOrNull() ?: 0
                            }
                        }
                    }
                } else if (!line.startsWith("#")) {
                    // C'est l'URL du flux
                    val channelName = currentName ?: "Chaîne Sans Nom"
                    val isHdr = channelName.contains("HDR", ignoreCase = true) ||
                            channelName.contains("Dolby Vision", ignoreCase = true)
                    val resolution = when {
                        channelName.contains("4K", ignoreCase = true) ||
                                channelName.contains("UHD", ignoreCase = true) -> "4K UHD"
                        channelName.contains("720", ignoreCase = true) -> "720p"
                        channelName.contains("FHD", ignoreCase = true) ||
                                channelName.contains("1080", ignoreCase = true) ||
                                channelName.contains("HD", ignoreCase = true) -> "1080p"
                        else -> "1080p"
                    }
                    val videoCodec = when {
                        channelName.contains("AV1", ignoreCase = true) -> "av1"
                        channelName.contains("HEVC", ignoreCase = true) ||
                                channelName.contains("H265", ignoreCase = true) ||
                                isHdr || resolution == "4K UHD" -> "hevc"
                        else -> "h264"
                    }

                    channels.add(
                        Channel(
                            id = currentTvgId ?: UUID.randomUUID().toString(),
                            name = channelName,
                            streamUrl = line,
                            logoUrl = currentLogo,
                            categoryName = currentGroup,
                            categoryId = currentGroup.lowercase().replace(" ", "_"),
                            isHdr = isHdr,
                            resolution = resolution,
                            videoCodec = videoCodec,
                            epgChannelId = currentTvgId,
                            hasCatchup = catchupDays > 0,
                            catchupDays = catchupDays
                        )
                    )

                    // Réinitialisation pour la ligne suivante
                    currentTvgId = null
                    currentLogo = null
                    currentGroup = "Général"
                    currentName = null
                    catchupDays = 0
                }
            }
        }

        return channels
    }
}
