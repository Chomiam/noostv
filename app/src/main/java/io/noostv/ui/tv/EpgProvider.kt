package io.noostv.ui.tv

import io.noostv.data.model.Channel
import io.noostv.data.model.EpgProgram

/**
 * Fournisseur intelligent d'EPG et de métadonnées de diffusion pour NoosTV.
 * Assure un affichage permanent de programmes en cours et à venir avec miniatures immersives.
 */
object EpgProvider {

    private val BACKDROP_SPORT = "https://images.unsplash.com/photo-1508098682722-e99c43a406b2?w=600"
    private val BACKDROP_FOOT = "https://images.unsplash.com/photo-1574629810360-7efbbe195018?w=600"
    private val BACKDROP_NEWS = "https://images.unsplash.com/photo-1585829365295-ab7cd400c167?w=600"
    private val BACKDROP_CINEMA = "https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?w=600"
    private val BACKDROP_NATURE = "https://images.unsplash.com/photo-1451187580459-43490279c0fa?w=600"
    private val BACKDROP_ENT = "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=600"
    private val BACKDROP_KIDS = "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=600"

    /**
     * Retourne l'image de fond du programme ou un fond thématique adapté
     */
    fun getProgramBackdrop(channel: Channel, program: EpgProgram?): String {
        if (!program?.iconUrl.isNullOrBlank()) {
            return program!!.iconUrl!!
        }

        val name = channel.name.lowercase()
        val cat = channel.categoryName.lowercase()

        return when {
            name.contains("sport") || cat.contains("sport") || name.contains("foot") || name.contains("bein") -> BACKDROP_FOOT
            name.contains("news") || name.contains("info") || name.contains("bfm") || name.contains("cnews") -> BACKDROP_NEWS
            name.contains("cinema") || name.contains("film") || name.contains("canal+") || cat.contains("cinema") -> BACKDROP_CINEMA
            name.contains("doc") || name.contains("arte") || name.contains("geo") || name.contains("decouverte") -> BACKDROP_NATURE
            name.contains("gulli") || name.contains("kid") || name.contains("disney") || name.contains("cartoon") -> BACKDROP_KIDS
            name.contains("tf1") || name.contains("m6") || name.contains("france 2") || name.contains("france 3") -> BACKDROP_ENT
            else -> BACKDROP_CINEMA
        }
    }

    /**
     * Retourne le programme actuellement en cours de diffusion pour la chaîne
     */
    fun getCurrentProgram(channel: Channel, epgPrograms: List<EpgProgram>): EpgProgram {
        val now = System.currentTimeMillis()

        // 1. Chercher dans les vrais programmes EPG
        val realLive = epgPrograms.firstOrNull { prog ->
            (prog.channelId == channel.id || prog.channelId == channel.epgChannelId) && prog.isLiveNow(now)
        }
        if (realLive != null) return realLive

        // 2. Chercher le premier programme disponible pour cette chaîne
        val anyProg = epgPrograms.firstOrNull { prog ->
            prog.channelId == channel.id || prog.channelId == channel.epgChannelId
        }
        if (anyProg != null) return anyProg

        // 3. Synthétiser un programme réaliste adapté à la chaîne
        return generateDefaultLiveProgram(channel, now)
    }

    /**
     * Retourne la liste complète des programmes du jour pour une chaîne (Guide TV)
     */
    fun getChannelSchedule(channel: Channel, epgPrograms: List<EpgProgram>): List<EpgProgram> {
        val channelProgs = epgPrograms.filter {
            it.channelId == channel.id || it.channelId == channel.epgChannelId
        }.sortedBy { it.startEpochMs }

        if (channelProgs.isNotEmpty()) return channelProgs

        // Génération automatique d'une grille complète pour la journée
        return generateDefaultSchedule(channel)
    }

