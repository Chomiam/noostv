package io.noostv

import android.app.PictureInPictureParams
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.noostv.core.audio.LocalSoundEffectManager
import io.noostv.core.audio.SoundEffectManager
import io.noostv.core.localization.AppLanguage
import io.noostv.core.localization.AppStrings
import io.noostv.core.localization.LocalAppLanguage
import io.noostv.core.localization.LocalStrings
import io.noostv.core.localization.LocaleHelper
import kotlinx.coroutines.launch
import io.noostv.core.device.DeviceDetector
import io.noostv.core.entitlement.EntitlementManager
import io.noostv.core.player.PlayerEngine
import io.noostv.core.storage.SessionManager
import io.noostv.data.model.Category
import io.noostv.data.model.Channel
import io.noostv.data.model.Series
import io.noostv.data.model.VodMovie
import io.noostv.data.repository.IptvRepository
import io.noostv.ui.common.SearchScreen
import io.noostv.ui.common.UpgradeDialog
import io.noostv.ui.login.LoginScreen
import io.noostv.ui.mobile.MobileHomeScreen
import io.noostv.ui.player.MiniPlayerBar
import io.noostv.ui.player.NoosPlayerScreen
import io.noostv.ui.theme.NoosTvTheme
import io.noostv.ui.tv.TvHomeScreen

enum class CurrentScreen {
    LOGIN,
    SYNC,
    HOME,
    PLAYER,
    SEARCH
}

class MainActivity : ComponentActivity() {

    private lateinit var deviceDetector: DeviceDetector
    private lateinit var entitlementManager: EntitlementManager
    private lateinit var repository: IptvRepository
    private lateinit var playerEngine: PlayerEngine
    private lateinit var sessionManager: SessionManager
    private lateinit var soundEffectManager: SoundEffectManager

    // Auto-PiP au bouton Home depuis le lecteur (mobile, API 26+) : vrai quand l'écran
    // lecteur est visible. onUserLeaveHint est le seul point fiable pour déclencher le PiP
    // quand l'utilisateur presse Home (Android ne le fait jamais automatiquement).
    @Volatile
    private var autoPipOnLeave = false

