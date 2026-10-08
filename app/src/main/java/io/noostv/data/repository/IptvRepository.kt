package io.noostv.data.repository

import io.noostv.data.api.XtreamCodesClient
import io.noostv.data.cache.EncryptedCatalogStore
import io.noostv.data.model.*
import io.noostv.data.parser.M3UParser
import io.noostv.data.parser.XmlTvParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream

/**
 * Dépôt central de données pour NoosTV (Live, VOD, Séries, EPG, Favoris).
 */
class IptvRepository(
    private val m3uParser: M3UParser = M3UParser(),
    private val xmlTvParser: XmlTvParser = XmlTvParser(),
    private val xtreamClient: XtreamCodesClient = XtreamCodesClient(),
    private val catalogStore: EncryptedCatalogStore? = null
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

    private val _vodCategories = MutableStateFlow<List<Category>>(emptyList())
    val vodCategories: StateFlow<List<Category>> = _vodCategories.asStateFlow()

    private val _seriesCategories = MutableStateFlow<List<Category>>(emptyList())
    val seriesCategories: StateFlow<List<Category>> = _seriesCategories.asStateFlow()

    private val _isVodLoading = MutableStateFlow(false)
    val isVodLoading: StateFlow<Boolean> = _isVodLoading.asStateFlow()

    private val _isSeriesLoading = MutableStateFlow(false)
    val isSeriesLoading: StateFlow<Boolean> = _isSeriesLoading.asStateFlow()

    private val _isLiveLoading = MutableStateFlow(false)
    val isLiveLoading: StateFlow<Boolean> = _isLiveLoading.asStateFlow()

    init {
        loadDemoCatalog()
    }

    /**
     * Vide tout l'état du dépôt (catalogue live, VOD, séries, EPG, catégories).
     * Appelé lors de la déconnexion pour ne laisser aucune donnée en mémoire.
     */
    fun clear() {
        _channels.value = emptyList()
        _movies.value = emptyList()
        _series.value = emptyList()
        _epgPrograms.value = emptyList()
        _categories.value = emptyList()
        _vodCategories.value = emptyList()
        _seriesCategories.value = emptyList()
        _isVodLoading.value = false
        _isSeriesLoading.value = false
        _isLiveLoading.value = false
        vodDetailsCache.clear()
        seriesDetailsCache.clear()
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
     * Charge l'ensemble du catalogue en direct depuis un serveur Xtream Codes
     * avec chargement progressif ultra-rapide (<200ms pour le direct)
     */
    suspend fun loadFromXtream(serverUrl: String, username: String, password: String): Result<Unit> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            // 0. CHARGEMENT IMMÉDIAT DU CACHE LOCAL CHIFFRÉ (Cold Start ~30ms)
            if (catalogStore != null) {
                val cachedLive = catalogStore.loadLiveCatalog(serverUrl, username)
                if (cachedLive != null && cachedLive.channels.isNotEmpty()) {
                    _channels.value = cachedLive.channels
                    _categories.value = cachedLive.categories
                    _isLiveLoading.value = false
                }

                val cachedVodCats = catalogStore.loadVodCategories(serverUrl, username)
                if (!cachedVodCats.isNullOrEmpty()) {
                    _vodCategories.value = cachedVodCats
                    val firstVodCatId = cachedVodCats.firstOrNull()?.id ?: ""
                    if (firstVodCatId.isNotBlank()) {
                        catalogStore.loadVodMovies(serverUrl, username, firstVodCatId)?.let {
                            if (it.isNotEmpty()) {
                                _movies.value = it
                                _isVodLoading.value = false
                            }
                        }
                    }
                }

                val cachedSeriesCats = catalogStore.loadSeriesCategories(serverUrl, username)
                if (!cachedSeriesCats.isNullOrEmpty()) {
                    _seriesCategories.value = cachedSeriesCats
                    val firstSeriesCatId = cachedSeriesCats.firstOrNull()?.id ?: ""
                    if (firstSeriesCatId.isNotBlank()) {
                        catalogStore.loadSeries(serverUrl, username, firstSeriesCatId)?.let {
                            if (it.isNotEmpty()) {
                                _series.value = it
                                _isSeriesLoading.value = false
                            }
                        }
                    }
                }
            }

            if (_channels.value.isEmpty()) {
                _isLiveLoading.value = true
            }

            // 1. Catégories direct & Chaînes direct (rapide, ~40Ko)
            val liveCatsResult = xtreamClient.getLiveCategories(serverUrl, username, password)
            val liveCats = liveCatsResult.getOrDefault(emptyList())

            val liveStreamsResult = xtreamClient.getLiveStreams(serverUrl, username, password)
            val liveStreams = liveStreamsResult.getOrDefault(emptyList())

            val catMap = liveCats.associate { it.id to it.name }
            val enrichedChannels = liveStreams.map { ch ->
                val realCatName = catMap[ch.categoryId] ?: ch.categoryName
                ch.copy(categoryName = realCatName)
            }

            // MISE À JOUR DISCRÈTE DU DIRECT TV : préserve les favoris et synchronise le cache
            if (enrichedChannels.isNotEmpty()) {
                val currentFavIds = _channels.value.filter { it.isFavorite }.map { it.id }.toSet()
                val updatedChannels = if (currentFavIds.isNotEmpty()) {
                    enrichedChannels.map { ch -> if (ch.id in currentFavIds) ch.copy(isFavorite = true) else ch }
                } else enrichedChannels

                _channels.value = updatedChannels
                _categories.value = liveCats
                catalogStore?.saveLiveCatalog(serverUrl, username, updatedChannels, liveCats)
            }
            _isLiveLoading.value = false

            // 2. Catégories Films VOD (rapide, ~50Ko)
            val vodCatsResult = xtreamClient.getVodCategories(serverUrl, username, password)
            val vodCats = vodCatsResult.getOrDefault(emptyList())
            if (vodCats.isNotEmpty()) {
                _vodCategories.value = vodCats
                catalogStore?.saveVodCategories(serverUrl, username, vodCats)
            }

            // 3. Charger les films VOD de la première catégorie (catalogue complet pour cette catégorie)
            val firstVodCatId = vodCats.firstOrNull()?.id ?: ""
            if (firstVodCatId.isNotBlank()) {
                if (_movies.value.isEmpty()) _isVodLoading.value = true
                val initialVodResult = xtreamClient.getVodStreams(serverUrl, username, password, categoryId = firstVodCatId, limit = null)
                initialVodResult.onSuccess {
                    _movies.value = it
                    catalogStore?.saveVodMovies(serverUrl, username, firstVodCatId, it)
                }
                _isVodLoading.value = false
            }

            // 4. Catégories Séries (rapide, ~50Ko)
            val seriesCatsResult = xtreamClient.getSeriesCategories(serverUrl, username, password)
            val seriesCats = seriesCatsResult.getOrDefault(emptyList())
            if (seriesCats.isNotEmpty()) {
                _seriesCategories.value = seriesCats
                catalogStore?.saveSeriesCategories(serverUrl, username, seriesCats)
            }

            // 5. Charger les séries de la première catégorie (catalogue complet pour cette catégorie)
            val firstSeriesCatId = seriesCats.firstOrNull()?.id ?: ""
            if (firstSeriesCatId.isNotBlank()) {
                if (_series.value.isEmpty()) _isSeriesLoading.value = true
                val initialSeriesResult = xtreamClient.getSeriesStreams(serverUrl, username, password, categoryId = firstSeriesCatId, limit = null)
                initialSeriesResult.onSuccess {
                    _series.value = it
                    catalogStore?.saveSeries(serverUrl, username, firstSeriesCatId, it)
                }
                _isSeriesLoading.value = false
            }

            // 6. Charger en arrière-plan l'EPG pour les premières chaînes en parallèle (rapide, ~300ms au lieu de 3s)
            val channelsToEpg = enrichedChannels.take(16)
            if (channelsToEpg.isNotEmpty()) {
                val epgList = java.util.Collections.synchronizedList(mutableListOf<EpgProgram>())
                val semaphore = Semaphore(5)
                coroutineScope {
                    channelsToEpg.forEach { ch ->
                        launch {
                            semaphore.withPermit {
                                val epgResult = xtreamClient.getShortEpg(serverUrl, username, password, ch.id)
                                epgResult.onSuccess { progs ->
                                    epgList.addAll(progs)
                                }
                            }
                        }
                    }
                }
                if (epgList.isNotEmpty()) {
                    _epgPrograms.value = epgList.toList()
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            _isLiveLoading.value = false
            _isVodLoading.value = false
            _isSeriesLoading.value = false
            Result.failure(e)
        }
    }

    private val vodDetailsCache = java.util.concurrent.ConcurrentHashMap<String, VodMovie>()
    private val seriesDetailsCache = java.util.concurrent.ConcurrentHashMap<String, Series>()

    /**
     * Récupère ou télécharge les métadonnées complètes d'un film VOD
     */
    suspend fun getOrFetchVodInfo(serverUrl: String, username: String, password: String, vodId: String): VodMovie? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        vodDetailsCache[vodId]?.let { return@withContext it }
        val res = xtreamClient.getVodInfo(serverUrl, username, password, vodId)
        res.getOrNull()?.also { enriched ->
            vodDetailsCache[vodId] = enriched
            _movies.value = _movies.value.map { if (it.id == vodId) enriched else it }
        }
    }

    /**
     * Récupère ou télécharge les métadonnées complètes d'une série avec saisons et épisodes
     */
    suspend fun getOrFetchSeriesInfo(serverUrl: String, username: String, password: String, seriesId: String): Series? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        seriesDetailsCache[seriesId]?.takeIf { it.seasons.isNotEmpty() && it.seasons.any { s -> s.episodes.isNotEmpty() } }?.let { return@withContext it }
        val res = xtreamClient.getSeriesInfo(serverUrl, username, password, seriesId)
        res.getOrNull()?.also { enriched ->
            seriesDetailsCache[seriesId] = enriched
            _series.value = _series.value.map { if (it.id == seriesId) enriched else it }
        }
    }

    /**
     * Charge les films VOD d'une catégorie spécifique (ou de l'ensemble du catalogue si Toutes)
     */
    suspend fun loadVodByCategory(serverUrl: String, username: String, password: String, categoryId: String) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val targetCatId = if (categoryId == "Toutes" || categoryId.isBlank()) null else categoryId

        // Affichage instantané depuis le cache chiffré si disponible
        if (catalogStore != null && targetCatId != null) {
            val cached = catalogStore.loadVodMovies(serverUrl, username, targetCatId)
            if (!cached.isNullOrEmpty()) {
                _movies.value = cached
                _isVodLoading.value = false
            } else {
                _isVodLoading.value = true
            }
        } else {
            _isVodLoading.value = true
        }

        try {
            val vodResult = xtreamClient.getVodStreams(serverUrl, username, password, categoryId = targetCatId, limit = null)
            vodResult.onSuccess { freshList ->
                _movies.value = freshList
                if (targetCatId != null) {
                    catalogStore?.saveVodMovies(serverUrl, username, targetCatId, freshList)
                }
            }
        } finally {
            _isVodLoading.value = false
        }
    }

    /**
     * Charge les séries d'une catégorie spécifique (ou de l'ensemble du catalogue si Toutes)
     */
    suspend fun loadSeriesByCategory(serverUrl: String, username: String, password: String, categoryId: String) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val targetCatId = if (categoryId == "Toutes" || categoryId.isBlank()) null else categoryId

        // Affichage instantané depuis le cache chiffré si disponible
        if (catalogStore != null && targetCatId != null) {
            val cached = catalogStore.loadSeries(serverUrl, username, targetCatId)
            if (!cached.isNullOrEmpty()) {
                _series.value = cached
                _isSeriesLoading.value = false
            } else {
                _isSeriesLoading.value = true
            }
        } else {
            _isSeriesLoading.value = true
        }

        try {
            val seriesResult = xtreamClient.getSeriesStreams(serverUrl, username, password, categoryId = targetCatId, limit = null)
            seriesResult.onSuccess { list ->
                val enriched = list.map { ser ->
                    seriesDetailsCache[ser.id]?.let { cached ->
                        ser.copy(seasons = cached.seasons)
                    } ?: ser
                }
                _series.value = enriched
                if (targetCatId != null) {
                    catalogStore?.saveSeries(serverUrl, username, targetCatId, enriched)
                }
            }
        } finally {
            _isSeriesLoading.value = false
        }
    }

    fun clearDiskCache(serverUrl: String, username: String) {
        catalogStore?.clearAccountCache(serverUrl, username)
    }

    fun clearAllDiskCaches() {
        catalogStore?.clearAllCaches()
    }

    /**
     * Charge l'EPG détaillé pour une chaîne spécifique à la demande (ex: prévisualisation)
     */
    suspend fun loadChannelEpg(serverUrl: String, username: String, password: String, streamId: String) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        if (serverUrl.isBlank()) return@withContext
        try {
            val result = xtreamClient.getShortEpg(serverUrl, username, password, streamId)
            result.onSuccess { progs ->
                if (progs.isNotEmpty()) {
                    val current = _epgPrograms.value.toMutableList()
                    current.removeAll { it.channelId == streamId }
                    current.addAll(progs)
                    _epgPrograms.value = current
                }
            }
        } catch (_: Exception) {}
    }

    private val epgLoadingChannels = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    /**
     * Charge en arrière-plan l'EPG pour un groupe de chaînes (ex: chaînes visibles à l'écran)
     */
    suspend fun loadEpgForChannels(
        serverUrl: String,
        username: String,
        password: String,
        channelsToLoad: List<Channel>
    ) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        if (serverUrl.isBlank() || channelsToLoad.isEmpty()) return@withContext
        val now = System.currentTimeMillis()
        val currentEpg = _epgPrograms.value

        val needed = channelsToLoad.filter { ch ->
            val hasValidEpg = currentEpg.any { p ->
                (p.channelId == ch.id || p.channelId == ch.epgChannelId) && p.isLiveNow(now)
            }
            !hasValidEpg && epgLoadingChannels.add(ch.id)
        }.take(24)

        if (needed.isEmpty()) return@withContext

        try {
            val newProgs = mutableListOf<EpgProgram>()
            val semaphore = Semaphore(6)
            coroutineScope {
                needed.forEach { ch ->
                    launch {
                        semaphore.withPermit {
                            val res = xtreamClient.getShortEpg(serverUrl, username, password, ch.id)
                            res.getOrNull()?.let { progs ->
                                synchronized(newProgs) {
                                    newProgs.addAll(progs)
                                }
                            }
                        }
                    }
                }
            }
            if (newProgs.isNotEmpty()) {
                val updated = _epgPrograms.value.toMutableList()
                val updatedIds = needed.map { it.id }.toSet()
                updated.removeAll { it.channelId in updatedIds }
                updated.addAll(newProgs)
                _epgPrograms.value = updated
            }
        } finally {
            needed.forEach { epgLoadingChannels.remove(it.id) }
        }
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
            // TF1
            EpgProgram(
                id = "epg_tf1_now",
                channelId = "tf1",
                title = "Journal de 20 Heures",
                description = "Le grand journal télévisé présenté par Gilles Bouleau. Enquêtes, météo et direct 4K HDR.",
                startEpochMs = now - (20 * 60 * 1000),
                stopEpochMs = now + (35 * 60 * 1000),
                category = "Information",
                iconUrl = "https://images.unsplash.com/photo-1585829365295-ab7cd400c167?w=600",
                hasCatchup = true
            ),
            EpgProgram(
                id = "epg_tf1_next1",
                channelId = "tf1",
                title = "Koh-Lanta : Les Chasseurs d'Immunité",
                description = "Épisode inédit présenté par Denis Brogniart aux Philippines avec des épreuves mythiques.",
                startEpochMs = now + (35 * 60 * 1000),
                stopEpochMs = now + (155 * 60 * 1000),
                category = "Divertissement",
                iconUrl = "https://images.unsplash.com/photo-1506744038136-46273834b3fb?w=600",
                hasCatchup = true
            ),
            EpgProgram(
                id = "epg_tf1_next2",
                channelId = "tf1",
                title = "Vendredi tout est permis",
                description = "Arthur et ses invités s'amusent avec le décor penché, Speed Quiz et Articule.",
                startEpochMs = now + (155 * 60 * 1000),
                stopEpochMs = now + (235 * 60 * 1000),
                category = "Humour",
                iconUrl = "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=600",
                hasCatchup = true
            ),

            // France 2
            EpgProgram(
                id = "epg_f2_now",
                channelId = "france2",
                title = "Envoyé Spécial",
                description = "Reportages exclusifs et grandes enquêtes de la rédaction d'Élise Lucet.",
                startEpochMs = now - (15 * 60 * 1000),
                stopEpochMs = now + (75 * 60 * 1000),
                category = "Magazine",
                iconUrl = "https://images.unsplash.com/photo-1451187580459-43490279c0fa?w=600",
                hasCatchup = true
            ),
            EpgProgram(
                id = "epg_f2_next1",
                channelId = "france2",
                title = "Complément d'Enquête",
                description = "Tristan Waleckx explore les coulisses du pouvoir et de l'économie moderne.",
                startEpochMs = now + (75 * 60 * 1000),
                stopEpochMs = now + (140 * 60 * 1000),
                category = "Investigation",
                iconUrl = "https://images.unsplash.com/photo-1504711434969-e33886168f5c?w=600",
                hasCatchup = true
            ),

            // Canal+ Sport
            EpgProgram(
                id = "epg_csport_now",
                channelId = "canal_sport",
                title = "UEFA Champions League en Direct (4K HDR)",
                description = "Grand choc européen en direct 4K UHD avec son multicanal 5.1 Dolby Atmos.",
                startEpochMs = now - (35 * 60 * 1000),
                stopEpochMs = now + (65 * 60 * 1000),
                category = "Sport",
                iconUrl = "https://images.unsplash.com/photo-1508098682722-e99c43a406b2?w=600",
                hasCatchup = true
            ),
            EpgProgram(
                id = "epg_csport_next1",
                channelId = "canal_sport",
                title = "Canal Champions Club : Le Débrief",
                description = "Analyses des buts, réactions à chaud dans les vestiaires et palettes tactiques.",
                startEpochMs = now + (65 * 60 * 1000),
                stopEpochMs = now + (125 * 60 * 1000),
                category = "Sport",
                iconUrl = "https://images.unsplash.com/photo-1574629810360-7efbbe195018?w=600",
                hasCatchup = true
            ),

            // beIN Sports 1
            EpgProgram(
                id = "epg_bein1_now",
                channelId = "bein1",
                title = "Club beIN Europe : Multiplex Football",
                description = "Toutes les rencontres des championnats espagnol, italien et allemand en direct.",
                startEpochMs = now - (25 * 60 * 1000),
                stopEpochMs = now + (50 * 60 * 1000),
                category = "Sport",
                iconUrl = "https://images.unsplash.com/photo-1574629810360-7efbbe195018?w=600",
                hasCatchup = true
            ),
            EpgProgram(
                id = "epg_bein1_next1",
                channelId = "bein1",
                title = "NBA Extra",
                description = "Le mag quotidien de la NBA avec Xavier Vaution et Jacques Monclar.",
                startEpochMs = now + (50 * 60 * 1000),
                stopEpochMs = now + (110 * 60 * 1000),
                category = "Sport",
                iconUrl = "https://images.unsplash.com/photo-1546519638-68e109498ffc?w=600",
                hasCatchup = true
            ),

            // ARTE
            EpgProgram(
                id = "epg_arte_now",
                channelId = "arte",
                title = "Les Mystères du Cosmos 4K",
                description = "Voyage immersif au cœur des trous noirs et des galaxies lointaines.",
                startEpochMs = now - (10 * 60 * 1000),
                stopEpochMs = now + (80 * 60 * 1000),
                category = "Documentaire",
                iconUrl = "https://images.unsplash.com/photo-1451187580459-43490279c0fa?w=600",
                hasCatchup = true
            ),
            EpgProgram(
                id = "epg_arte_next1",
                channelId = "arte",
                title = "28 Minutes : Le Magazine d'Actualité",
                description = "Débats de fond et éclairages culturels sur les grands enjeux du monde.",
                startEpochMs = now + (80 * 60 * 1000),
                stopEpochMs = now + (130 * 60 * 1000),
                category = "Société",
                iconUrl = "https://images.unsplash.com/photo-1504711434969-e33886168f5c?w=600",
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
        _vodCategories.value = listOf(
            Category("sf_movies", "Science-Fiction", CategoryType.VOD),
            Category("drame_movies", "Drame", CategoryType.VOD),
            Category("action_movies", "Action", CategoryType.VOD)
        )
        _seriesCategories.value = listOf(
            Category("drama_series", "Drame", CategoryType.SERIES),
            Category("action_series", "Action", CategoryType.SERIES),
            Category("horreur_series", "Horreur", CategoryType.SERIES)
        )
    }
}