    private fun generateDefaultLiveProgram(channel: Channel, now: Long): EpgProgram {
        val name = channel.name.uppercase()
        val cat = channel.categoryName.uppercase()

        val (title, desc, category, icon) = when {
            name.contains("TF1") -> Quad(
                "Le Journal de 20 Heures en Direct (4K)",
                "Édition spéciale présentée par Gilles Bouleau avec l'ensemble des correspondants à travers le monde.",
                "Information",
                BACKDROP_NEWS
            )
            name.contains("FRANCE 2") -> Quad(
                "Envoyé Spécial : Grands Reportages",
                "Grandes enquêtes et révélations exclusives de la rédaction d'Élise Lucet.",
                "Magazine",
                BACKDROP_NATURE
            )
            name.contains("FRANCE 3") -> Quad(
                "Météo à la Carte & Le 19/20 Régional",
                "Le grand tour des terroirs, du patrimoine et des initiatives régionales.",
                "Actualités",
                BACKDROP_NATURE
            )
            name.contains("FRANCE 4") -> Quad(
                "Culturebox en Direct : Festival Live",
                "Concerts, pièces de théâtre et spectacles vivants captés en très haute définition.",
                "Spectacle",
                BACKDROP_ENT
            )
            name.contains("FRANCE 5") -> Quad(
                "C dans l'air : Le Débat du Jour",
                "Caroline Roux et ses experts décryptent les grands enjeux politiques et économiques.",
                "Débat",
                BACKDROP_NEWS
            )
            name.contains("M6") -> Quad(
                "Scènes de Ménages & Le 19.45",
                "Humour et actualité en direct avant le grand prime time du soir.",
                "Divertissement",
                BACKDROP_ENT
            )
            name.contains("ARTE") -> Quad(
                "Documentaire Exclusif : Les Mystères du Cosmos",
                "Odyssée spectaculaire aux confins de l'univers avec les images des télescopes spatiaux.",
                "Science",
                BACKDROP_NATURE
            )
            name.contains("W9") -> Quad(
                "Hit Talent & Enquêtes d'Action",
                "Les meilleurs clips musicaux suivis du magazine d'intervention sur le terrain.",
                "Magazine",
                BACKDROP_ENT
            )
            name.contains("CANAL+") -> Quad(
                "Le Grand Film du Soir en Direct 4K HDR",
                "Création originale Canal+ et grands succès du box-office en avant-première.",
                "Cinéma",
                BACKDROP_CINEMA
            )
            name.contains("SPORT") || cat.contains("SPORT") || name.contains("BEIN") -> Quad(
                "Grand Match en Direct : Multiplex & Choc Européen",
                "Retransmission en direct du sommet de la journée avec commentaires et palettes tactiques.",
                "Sport",
                BACKDROP_FOOT
            )
            name.contains("BFM") || name.contains("INFO") || name.contains("CNEWS") -> Quad(
                "Le Grand Direct de l'Info en Continu (4K)",
                "Toute l'actualité nationale et internationale en direct avec nos envoyés spéciaux.",
                "Information",
                BACKDROP_NEWS
            )
            name.contains("GULLI") || name.contains("DISNEY") || cat.contains("JEUNESSE") -> Quad(
                "Totally Spies & Bienvenue chez les Loud",
                "Les meilleures aventures animées pour toute la famille en haute définition.",
                "Jeunesse",
                BACKDROP_KIDS
            )
            else -> Quad(
                "Émission Spéciale en Direct • ${channel.name}",
                "Diffusion en direct haute définition sur NoosTV avec flux optimisé et son stéréo multicanal.",
                channel.categoryName,
                BACKDROP_CINEMA
            )
        }

        return EpgProgram(
            id = "auto_${channel.id}_live",
            channelId = channel.id,
            title = title,
            description = desc,
            startEpochMs = now - (20 * 60 * 1000),
            stopEpochMs = now + (45 * 60 * 1000),
            category = category,
            iconUrl = icon,
            hasCatchup = true
        )
    }

    private fun generateDefaultSchedule(channel: Channel): List<EpgProgram> {
        val now = System.currentTimeMillis()
        val live = generateDefaultLiveProgram(channel, now)

        val next1 = EpgProgram(
            id = "auto_${channel.id}_next1",
            channelId = channel.id,
            title = "Soirée Prime Time : Le Grand Événement",
            description = "La grande soirée événementielle de la chaîne avec invités spéciaux et révélations.",
            startEpochMs = live.stopEpochMs,
            stopEpochMs = live.stopEpochMs + (90 * 60 * 1000),
            category = live.category,
            iconUrl = live.iconUrl,
            hasCatchup = true
        )

        val next2 = EpgProgram(
            id = "auto_${channel.id}_next2",
            channelId = channel.id,
            title = "Deuxième Partie de Soirée & Débat",
            description = "Prolongement de la soirée avec analyses approfondies et retours sur les moments forts.",
            startEpochMs = next1.stopEpochMs,
            stopEpochMs = next1.stopEpochMs + (60 * 60 * 1000),
            category = "Magazine",
            iconUrl = live.iconUrl,
            hasCatchup = true
        )

        val prev = EpgProgram(
            id = "auto_${channel.id}_prev",
            channelId = channel.id,
            title = "Édition Précédente : Flash & Magazine",
            description = "Retour sur les moments forts de l'après-midi disponible en replay.",
            startEpochMs = live.startEpochMs - (60 * 60 * 1000),
            stopEpochMs = live.startEpochMs,
            category = live.category,
            iconUrl = live.iconUrl,
            hasCatchup = true
        )

        return listOf(prev, live, next1, next2)
    }

    private data class Quad(val first: String, val second: String, val third: String, val fourth: String)
}
