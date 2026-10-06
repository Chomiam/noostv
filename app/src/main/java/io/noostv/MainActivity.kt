package io.noostv

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import io.noostv.core.device.DeviceDetector
import io.noostv.core.entitlement.EntitlementManager
import io.noostv.core.player.PlayerEngine
import io.noostv.core.storage.SessionManager
import io.noostv.data.model.Channel
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
        entitlementManager = EntitlementManager()
        repository = IptvRepository()
        playerEngine = PlayerEngine(this, entitlementManager)
        sessionManager = SessionManager(this)

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

                when (currentScreen) {
                    CurrentScreen.LOGIN -> {
                        LoginScreen(
                            onLoginSuccess = { server, user, pass ->
                                sessionManager.saveCredentials(server, user, pass)
                                Toast.makeText(this@MainActivity, "Connexion IPTV réussie !", Toast.LENGTH_SHORT).show()
                                currentScreen = CurrentScreen.HOME
                            },
                            onDemoSelected = {
                                Toast.makeText(this@MainActivity, "Mode Démonstration 4K HDR activé", Toast.LENGTH_SHORT).show()
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
                                entitlementManager = entitlementManager,
                                onSelectChannel = { startPlayChannel(it) },
                                onSelectMovie = { startPlayMovie(it) },
                                onOpenEpg = { currentScreen = CurrentScreen.EPG },
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
                                playerEngine.stop()
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
                            entitlementManager.upgradeToPremium()
                            showUpgradeDialog = false
                            Toast.makeText(this@MainActivity, "Félicitations ! NoosTV VIP Premium Activé (4K HDR Débloqué)", Toast.LENGTH_LONG).show()
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
