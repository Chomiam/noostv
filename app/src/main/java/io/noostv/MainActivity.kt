package io.noostv

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.runtime.CompositionLocalProvider
import io.noostv.core.audio.LocalSoundEffectManager
import io.noostv.core.audio.SoundEffectManager
import android.view.KeyEvent
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
import io.noostv.ui.player.NoosPlayerScreen
import io.noostv.ui.theme.NoosTvTheme
import io.noostv.ui.tv.TvEpgScreen
import io.noostv.ui.tv.TvHomeScreen

enum class CurrentScreen {
    LOGIN,
    HOME,
    PLAYER,
    EPG,
    SEARCH
}

class MainActivity : ComponentActivity() {

    private lateinit var deviceDetector: DeviceDetector
    private lateinit var entitlementManager: EntitlementManager
    private lateinit var repository: IptvRepository
    private lateinit var playerEngine: PlayerEngine
    private lateinit var sessionManager: SessionManager
    private lateinit var soundEffectManager: SoundEffectManager

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
        repository = IptvRepository()
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
                    // Si l'utilisateur n'a pas encore configuré d'identifiants, on démarre sur LOGIN
                    var currentScreen by remember {
                        mutableStateOf(if (sessionManager.isLoggedIn) CurrentScreen.HOME else CurrentScreen.LOGIN)
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
                    currentScreen = CurrentScreen.LOGIN
                }

                fun startPlayMovie(movie: VodMovie) {
                    val success = playerEngine.playStream(
                        url = movie.streamUrl,
                        title = movie.title,
                        isHdrStream = movie.isHdr,
                        is4K = movie.resolution.contains("4K")
                    )
                    if (success) {
                        currentMovie = movie
                        currentChannel = null
                        customStreamTitle = null
                        customStreamSubtitle = null
                        currentScreen = CurrentScreen.PLAYER
                    } else {
                        showUpgradeDialog = true
                    }
                }

                fun startPlayEpisode(ser: Series, ep: io.noostv.data.model.Episode) {
                    val ext = if (ep.containerExtension.isNotBlank()) ep.containerExtension else "mp4"
                    val streamUrl = if (ep.streamUrl.isNotBlank()) ep.streamUrl else "${sessionManager.serverUrl.trimEnd('/')}/series/${sessionManager.username}/${sessionManager.password}/${ep.id}.$ext"
                    val epSubtitle = "S${ep.seasonNumber}E${ep.episodeNumber}: ${ep.title}"
                    val success = playerEngine.playStream(
                        url = streamUrl,
                        title = "${ser.title} - $epSubtitle",
                        isHdrStream = false,
                        is4K = false
                    )
                    if (success) {
                        currentMovie = null
                        currentChannel = null
                        customStreamTitle = ser.title
                        customStreamSubtitle = epSubtitle
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
                            startPlayEpisode(targetSeries, ep)
                        } else {
                            val ext = "mp4"
                            val streamUrl = "${sessionManager.serverUrl.trimEnd('/')}/series/${sessionManager.username}/${sessionManager.password}/${targetSeries.id}.$ext"
                            val success = playerEngine.playStream(
                                url = streamUrl,
                                title = targetSeries.title,
                                isHdrStream = false,
                                is4K = false
                            )
                            if (success) {
                                currentMovie = null
                                currentChannel = null
                                customStreamTitle = targetSeries.title
                                customStreamSubtitle = "Série"
                                currentScreen = CurrentScreen.PLAYER
                            } else {
                                showUpgradeDialog = true
                            }
                        }
                    }
                }

                // Chargement automatique en arrière-plan si déjà connecté
                LaunchedEffect(sessionManager.isLoggedIn) {
                    if (sessionManager.isLoggedIn && sessionManager.serverUrl.isNotBlank()) {
                        repository.loadFromXtream(
                            sessionManager.serverUrl,
                            sessionManager.username,
                            sessionManager.password
                        )
                    }
                }

                // Gestion du bouton Retour de la télécommande TV pour revenir au menu principal
                BackHandler(enabled = currentScreen != CurrentScreen.HOME) {
                    if (currentScreen == CurrentScreen.PLAYER && currentChannel == null) {
                        playerEngine.stop()
                    }
                    currentScreen = CurrentScreen.HOME
                }

                when (currentScreen) {
                    CurrentScreen.LOGIN -> {
                        LoginScreen(
                            sessionManager = sessionManager,
                            onLoginSuccess = { server, user, pass ->
                                sessionManager.saveCredentials(server, user, pass)
                                sessionManager.isPremium = true
                                entitlementManager.upgradeToPremium("dev_vip_user")
                                Toast.makeText(this@MainActivity, "Connexion réussie ! Chargement du catalogue...", Toast.LENGTH_SHORT).show()
                                coroutineScope.launch {
                                    repository.loadFromXtream(server, user, pass)
                                }
                                currentScreen = CurrentScreen.HOME
                            },
                            onDemoSelected = {
                                entitlementManager.upgradeToPremium("dev_vip_user")
                                sessionManager.isPremium = true
                                Toast.makeText(this@MainActivity, "✨ Mode Démo 4K HDR & VIP Activé !", Toast.LENGTH_SHORT).show()
                                currentScreen = CurrentScreen.HOME
                            }
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
                                onSelectMovie = { startPlayMovie(it) },
                                onSelectSeries = { startPlaySeries(it) },
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
                                }
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
                                }
                            )
                        }
                    }

                    CurrentScreen.EPG -> {
                        TvEpgScreen(
                            channels = channels,
                            epgPrograms = epgPrograms,
                            onSelectChannel = { startPlayChannel(it) },
                            onBack = { currentScreen = CurrentScreen.HOME }
                        )
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
                            channel = currentChannel,
                            channels = channels,
                            epgPrograms = epgPrograms,
                            onBack = {
                                if (currentChannel == null) {
                                    playerEngine.stop()
                                }
                                currentScreen = CurrentScreen.HOME
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
                            onSelectChannel = { startPlayChannel(it) }
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
