package io.noostv

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import io.noostv.core.device.DeviceDetector
import io.noostv.core.entitlement.EntitlementManager
import io.noostv.core.player.PlayerEngine
import io.noostv.core.storage.SessionManager
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        deviceDetector = DeviceDetector(this)
        sessionManager = SessionManager(this)
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
                        currentScreen = CurrentScreen.PLAYER
                    } else {
                        showUpgradeDialog = true
                    }
                }

                fun startPlaySeries(ser: Series) {
                    val ep = ser.seasons.firstOrNull()?.episodes?.firstOrNull()
                    val streamUrl = ep?.streamUrl ?: "${sessionManager.serverUrl.trimEnd('/')}/series/${sessionManager.username}/${sessionManager.password}/${ser.id}.mp4"
                    val success = playerEngine.playStream(
                        url = streamUrl,
                        title = "${ser.title} - ${ep?.title ?: "Épisode 1"}",
                        isHdrStream = false,
                        is4K = false
                    )
                    if (success) {
                        currentMovie = null
                        currentChannel = null
                        currentScreen = CurrentScreen.PLAYER
                    } else {
                        showUpgradeDialog = true
                    }
                }

                val coroutineScope = rememberCoroutineScope()

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
                                onSelectMovie = { startPlayMovie(it) },
                                onSelectSeries = { startPlaySeries(it) },
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
                                onOpenLogin = { currentScreen = CurrentScreen.LOGIN }
                            )
                        } else {
                            MobileHomeScreen(
                                channels = channels,
                                movies = movies,
                                series = series,
                                entitlementManager = entitlementManager,
                                onSelectChannel = { startPlayChannel(it) },
                                onSelectMovie = { startPlayMovie(it) },
                                onOpenSearch = { currentScreen = CurrentScreen.SEARCH },
                                onOpenEpg = { currentScreen = CurrentScreen.EPG },
                                onOpenUpgrade = { showUpgradeDialog = true }
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
                            onBack = { currentScreen = CurrentScreen.HOME }
                        )
                    }

                    CurrentScreen.PLAYER -> {
                        val activeTitle = currentChannel?.name ?: currentMovie?.title ?: "NoosTV Stream"
                        val activeSubtitle = currentChannel?.categoryName ?: currentMovie?.genres?.joinToString(", ") ?: ""
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
                            } else null
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

    override fun onDestroy() {
        super.onDestroy()
        playerEngine.release()
    }
}
