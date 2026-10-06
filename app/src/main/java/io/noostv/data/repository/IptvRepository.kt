package io.noostv.data.repository

import io.noostv.data.api.XtreamCodesClient
import io.noostv.data.model.*
import io.noostv.data.parser.M3UParser
import io.noostv.data.parser.XmlTvParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayInputStream

/**
 * Dépôt central de données pour NoosTV (Live, VOD, Séries, EPG, Favoris).
 */
class IptvRepository(
    private val m3uParser: M3UParser = M3UParser(),
    private val xmlTvParser: XmlTvParser = XmlTvParser(),
    private val xtreamClient: XtreamCodesClient = XtreamCodesClient()
) {
    private val _channels = MutableStateFlow<List<Channel>>(emptyList())
    val channels: StateFlow<List<Channel>> = _channels.asStateFlow()

    private val _movies = MutableStateFlow<List<VodMovie>>(emptyList())
    val movies: StateFlow<List<VodMovie>> = _movies.asStateFlow()

    private val _series = MutableStateFlow<List<Series>>(emptyList())
    val series: StateFlow<List<Series>> = _series.asStateFlow()

    private val _epgPrograms = MutableStateFlow<List<EpgProgram>>(emptyList())
    val epgPrograms: StateFlow<List<EpgProgram>> = _epgPrograms.asStateFlow()

    private val _categories = MutableStateFlow<List<Category>>(emptyList())
    val categories: StateFlow<List<Category>> = _categories.asStateFlow()

    init {
        loadDemoCatalog()
    }

    /**
     * Charge une liste M3U / M3U8
     */
    fun loadM3uPlaylist(m3uContent: String) {
        val parsed = m3uParser.parse(ByteArrayInputStream(m3uContent.toByteArray(Charsets.UTF_8)))
        _channels.value = parsed

        // Extraire les catégories uniques
        val cats = parsed.map { it.categoryName }.distinct().map { name ->
            Category(id = name.lowercase().replace(" ", "_"), name = name, type = CategoryType.LIVE)
        }
        _categories.value = cats
    }

    /**
     * Charge un guide EPG XMLTV
     */
    fun loadXmlTvGuide(xmlContent: String) {
        val parsed = xmlTvParser.parse(ByteArrayInputStream(xmlContent.toByteArray(Charsets.UTF_8)))
        _epgPrograms.value = parsed
    }

    /**
     * Bascule le statut favori d'une chaîne
     */
    fun toggleFavorite(channelId: String) {
        _channels.value = _channels.value.map { ch ->
            if (ch.id == channelId) ch.copy(isFavorite = !ch.isFavorite) else ch
        }
    }

    /**
     * Catalogue de démonstration NoosTV riche avec chaînes 4K HDR, AV1, VOD et Séries
     */
    private fun loadDemoCatalog() {
        val now = System.currentTimeMillis()
        val oneHour = 3600_000L

        val demoChannels = listOf(
            Channel(
                id = "tf1_4k",
                num = 1,
                name = "TF1 4K HDR",
                streamUrl = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8",
                logoUrl = "https://images.unsplash.com/photo-1594909122845-11baa439b7bf?w=128",
                categoryName = "Généraliste",
                isHdr = true,
                resolution = "4K UHD",
                videoCodec = "hevc",
                epgChannelId = "tf1",
                isFavorite = true,
                hasCatchup = true,
                catchupDays = 7
            ),
            Channel(
                id = "france2_uhd",
                num = 2,
                name = "France 2 UHD (AV1 HDR)",
                streamUrl = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8",
                logoUrl = "https://images.unsplash.com/photo-1594909122845-11baa439b7bf?w=128",
                categoryName = "Généraliste",
                isHdr = true,
                resolution = "4K UHD",
                videoCodec = "av1",
                epgChannelId = "france2",
                isFavorite = true,
                hasCatchup = true,
                catchupDays = 7
            ),
            Channel(
                id = "canal_sport",
                num = 3,
                name = "Canal+ Sport 4K Dolby Vision",
                streamUrl = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8",
                logoUrl = "https://images.unsplash.com/photo-1508098682722-e99c43a406b2?w=128",
                categoryName = "Sport",
                isHdr = true,
                resolution = "4K UHD",
                videoCodec = "hevc",
                epgChannelId = "canal_sport",
                isFavorite = true,
                hasCatchup = true,
                catchupDays = 3
            ),
            Channel(
                id = "bein_sports_1",
                num = 4,
                name = "beIN Sports 1 Full HD",
                streamUrl = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8",
                categoryName = "Sport",
                isHdr = false,
                resolution = "1080p",
                videoCodec = "h264",
                epgChannelId = "bein1",
                hasCatchup = true
            ),
            Channel(
                id = "arte_hd",
                num = 7,
                name = "ARTE Concert HD",
                streamUrl = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8",
                categoryName = "Documentaires",
                isHdr = false,
                resolution = "1080p",
                videoCodec = "h264",
                epgChannelId = "arte"
            )
        )

        val demoMovies = listOf(
            VodMovie(
                id = "m1",
                title = "Dune: Deuxième Partie",
                streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                posterUrl = "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=400",
                rating = 8.6f,
                releaseYear = 2024,
                durationMinutes = 166,
                plot = "Paul Atreides s'unit à Chani et aux Fremen pour mener la révolte contre ceux qui ont anéanti sa famille.",
                genres = listOf("Science-Fiction", "Aventure"),
                isHdr = true,
                hdrFormat = "Dolby Vision",
                resolution = "4K UHD",
                videoCodec = "av1"
            ),
            VodMovie(
                id = "m2",
                title = "Oppenheimer",
                streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
                posterUrl = "https://images.unsplash.com/photo-1440404653325-ab127d49abc1?w=400",
                rating = 8.9f,
                releaseYear = 2023,
                durationMinutes = 180,
                plot = "L'histoire du physicien J. Robert Oppenheimer et du développement de la bombe atomique.",
                genres = listOf("Drame", "Histoire"),
                isHdr = true,
                hdrFormat = "HDR10",
                resolution = "4K UHD",
                videoCodec = "hevc"
            ),
            VodMovie(
                id = "m3",
                title = "Interstellar",
                streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4",
                posterUrl = "https://images.unsplash.com/photo-1451187580459-43490279c0fa?w=400",
                rating = 8.7f,
                releaseYear = 2014,
                durationMinutes = 169,
                plot = "Une équipe d'explorateurs franchit un trou de ver pour assurer la survie de l'humanité.",
                genres = listOf("Science-Fiction", "Drame"),
                isHdr = true,
                hdrFormat = "HDR10",
                resolution = "4K UHD",
                videoCodec = "hevc"
            )
        )

        val demoSeries = listOf(
            Series(
                id = "s1",
                title = "The Last of Us",
                posterUrl = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=400",
                rating = 8.8f,
                releaseYear = 2023,
                plot = "Quand le monde est ravagé par une pandémie fongique, Joel doit escorter Ellie, 14 ans, à travers les États-Unis.",
                genres = listOf("Drame", "Horreur", "Action"),
                seasons = listOf(
                    Season(
                        seasonNumber = 1,
                        name = "Saison 1",
                        episodeCount = 9,
                        episodes = listOf(
                            Episode(
                                id = "s1_e1",
                                seriesId = "s1",
                                seasonNumber = 1,
                                episodeNumber = 1,
                                title = "Quand on est perdu dans l'obscurité",
                                streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                                durationMinutes = 81
                            ),
                            Episode(
                                id = "s1_e2",
                                seriesId = "s1",
                                seasonNumber = 1,
                                episodeNumber = 2,
                                title = "Les Infectés",
                                streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                                durationMinutes = 53
                            )
                        )
                    )
                )
            )
        )

        val demoEpg = listOf(
            EpgProgram(
                id = "epg_1",
                channelId = "tf1",
                title = "Journal de 20 Heures",
                description = "Le grand journal télévisé présenté par Gilles Bouleau en direct 4K.",
                startEpochMs = now - (20 * 60 * 1000),
                stopEpochMs = now + (40 * 60 * 1000),
                category = "Information",
                hasCatchup = true
            ),
            EpgProgram(
                id = "epg_2",
                channelId = "france2",
                title = "Envoyé Spécial",
                description = "Reportages exclusifs et enquêtes au coeur de l'actualité.",
                startEpochMs = now - (10 * 60 * 1000),
                stopEpochMs = now + (70 * 60 * 1000),
                category = "Magazine",
                hasCatchup = true
            ),
            EpgProgram(
                id = "epg_3",
                channelId = "canal_sport",
                title = "UEFA Champions League en Direct (4K HDR)",
                description = "Grand match de coupe d'Europe avec son multicanal 5.1 Dolby Atmos.",
                startEpochMs = now - (35 * 60 * 1000),
                stopEpochMs = now + (65 * 60 * 1000),
                category = "Sport",
                hasCatchup = true
            )
        )

        _channels.value = demoChannels
        _movies.value = demoMovies
        _series.value = demoSeries
        _epgPrograms.value = demoEpg
        _categories.value = listOf(
            Category("general", "Généraliste"),
            Category("sport", "Sport"),
            Category("documentaires", "Documentaires")
        )
    }
}
