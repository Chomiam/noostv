package io.noostv.ui.tv

import io.noostv.data.model.Channel
import io.noostv.data.model.EpgProgram
import kotlin.math.abs

/**
 * Fournisseur intelligent d'EPG et de miniatures thématiques pour NoosTV.
 * Assure un affichage dynamique, varié et réaliste des programmes TV en cours et à venir,
 * avec des pools d'images haute définition distinctes par thématique pour que chaque chaîne ait sa propre identité visuelle.
 */
object EpgProvider {

    // 1. Combat & MMA / UFC / Boxe
    private val BACKDROPS_COMBAT = listOf(
        "https://images.unsplash.com/photo-1549719386-74dfcbf7dbed?w=700", // Éclairage cage Octogone
        "https://images.unsplash.com/photo-1517838277536-f5f99be501cd?w=700", // Ring de boxe et gants
        "https://images.unsplash.com/photo-1509563457123-ab5432836359?w=700", // Ceinture de championnat
        "https://images.unsplash.com/photo-1517963879433-6ad2b056d712?w=700"  // Arène de combat sous projecteurs
    )

    // 2. Football & Soccer
    private val BACKDROPS_FOOTBALL = listOf(
        "https://images.unsplash.com/photo-1508098682722-e99c43a406b2?w=700", // Stade de nuit sous projecteurs
        "https://images.unsplash.com/photo-1574629810360-7efbbe195018?w=700", // Pelouse de football et tribunes
        "https://images.unsplash.com/photo-1489944440615-453fc2b6a9a9?w=700", // Vue aérienne de stade
        "https://images.unsplash.com/photo-1522778119026-d647f0596c20?w=700"  // Cage de but et ambiance match
    )

    // 3. Sports Mécaniques & F1
    private val BACKDROPS_MOTORSPORT = listOf(
        "https://images.unsplash.com/photo-1568605117036-5fe5e7bab0b7?w=700", // Circuit F1 nocturne
        "https://images.unsplash.com/photo-1552519507-da3b142c6e3d?w=700", // Course automobile haute vitesse
        "https://images.unsplash.com/photo-1534093607318-f025413f49cb?w=700"  // Piste de grand prix
    )

    // 4. Basketball & Sports Indoor
    private val BACKDROPS_BASKETBALL = listOf(
        "https://images.unsplash.com/photo-1546519638-68e109498ffc?w=700", // Arène NBA
        "https://images.unsplash.com/photo-1519766304817-4f37bda74a29?w=700"  // Terrain de basket sous les projecteurs
    )

    // 5. Tennis
    private val BACKDROPS_TENNIS = listOf(
        "https://images.unsplash.com/photo-1554068865-24cecd4e34b8?w=700", // Court de tennis
        "https://images.unsplash.com/photo-1622279457486-62dcc4a431d6?w=700"  // Court en terre battue
    )

    // 6. Cinéma & Films
    private val BACKDROPS_CINEMA = listOf(
        "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=700", // Atmosphère sci-fi néon
        "https://images.unsplash.com/photo-1478760329108-5c3ed9d495a0?w=700", // Ville sombre et thriller
        "https://images.unsplash.com/photo-1485846234645-a62644f84728?w=700", // Plateau de tournage caméra
        "https://images.unsplash.com/photo-1517604931442-7e0c8ed2963c?w=700", // Salle de cinéma en avant-première
        "https://images.unsplash.com/photo-1536440136628-849c177e76a1?w=700"  // Objectif cinématographique
    )

    // 7. Séries & Fiction TV
    private val BACKDROPS_SERIES = listOf(
        "https://images.unsplash.com/photo-1594909122845-11baa439b7bf?w=700", // Production de série TV
        "https://images.unsplash.com/photo-1574375927938-d5a98e8ffe85?w=700", // Écran de série dramatique
        "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=700"  // Ambiance polar et enquête
    )

    // 8. Actualités & Information
    private val BACKDROPS_NEWS = listOf(
        "https://images.unsplash.com/photo-1585829365295-ab7cd400c167?w=700", // Studio de rédaction d'actualités
        "https://images.unsplash.com/photo-1505373877841-8d25f7d46678?w=700", // Conférence de presse internationale
        "https://images.unsplash.com/photo-1451187580459-43490279c0fa?w=700"  // Globe digital info monde
    )