    private fun requestPip() {
        if (Build.VERSION.SDK_INT >= 26) {
            try {
                enterPictureInPictureMode(
                    PictureInPictureParams.Builder()
                        .setAspectRatio(Rational(16, 9))
                        .build()
                )
            } catch (_: Exception) {
                // PiP indisponible (ROM/écran incompatible) : on ignore silencieusement.
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (autoPipOnLeave) {
            requestPip()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        deviceDetector = DeviceDetector(this)
        sessionManager = SessionManager(this)
        soundEffectManager = SoundEffectManager(this, sessionManager)
        val initialSub = if (sessionManager.isPremium) {
            io.noostv.core.entitlement.UserSubscription(
                userId = "dev_user",
                tier = io.noostv.core.entitlement.SubscriptionTier.PREMIUM_VIP
            )
        } else {
            io.noostv.core.entitlement.UserSubscription(
                userId = "guest_user",
                tier = io.noostv.core.entitlement.SubscriptionTier.FREE
            )
        }
        entitlementManager = EntitlementManager(initialSub)
        repository = IptvRepository(catalogStore = io.noostv.data.cache.EncryptedCatalogStore(this))
        playerEngine = PlayerEngine(this, entitlementManager)

        setContent {
            var currentLanguage by remember {
                mutableStateOf(AppLanguage.fromCode(sessionManager.appLanguage))
            }

            LaunchedEffect(currentLanguage) {
                LocaleHelper.setAppLocale(this@MainActivity, currentLanguage)
            }

            CompositionLocalProvider(
                LocalAppLanguage provides currentLanguage,
                LocalStrings provides AppStrings.get(currentLanguage),
                LocalSoundEffectManager provides soundEffectManager
            ) {
                NoosTvTheme {
                    val syncProgress by repository.syncProgress.collectAsState()

                    // Si l'utilisateur n'a pas encore configuré d'identifiants, on démarre sur LOGIN
                    // Si déjà connecté, écran de chargement intégral fluide (SYNC) jusqu'à ce que les données soient prêtes
                    var currentScreen by remember {
                        mutableStateOf(
                            when {
                                !sessionManager.isLoggedIn -> CurrentScreen.LOGIN
                                sessionManager.serverUrl.isBlank() -> CurrentScreen.HOME
                                else -> CurrentScreen.SYNC
                            }
                        )
                    }
                var showUpgradeDialog by remember { mutableStateOf(false) }

                val channels by repository.channels.collectAsState()
                val movies by repository.movies.collectAsState()
                val series by repository.series.collectAsState()
                val epgPrograms by repository.epgPrograms.collectAsState()
                val categories by repository.categories.collectAsState()
                val vodCategories by repository.vodCategories.collectAsState()
                val seriesCategories by repository.seriesCategories.collectAsState()
                val isVodLoading by repository.isVodLoading.collectAsState()
                val isSeriesLoading by repository.isSeriesLoading.collectAsState()
                val coroutineScope = rememberCoroutineScope()

                var currentChannel by remember { mutableStateOf<Channel?>(null) }
                var currentMovie by remember { mutableStateOf<VodMovie?>(null) }
                var currentContentId by remember { mutableStateOf<String?>(null) }
                var currentStartPositionMs by remember { mutableStateOf(0L) }
                var isMiniPlayerActive by remember { mutableStateOf(false) }

                // État de navigation & exploration persistant (survit au lecteur, à la recherche et à l'EPG)
                var savedTvNavTab by remember { mutableStateOf(io.noostv.ui.tv.TvNavTab.TV) }
                var savedMobileTab by remember { mutableStateOf(io.noostv.ui.mobile.MobileBottomTab.TV) }

                var savedTvVodCategory by remember { mutableStateOf("Toutes") }
                var savedTvSeriesCategory by remember { mutableStateOf("Toutes") }
                var savedTvMoviePage by remember { mutableIntStateOf(1) }
                var savedTvSeriesPage by remember { mutableIntStateOf(1) }
                var savedTvFocusedMovieId by remember { mutableStateOf<String?>(null) }
                var savedTvFocusedSeriesId by remember { mutableStateOf<String?>(null) }

                var savedMobileVodCategory by remember { mutableStateOf("Toutes") }
                var savedMobileSeriesCategory by remember { mutableStateOf("Toutes") }
                var savedMobileMoviePage by remember { mutableIntStateOf(1) }
                var savedMobileSeriesPage by remember { mutableIntStateOf(1) }

                // Mobile : dès qu'on est dans le lecteur, presser Home met automatiquement la
                // lecture en mini-fenêtre PiP (déclenché par onUserLeaveHint → requestPip).
                LaunchedEffect(currentScreen) {
                    autoPipOnLeave = !deviceDetector.isTv && currentScreen == CurrentScreen.PLAYER
                }

                fun startPlayChannel(channel: Channel) {
                    val success = playerEngine.playStream(
                        url = channel.streamUrl,
                        title = channel.name,
                        isHdrStream = channel.isHdr,
                        is4K = channel.resolution.contains("4K")
                    )
                    if (success) {
                        currentChannel = channel
                        currentMovie = null
                        currentContentId = null
                        currentStartPositionMs = 0L
                        isMiniPlayerActive = false
                        if (deviceDetector.isTv) {
                            savedTvNavTab = io.noostv.ui.tv.TvNavTab.TV
                        } else {
                            savedMobileTab = io.noostv.ui.mobile.MobileBottomTab.TV
                        }
                        currentScreen = CurrentScreen.PLAYER
                    } else {
                        showUpgradeDialog = true
                    }
                }

                var customStreamTitle by remember { mutableStateOf<String?>(null) }
                var customStreamSubtitle by remember { mutableStateOf<String?>(null) }

                fun performLogout() {
                    val s = sessionManager.serverUrl
                    val u = sessionManager.username
                    val p = sessionManager.password
                    if (s.isNotBlank() && u.isNotBlank() && p.isNotBlank()) {
                        sessionManager.saveAccountToHistory(s, u, p, sessionManager.playlistName)
                    }
                    playerEngine.stop()
                    repository.clear()
                    sessionManager.logout()
                    currentChannel = null
                    currentMovie = null
                    customStreamTitle = null
                    customStreamSubtitle = null
                    isMiniPlayerActive = false
                    savedTvNavTab = io.noostv.ui.tv.TvNavTab.TV
                    savedMobileTab = io.noostv.ui.mobile.MobileBottomTab.TV
                    savedTvVodCategory = "Toutes"
                    savedTvSeriesCategory = "Toutes"
                    savedTvMoviePage = 1
                    savedTvSeriesPage = 1
                    savedTvFocusedMovieId = null
                    savedTvFocusedSeriesId = null
                    savedMobileVodCategory = "Toutes"
                    savedMobileSeriesCategory = "Toutes"
                    savedMobileMoviePage = 1
                    savedMobileSeriesPage = 1
                    currentScreen = CurrentScreen.LOGIN
                }

                fun startPlayMovie(movie: VodMovie, startPositionMs: Long = 0L) {
                    val success = playerEngine.playStream(
                        url = movie.streamUrl,
                        title = movie.title,
                        isHdrStream = movie.isHdr,
                        is4K = movie.resolution.contains("4K"),
                        startPositionMs = startPositionMs
                    )
                    if (success) {
                        currentMovie = movie
                        currentChannel = null
                        currentContentId = "movie_${movie.id}"
                        currentStartPositionMs = startPositionMs
                        customStreamTitle = null
                        customStreamSubtitle = null
                        isMiniPlayerActive = false
                        if (deviceDetector.isTv) {
                            savedTvNavTab = io.noostv.ui.tv.TvNavTab.MOVIES
                            savedTvFocusedMovieId = movie.id
                        } else {
                            savedMobileTab = io.noostv.ui.mobile.MobileBottomTab.MOVIES
                        }
                        currentScreen = CurrentScreen.PLAYER
                    } else {
                        showUpgradeDialog = true
                    }
                }

                fun startPlayEpisode(ser: Series, ep: io.noostv.data.model.Episode, startPositionMs: Long = 0L) {
                    val ext = if (ep.containerExtension.isNotBlank()) ep.containerExtension else "mp4"
                    val streamUrl = if (ep.streamUrl.isNotBlank()) ep.streamUrl else "${sessionManager.serverUrl.trimEnd('/')}/series/${sessionManager.username}/${sessionManager.password}/${ep.id}.$ext"
                    val epSubtitle = "S${ep.seasonNumber}E${ep.episodeNumber}: ${ep.title}"
                    val success = playerEngine.playStream(
                        url = streamUrl,
                        title = "${ser.title} - $epSubtitle",
                        isHdrStream = false,
                        is4K = false,
                        startPositionMs = startPositionMs
                    )
                    if (success) {
                        currentMovie = null
                        currentChannel = null
                        currentContentId = "ep_${ep.id}"
                        currentStartPositionMs = startPositionMs
                        customStreamTitle = ser.title
                        customStreamSubtitle = epSubtitle
                        isMiniPlayerActive = false
                        if (deviceDetector.isTv) {
                            savedTvNavTab = io.noostv.ui.tv.TvNavTab.SERIES
                            savedTvFocusedSeriesId = ser.id
                        } else {
                            savedMobileTab = io.noostv.ui.mobile.MobileBottomTab.SERIES
                        }
                        currentScreen = CurrentScreen.PLAYER
                    } else {
                        showUpgradeDialog = true
                    }
                }

                fun startPlaySeries(ser: Series) {
                    coroutineScope.launch {
                        var targetSeries = ser
                        if ((targetSeries.seasons.isEmpty() || targetSeries.seasons.all { it.episodes.size <= 1 }) && sessionManager.isLoggedIn) {
                            val fetched = repository.getOrFetchSeriesInfo(
                                sessionManager.serverUrl,
                                sessionManager.username,
                                sessionManager.password,
                                ser.id
                            )
                            if (fetched != null && fetched.seasons.isNotEmpty()) {
                                targetSeries = fetched
                            }
                        }
                        val ep = targetSeries.seasons.firstOrNull()?.episodes?.firstOrNull()
                        if (ep != null) {
                            val epResume = sessionManager.getPlaybackResume("ep_${ep.id}")
                            startPlayEpisode(targetSeries, ep, epResume?.positionMs ?: 0L)
                        } else {
                            val ext = "mp4"
                            val streamUrl = "${sessionManager.serverUrl.trimEnd('/')}/series/${sessionManager.username}/${sessionManager.password}/${targetSeries.id}.$ext"
                            val serResume = sessionManager.getPlaybackResume("ser_${targetSeries.id}")
                            val success = playerEngine.playStream(
                                url = streamUrl,
                                title = targetSeries.title,
                                isHdrStream = false,
                                is4K = false,
                                startPositionMs = serResume?.positionMs ?: 0L
                            )
                            if (success) {
                                currentMovie = null
                                currentChannel = null
                                currentContentId = "ser_${targetSeries.id}"
                                currentStartPositionMs = serResume?.positionMs ?: 0L
                                customStreamTitle = targetSeries.title
                                customStreamSubtitle = "Série"
                                isMiniPlayerActive = false
                                if (deviceDetector.isTv) {
                                    savedTvNavTab = io.noostv.ui.tv.TvNavTab.SERIES
                                    savedTvFocusedSeriesId = targetSeries.id
                                } else {
                                    savedMobileTab = io.noostv.ui.mobile.MobileBottomTab.SERIES
                                }
                                currentScreen = CurrentScreen.PLAYER
                            } else {
                                showUpgradeDialog = true
                            }
                        }
                    }
                }

                // Chargement automatique : cache instantané si disponible (< 50ms), sinon écran de synchronisation
                LaunchedEffect(sessionManager.isLoggedIn) {
                    if (sessionManager.isLoggedIn) {
                        if (sessionManager.serverUrl.isNotBlank()) {
                            val hasCache = repository.hasCachedCatalog(sessionManager.serverUrl, sessionManager.username)
                            val cacheLoaded = if (hasCache) {
                                repository.loadFromCache(sessionManager.serverUrl, sessionManager.username)
                            } else false

                            if (!cacheLoaded) {
                                currentScreen = CurrentScreen.SYNC
                                repository.loadFromXtream(
                                    sessionManager.serverUrl,
                                    sessionManager.username,
                                    sessionManager.password
                                )
                            }
                        } else {
                            repository.loadDemoCatalog()
                        }
                    }
                }

                // Transition automatique de SYNC vers HOME dès que le catalogue est prêt
                LaunchedEffect(syncProgress?.isFinished) {
                    if (syncProgress?.isFinished == true && currentScreen == CurrentScreen.SYNC) {
                        kotlinx.coroutines.delay(280)
                        currentScreen = CurrentScreen.HOME
                    }
                }

                // Bouton Retour : sur mobile, revenir du lecteur affiche le bandeau mini-lecteur
                // (lecture continue) au lieu de fermer ; sur TV, retour classique vers l'accueil.
                BackHandler(enabled = currentScreen != CurrentScreen.HOME && currentScreen != CurrentScreen.SYNC) {
                    if (!deviceDetector.isTv && currentScreen == CurrentScreen.PLAYER) {
                        isMiniPlayerActive = true
                        currentScreen = CurrentScreen.HOME
                        return@BackHandler
                    }
                    if (currentScreen == CurrentScreen.PLAYER && currentChannel == null) {
                        playerEngine.stop()
                    }
                    currentScreen = CurrentScreen.HOME
                }

                val refreshCatalogAction: () -> Unit = {
                    currentScreen = CurrentScreen.SYNC
                    coroutineScope.launch {
                        repository.loadFromXtream(sessionManager.serverUrl, sessionManager.username, sessionManager.password)
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                when (currentScreen) {
                    CurrentScreen.LOGIN -> {
                        LoginScreen(
                            sessionManager = sessionManager,
                            onLoginSuccess = { server, user, pass ->
                                sessionManager.saveCredentials(server, user, pass)
                                sessionManager.isPremium = true
                                entitlementManager.upgradeToPremium("dev_vip_user")
                                currentScreen = CurrentScreen.SYNC
                                coroutineScope.launch {
                                    repository.loadFromXtream(server, user, pass)
                                }
                            },
                            onDemoSelected = {
                                entitlementManager.upgradeToPremium("dev_vip_user")
                                sessionManager.isPremium = true
                                repository.loadDemoCatalog()
                                Toast.makeText(this@MainActivity, "✨ Mode Démo 4K HDR & VIP Activé !", Toast.LENGTH_SHORT).show()
                                currentScreen = CurrentScreen.HOME
                            }
                        )
                    }

                    CurrentScreen.SYNC -> {
                        io.noostv.ui.tv.TvCatalogSyncScreen(
                            syncProgress = syncProgress,
                            onRetry = {
                                coroutineScope.launch {
                                    repository.loadFromXtream(
                                        sessionManager.serverUrl,
                                        sessionManager.username,
                                        sessionManager.password
                                    )
                                }
                            },
                            onLogout = { performLogout() }
                        )
                    }

                    CurrentScreen.HOME -> {
                        if (deviceDetector.isTv) {
                            TvHomeScreen(
                                channels = channels,
                                movies = movies,
                                series = series,
                                epgPrograms = epgPrograms,
                                categories = categories,
                                vodCategories = vodCategories,
                                seriesCategories = seriesCategories,
                                isVodLoading = isVodLoading,
                                isSeriesLoading = isSeriesLoading,
                                sessionManager = sessionManager,
                                entitlementManager = entitlementManager,
                                playerEngine = playerEngine,
                                initialPreviewChannel = currentChannel,
                                onClearCurrentChannel = { currentChannel = null },
                                onSelectChannel = { startPlayChannel(it) },
                                onLoadChannelEpg = { chan ->
                                    coroutineScope.launch {
                                        repository.loadChannelEpg(
                                            sessionManager.serverUrl,
                                            sessionManager.username,
                                            sessionManager.password,
                                            chan.id
                                        )
                                    }
                                },
                                onLoadChannelsBatch = { chs ->
                                    coroutineScope.launch {
                                        repository.loadEpgForChannels(
                                            sessionManager.serverUrl,
                                            sessionManager.username,
                                            sessionManager.password,
                                            chs
                                        )
                                    }
                                },
                                onSelectMovie = { movie, startPos -> startPlayMovie(movie, startPos) },
                                onSelectSeries = { startPlaySeries(it) },
                                onSelectEpisode = { ser, ep, startPos -> startPlayEpisode(ser, ep, startPos) },
                                onFetchVodInfo = { movieId ->
                                    repository.getOrFetchVodInfo(
                                        sessionManager.serverUrl,
                                        sessionManager.username,
                                        sessionManager.password,
                                        movieId
                                    )
                                },
                                onFetchSeriesInfo = { seriesId ->
                                    repository.getOrFetchSeriesInfo(
                                        sessionManager.serverUrl,
                                        sessionManager.username,
                                        sessionManager.password,
                                        seriesId
                                    )
                                },
                                onSelectVodCategory = { cat ->
                                    coroutineScope.launch {
                                        repository.loadVodByCategory(
                                            sessionManager.serverUrl,
                                            sessionManager.username,
                                            sessionManager.password,
                                            cat.id
                                        )
                                    }
                                },
                                onSelectSeriesCategory = { cat ->
                                    coroutineScope.launch {
                                        repository.loadSeriesByCategory(
                                            sessionManager.serverUrl,
                                            sessionManager.username,
                                            sessionManager.password,
                                            cat.id
                                        )
                                    }
                                },
                                onOpenSearch = { currentScreen = CurrentScreen.SEARCH },
                                onOpenUpgrade = { showUpgradeDialog = true },
                                onOpenLogin = { currentScreen = CurrentScreen.LOGIN },
                                onLogout = { performLogout() },
                                onLanguageChanged = { newLang ->
                                    currentLanguage = newLang
                                    sessionManager.appLanguage = newLang.code
                                },
                                initialTab = savedTvNavTab,
                                onTabSelected = { savedTvNavTab = it },
                                initialVodCategory = savedTvVodCategory,
                                onVodCategorySelected = { savedTvVodCategory = it },
                                initialSeriesCategory = savedTvSeriesCategory,
                                onSeriesCategorySelected = { savedTvSeriesCategory = it },
                                initialMoviePage = savedTvMoviePage,
                                onMoviePageChange = { savedTvMoviePage = it },
                                initialSeriesPage = savedTvSeriesPage,
                                onSeriesPageChange = { savedTvSeriesPage = it },
                                initialFocusedMovieId = savedTvFocusedMovieId,
                                onMovieFocused = { savedTvFocusedMovieId = it },
                                initialFocusedSeriesId = savedTvFocusedSeriesId,
                                onSeriesFocused = { savedTvFocusedSeriesId = it },
                                onRefreshCatalog = refreshCatalogAction
                            )
                        } else {
                            MobileHomeScreen(
                                channels = channels,
                                movies = movies,
                                series = series,
                                epgPrograms = epgPrograms,
                                categories = categories,
                                vodCategories = vodCategories,
                                seriesCategories = seriesCategories,
                                isVodLoading = isVodLoading,
                                isSeriesLoading = isSeriesLoading,
                                sessionManager = sessionManager,
                                entitlementManager = entitlementManager,
                                playerEngine = playerEngine,
                                onSelectChannel = { startPlayChannel(it) },
                                onSelectMovie = { startPlayMovie(it) },
                                onSelectSeries = { startPlaySeries(it) },
                                onLoadChannelEpg = { chan ->
                                    coroutineScope.launch {
                                        repository.loadChannelEpg(
                                            sessionManager.serverUrl,
                                            sessionManager.username,
                                            sessionManager.password,
                                            chan.id
                                        )
                                    }
                                },
                                onLoadChannelsBatch = { chs ->
                                    coroutineScope.launch {
                                        repository.loadEpgForChannels(
                                            sessionManager.serverUrl,
                                            sessionManager.username,
                                            sessionManager.password,
                                            chs
                                        )
                                    }
                                },
                                onSelectEpisode = { ser, ep -> startPlayEpisode(ser, ep) },
                                onFetchVodInfo = { movieId ->
                                    repository.getOrFetchVodInfo(
                                        sessionManager.serverUrl,
                                        sessionManager.username,
                                        sessionManager.password,
                                        movieId
                                    )
                                },
                                onFetchSeriesInfo = { seriesId ->
                                    repository.getOrFetchSeriesInfo(
                                        sessionManager.serverUrl,
                                        sessionManager.username,
                                        sessionManager.password,
                                        seriesId
                                    )
                                },
                                onSelectVodCategory = { cat: Category ->
                                    coroutineScope.launch {
                                        repository.loadVodByCategory(
                                            sessionManager.serverUrl,
                                            sessionManager.username,
                                            sessionManager.password,
                                            cat.id
                                        )
                                    }
                                },
                                onSelectSeriesCategory = { cat: Category ->
                                    coroutineScope.launch {
                                        repository.loadSeriesByCategory(
                                            sessionManager.serverUrl,
                                            sessionManager.username,
                                            sessionManager.password,
                                            cat.id
                                        )
                                    }
                                },
                                onOpenSearch = { currentScreen = CurrentScreen.SEARCH },
                                onOpenUpgrade = { showUpgradeDialog = true },
                                onOpenLogin = { currentScreen = CurrentScreen.LOGIN },
                                onLogout = { performLogout() },
                                onLanguageChanged = { newLang ->
                                    currentLanguage = newLang
                                    sessionManager.appLanguage = newLang.code
                                },
                                initialTab = savedMobileTab,
                                onTabSelected = { savedMobileTab = it },
                                initialVodCategory = savedMobileVodCategory,
                                onVodCategorySelected = { savedMobileVodCategory = it },
                                initialSeriesCategory = savedMobileSeriesCategory,
                                onSeriesCategorySelected = { savedMobileSeriesCategory = it },
                                initialMoviePage = savedMobileMoviePage,
                                onMoviePageChange = { savedMobileMoviePage = it },
                                initialSeriesPage = savedMobileSeriesPage,
                                onSeriesPageChange = { savedMobileSeriesPage = it },
                                onRefreshCatalog = refreshCatalogAction
                            )
                        }
                    }

                    CurrentScreen.SEARCH -> {
                        SearchScreen(
                            channels = channels,
                            movies = movies,
                            series = series,
                            epgPrograms = epgPrograms,
                            onSelectChannel = { startPlayChannel(it) },
                            onSelectMovie = { startPlayMovie(it) },
                            onSelectSeries = { startPlaySeries(it) },
                            onBack = { currentScreen = CurrentScreen.HOME }
                        )
                    }

                    CurrentScreen.PLAYER -> {
                        val activeTitle = currentChannel?.name ?: currentMovie?.title ?: customStreamTitle ?: "NoosTV Stream"
                        val activeSubtitle = currentChannel?.categoryName ?: currentMovie?.genres?.joinToString(", ") ?: customStreamSubtitle ?: ""
                        val isLive = currentChannel != null
                        val isHdr = currentChannel?.isHdr ?: currentMovie?.isHdr ?: false
                        val resolution = currentChannel?.resolution ?: currentMovie?.resolution ?: "1080p"
                        val codec = currentChannel?.videoCodec ?: currentMovie?.videoCodec ?: "hevc"

                        NoosPlayerScreen(
                            playerEngine = playerEngine,
                            title = activeTitle,
                            subtitle = activeSubtitle,
                            isLive = isLive,
                            isHdr = isHdr,
                            resolution = resolution,
                            codec = codec,
                            isMobile = !deviceDetector.isTv,
                            channel = currentChannel,
                            channels = channels,
                            epgPrograms = epgPrograms,
                            contentId = currentContentId,
                            sessionManager = sessionManager,
                            initialPositionMs = currentStartPositionMs,
                            onBack = {
                                if (!deviceDetector.isTv) {
                                    // Mobile : retour depuis le lecteur → bandeau mini-lecteur,
                                    // la lecture continue en arrière-plan de l'accueil.
                                    isMiniPlayerActive = true
                                    currentScreen = CurrentScreen.HOME
                                } else {
                                    if (currentChannel == null) {
                                        playerEngine.stop()
                                    }
                                    currentScreen = CurrentScreen.HOME
                                }
                            },
                            onNextChannel = if (isLive && channels.isNotEmpty()) {
                                {
                                    val idx = channels.indexOf(currentChannel)
                                    val next = if (idx >= 0 && idx < channels.size - 1) channels[idx + 1] else channels.first()
                                    startPlayChannel(next)
                                }
                            } else null,
                            onPreviousChannel = if (isLive && channels.isNotEmpty()) {
                                {
                                    val idx = channels.indexOf(currentChannel)
                                    val prev = if (idx > 0) channels[idx - 1] else channels.last()
                                    startPlayChannel(prev)
                                }
                            } else null,
                            onSelectChannel = { startPlayChannel(it) },
                            onEnterPip = if (!deviceDetector.isTv && Build.VERSION.SDK_INT >= 26) { { requestPip() } } else null,
                            isPipMode = false
                        )
                    }
                }

                // Bandeau mini-lecteur (mobile uniquement) : s'affiche en bas de l'accueil au retour
                // du lecteur via le bouton Retour, sans gêner la navigation (overlay aligné en bas).
                if (!deviceDetector.isTv && isMiniPlayerActive) {
                    MiniPlayerBar(
                        playerEngine = playerEngine,
                        title = currentChannel?.name ?: currentMovie?.title ?: customStreamTitle ?: "NoosTV Stream",
                        subtitle = currentChannel?.categoryName ?: currentMovie?.genres?.joinToString(", ") ?: customStreamSubtitle ?: "",
                        onExpand = { currentScreen = CurrentScreen.PLAYER },
                        onClose = {
                            playerEngine.stop()
                            currentChannel = null
                            currentMovie = null
                            customStreamTitle = null
                            customStreamSubtitle = null
                            isMiniPlayerActive = false
                        },
                        modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp)
                    )
                }
                }

                if (showUpgradeDialog) {
                    UpgradeDialog(
                        onDismiss = { showUpgradeDialog = false },
                        onActivatePremium = {
                            entitlementManager.upgradeToPremium("dev_vip_user")
                            sessionManager.isPremium = true
                            showUpgradeDialog = false
                            Toast.makeText(this@MainActivity, "✨ Code Validé ! Accès VIP Premium Activé à vie", Toast.LENGTH_LONG).show()
                        }
                    )
                }
            }
        }
    }
}

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP,
                KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.KEYCODE_DPAD_LEFT,
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    soundEffectManager.playFocus()
                }
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                    soundEffectManager.playSelect()
                }
                KeyEvent.KEYCODE_BACK -> {
                    soundEffectManager.playBack()
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onDestroy() {
        super.onDestroy()
        playerEngine.release()
        soundEffectManager.release()
    }
}