    // 9. Documentaires & Découverte
    private val BACKDROPS_NATURE = listOf(
        "https://images.unsplash.com/photo-1516426122078-c23e76319801?w=700", // Faune sauvage safari
        "https://images.unsplash.com/photo-1464822759023-fed622ff2c3b?w=700", // Sommets montagneux
        "https://images.unsplash.com/photo-1544551763-46a013bb70d5?w=700", // Fonds marins et océan
        "https://images.unsplash.com/photo-1451187580459-43490279c0fa?w=700"  // Espace et nébuleuse
    )

    // 10. Jeunesse & Dessins Animés
    private val BACKDROPS_KIDS = listOf(
        "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=700", // Monde féérique coloré
        "https://images.unsplash.com/photo-1579783900882-c0d3dad7b119?w=700", // Animation et illustration
        "https://images.unsplash.com/photo-1563089145-599997674d42?w=700"  // Paysage imaginaire
    )

    // 11. Musique & Concerts
    private val BACKDROPS_MUSIC = listOf(
        "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=700", // Scène festival laser
        "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=700", // Concert électro DJ
        "https://images.unsplash.com/photo-1598488035139-bdbb2231ce04?w=700"  // Studio d'enregistrement
    )

    /**
     * Retourne l'image de fond du programme ou sélectionne une image thématisée variée dans le pool adapté.
     */
    fun getProgramBackdrop(channel: Channel, program: EpgProgram?): String {
        // Si le programme possède une vraie URL d'icône/vignette EPG du diffuseur
        if (!program?.iconUrl.isNullOrBlank() && !program!!.iconUrl!!.contains("unsplash.com")) {
            return program.iconUrl!!
        }

        val name = channel.name.lowercase()
        val cat = channel.categoryName.lowercase()
        val progTitle = (program?.title ?: "").lowercase()

        val pool = when {
            name.contains("ufc") || name.contains("mma") || name.contains("boxe") || name.contains("boxing") ||
                    name.contains("wwe") || cat.contains("ufc") || cat.contains("combat") ||
                    progTitle.contains("ufc") || progTitle.contains("mma") -> BACKDROPS_COMBAT

            name.contains("f1") || name.contains("formule") || name.contains("moto") ||
                    name.contains("racing") || cat.contains("moto") -> BACKDROPS_MOTORSPORT

            name.contains("basket") || name.contains("nba") -> BACKDROPS_BASKETBALL

            name.contains("tennis") || name.contains("wimbledon") || name.contains("roland") -> BACKDROPS_TENNIS

            name.contains("foot") || name.contains("sport") || name.contains("bein") ||
                    name.contains("eurosport") || name.contains("equipe") || name.contains("canal+ sport") ||
                    name.contains("dazn") || cat.contains("sport") || cat.contains("foot") -> BACKDROPS_FOOTBALL

            name.contains("news") || name.contains("info") || name.contains("bfm") ||
                    name.contains("cnews") || name.contains("lci") || name.contains("france 24") ||
                    name.contains("cnn") || name.contains("bbc") || cat.contains("info") || cat.contains("news") -> BACKDROPS_NEWS

            name.contains("doc") || name.contains("arte") || name.contains("geo") ||
                    name.contains("decouverte") || name.contains("nat geo") || name.contains("histoire") ||
                    name.contains("planete") || cat.contains("doc") -> BACKDROPS_NATURE

            name.contains("gulli") || name.contains("kid") || name.contains("disney") ||
                    name.contains("cartoon") || name.contains("nickelodeon") || name.contains("boing") ||
                    name.contains("jeunesse") || cat.contains("jeun") -> BACKDROPS_KIDS

            name.contains("music") || name.contains("mtv") || name.contains("trace") ||
                    name.contains("melody") || name.contains("mezzo") || name.contains("nrj hits") || cat.contains("music") -> BACKDROPS_MUSIC

            name.contains("serie") || cat.contains("serie") -> BACKDROPS_SERIES

            name.contains("cinema") || name.contains("film") || name.contains("canal+") ||
                    name.contains("cine") || name.contains("ocs") || name.contains("hbo") ||
                    name.contains("paramount") || cat.contains("cine") || cat.contains("film") -> BACKDROPS_CINEMA

            else -> BACKDROPS_CINEMA
        }

        // Varier l'image en fonction du hash de la chaîne et du titre pour que deux chaînes consécutives n'aient jamais la même photo
        val hash = abs(channel.id.hashCode() * 37 + channel.name.hashCode() + (program?.title?.hashCode() ?: 0))
        return pool[hash % pool.size]
    }

    /**
     * Retourne le programme actuellement en cours de diffusion pour la chaîne
     */
    fun getCurrentProgram(channel: Channel, epgPrograms: List<EpgProgram>): EpgProgram {
        val now = System.currentTimeMillis()

        // 1. Chercher dans les vrais programmes EPG en cours
        val realLive = epgPrograms.firstOrNull { prog ->
            (prog.channelId == channel.id || prog.channelId == channel.epgChannelId) && prog.isLiveNow(now)
        }
        if (realLive != null) return realLive

        // 2. Chercher le premier programme disponible dans le futur proche pour cette chaîne
        val anyProg = epgPrograms.firstOrNull { prog ->
            prog.channelId == channel.id || prog.channelId == channel.epgChannelId
        }
        if (anyProg != null) return anyProg

        // 3. Synthétiser un programme réaliste adapté à la chaîne avec horaires dynamiques
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

        // Génération automatique d'une grille complète et variée pour la journée
        return generateDefaultSchedule(channel)
    }

    private fun generateDefaultLiveProgram(channel: Channel, now: Long): EpgProgram {
        val name = channel.name.uppercase()
        val cat = channel.categoryName.uppercase()
        val hash = abs(channel.id.hashCode() * 19 + channel.name.hashCode())

        // Plages horaires calculées de manière déterministe et réaliste
        val elapsedMinutes = (10 + (hash % 45)).toLong()
        val durationMinutes = (50 + ((hash / 5) % 75)).toLong()
        val remainingMinutes = (durationMinutes - elapsedMinutes).coerceAtLeast(10L)

        val startMs = now - (elapsedMinutes * 60 * 1000L)
        val stopMs = now + (remainingMinutes * 60 * 1000L)

        val (title, desc, category) = when {
            // Combats & UFC / MMA
            name.contains("UFC") || name.contains("MMA") || cat.contains("UFC") || cat.contains("COMBAT") -> {
                val ufcTitles = listOf(
                    "UFC : Carte Principale & Choc des Titres en Direct",
                    "UFC Fight Night : Soirée Choc des Poids Lourds",
                    "UFC Countdown : Dans les Coulisses des Combattants",
                    "MMA World Series : Combats Préliminaires & K.O.",
                    "UFC 300 : Le Face-à-face des Champions en Direct"
                )
                Triple(
                    ufcTitles[hash % ufcTitles.size],
                    "Diffusion exclusive en direct haute définition avec analyses des experts et commentaires en direct.",
                    "MMA / Combat"
                )
            }

            // Chaînes TNT / Généralistes Françaises
            name.contains("TF1") -> Triple(
                "Le Journal de 20 Heures en Direct (4K)",
                "Édition spéciale présentée avec l'ensemble des correspondants à travers le monde.",
                "Information"
            )
            name.contains("FRANCE 2") -> Triple(
                "Envoyé Spécial : Grands Reportages",
                "Grandes enquêtes et révélations exclusives de la rédaction d'Élise Lucet.",
                "Magazine"
            )
            name.contains("FRANCE 3") -> Triple(
                "Météo à la Carte & Le 19/20 Régional",
                "Le grand tour des terroirs, du patrimoine et des initiatives régionales.",
                "Actualités"
            )
            name.contains("FRANCE 4") -> Triple(
                "Culturebox en Direct : Festival Live",
                "Concerts, pièces de théâtre et spectacles vivants captés en très haute définition.",
                "Spectacle"
            )
            name.contains("FRANCE 5") -> Triple(
                "C dans l'air : Le Débat du Jour",
                "Caroline Roux et ses experts décryptent les grands enjeux politiques et économiques.",
                "Débat"
            )
            name.contains("M6") -> Triple(
                "Scènes de Ménages & Le 19.45",
                "Humour et actualité en direct avant le grand prime time du soir.",
                "Divertissement"
            )
            name.contains("ARTE") -> Triple(
                "Documentaire Exclusif : Les Mystères du Cosmos",
                "Odyssée spectaculaire aux confins de l'univers avec les images des télescopes spatiaux.",
                "Science"
            )
            name.contains("W9") -> Triple(
                "Hit Talent & Enquêtes d'Action",
                "Les meilleurs clips musicaux suivis du magazine d'intervention sur le terrain.",
                "Magazine"
            )

            // Sport Général & Football
            name.contains("SPORT") || cat.contains("SPORT") || name.contains("BEIN") || name.contains("FOOT") || name.contains("EUROSPORT") -> {
                val sportTitles = listOf(
                    "Grand Match en Direct : Multiplex & Choc Européen",
                    "Soir de Match : Avant-Match, Compositions & Coup d'Envoi",
                    "Le Mag des Sports : Résumés, Débriefs et Palettes Tactiques",
                    "Le Direct Sport : Temps Forts et Buts en Direct",
                    "Grand Format : Les Meilleurs Moments de la Compétition"
                )
                Triple(
                    sportTitles[hash % sportTitles.size],
                    "Retransmission exclusive en direct haute définition sur NoosTV avec commentaires experts.",
                    "Sport"
                )
            }

            // Cinéma & VOD
            name.contains("CINE") || name.contains("FILM") || name.contains("CANAL+") || name.contains("OCS") || name.contains("HBO") || cat.contains("CINE") -> {
                val movieTitles = listOf(
                    "Le Grand Film du Soir en Direct 4K UHD",
                    "Cinéma Passion : Séance Blockbuster & Révélations",
                    "Soirée Cinéma Culte : Version Intégrale Remastérisée",
                    "Grand Frisson : Thriller & Action en Avant-Première",
                    "Séance Prestige : Succès du Box-Office International"
                )
                Triple(
                    movieTitles[hash % movieTitles.size],
                    "Diffusion cinématographique en très haute définition avec son multicanal surround.",
                    "Cinéma"
                )
            }

            // Information & News en continu
            name.contains("NEWS") || name.contains("INFO") || name.contains("BFM") || name.contains("CNEWS") || name.contains("LCI") || cat.contains("INFO") -> {
                val newsTitles = listOf(
                    "Le Fil de l'Info en Continu & Édition Spéciale (4K)",
                    "Grand Journal : Toute l'Actualité Nationale et Internationale",
                    "Le Débat du Jour : Analyses Économiques et Politiques",
                    "Le Grand Direct : Les Correspondants à Travers le Monde"
                )
                Triple(
                    newsTitles[hash % newsTitles.size],
                    "L'actualité en direct 24h/24 avec les rédactions et envoyés spéciaux sur le terrain.",
                    "Information"
                )
            }

            // Jeunesse & Enfants
            name.contains("GULLI") || name.contains("DISNEY") || name.contains("CARTOON") || name.contains("NICK") || cat.contains("JEUN") -> {
                Triple(
                    "Les Grandes Aventures Animées en Famille",
                    "Les meilleures aventures animées pour toute la famille en haute définition.",
                    "Jeunesse"
                )
            }

            // Documentaires
            name.contains("DOC") || name.contains("DISCOVERY") || name.contains("GEO") || cat.contains("DOC") -> {
                Triple(
                    "Aux Frontières de la Terre : Grand Reportage",
                    "Expédition scientifique et exploration des merveilles de notre planète.",
                    "Documentaire"
                )
            }

            // Chaîne personnalisée / Autre
            else -> {
                val fallbackTitles = listOf(
                    "Diffusion en Direct : ${channel.name}",
                    "Programme Spécial en Direct • ${channel.name}",
                    "Le Mag en Direct • ${channel.name}",
                    "Direct & Événements • ${channel.name}"
                )
                Triple(
                    fallbackTitles[hash % fallbackTitles.size],
                    "Diffusion haute définition en direct sur NoosTV avec flux optimisé et son stéréo multicanal.",
                    if (channel.categoryName.isNotBlank()) channel.categoryName else "Généraliste"
                )
            }
        }

        return EpgProgram(
            id = "auto_${channel.id}_live",
            channelId = channel.id,
            title = title,
            description = desc,
            startEpochMs = startMs,
            stopEpochMs = stopMs,
            category = category,
            iconUrl = null, // Laisse getProgramBackdrop choisir dans le pool thématique varié
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
            stopEpochMs = live.stopEpochMs + (90 * 60 * 1000L),
            category = live.category,
            iconUrl = null,
            hasCatchup = true
        )

        val next2 = EpgProgram(
            id = "auto_${channel.id}_next2",
            channelId = channel.id,
            title = "Deuxième Partie de Soirée & Débat",
            description = "Prolongement de la soirée avec analyses approfondies et retours sur les moments forts.",
            startEpochMs = next1.stopEpochMs,
            stopEpochMs = next1.stopEpochMs + (60 * 60 * 1000L),
            category = "Magazine",
            iconUrl = null,
            hasCatchup = true
        )

        val prev = EpgProgram(
            id = "auto_${channel.id}_prev",
            channelId = channel.id,
            title = "Édition Précédente : Flash & Magazine",
            description = "Retour sur les moments forts de la journée disponible en replay.",
            startEpochMs = live.startEpochMs - (60 * 60 * 1000L),
            stopEpochMs = live.startEpochMs,
            category = live.category,
            iconUrl = null,
            hasCatchup = true
        )

        return listOf(prev, live, next1, next2)
    }
}
