package io.noostv.ui.player

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import io.noostv.core.player.PlayerEngine
import io.noostv.core.player.TrackInfo
import io.noostv.core.player.VideoTrackInfo
import io.noostv.core.player.PlaybackStats
import io.noostv.core.player.formatDuration
import io.noostv.core.storage.SessionManager
import io.noostv.data.model.Channel
import io.noostv.data.model.EpgProgram
import io.noostv.ui.common.HdrBadge
import io.noostv.ui.common.LiveIndicatorBadge
import io.noostv.ui.common.ResolutionBadge
import io.noostv.ui.theme.*
import io.noostv.ui.tv.EpgProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Onglets disponibles dans le menu des réglages et informations de lecture
 */
enum class PlayerSettingsTab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    AUDIO_SUBS("Audio & Subs", Icons.Default.Subtitles),
    SPEED("Vitesse", Icons.Default.Speed),
    QUALITY("Qualité", Icons.Default.HighQuality),
    INFO("Infos de lecture", Icons.Default.Info)
}

@OptIn(UnstableApi::class)
@Composable
fun NoosPlayerScreen(
    playerEngine: PlayerEngine,
    title: String,
    subtitle: String,
    isLive: Boolean,
    isHdr: Boolean,
    resolution: String,
    codec: String,
    isMobile: Boolean = false,
    channel: Channel? = null,
    channels: List<Channel> = emptyList(),
    epgPrograms: List<EpgProgram> = emptyList(),
    contentId: String? = null,
    sessionManager: SessionManager? = null,
    initialPositionMs: Long = 0L,
    onBack: () -> Unit,
    onNextChannel: (() -> Unit)? = null,
    onPreviousChannel: (() -> Unit)? = null,
    onSelectChannel: ((Channel) -> Unit)? = null,
    onEnterPip: (() -> Unit)? = null,
    isPipMode: Boolean = false
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isOsdVisible by remember { mutableStateOf(true) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var selectedSettingsTab by remember { mutableStateOf(PlayerSettingsTab.INFO) }
    var showZapHud by remember { mutableStateOf(isLive) }
    var seekFeedback by remember { mutableStateOf<String?>(null) }
    var resizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var resizeToastText by remember { mutableStateOf<String?>(null) }
    var actionToastText by remember { mutableStateOf<String?>(null) }

    // Saisie numérique directe télécommande (ex: "1", "12")
    var numericBuffer by remember { mutableStateOf("") }

    val isPlaying by playerEngine.isPlaying.collectAsState()
    val playerError by playerEngine.playerError.collectAsState()
    val audioTracks by playerEngine.availableAudioTracks.collectAsState()
    val subtitleTracks by playerEngine.availableSubtitleTracks.collectAsState()
    val videoTracks by playerEngine.availableVideoTracks.collectAsState()
    val playbackSpeed by playerEngine.playbackSpeed.collectAsState()
    val playbackStats by playerEngine.playbackStats.collectAsState()

    // Focus Requester pour capturer les boutons télécommande TV
    val playerFocusRequester = remember { FocusRequester() }
    val playPauseFocusRequester = remember { FocusRequester() }
    val timelineFocusRequester = remember { FocusRequester() }
    var isTimelineFocused by remember { mutableStateOf(false) }

    // Position et durée VOD
    var currentPositionMs by remember { mutableLongStateOf(playerEngine.currentPosition) }
    var durationMs by remember { mutableLongStateOf(playerEngine.duration) }
    var isUserScrubbing by remember { mutableStateOf(false) }
    var scrubbedPositionMs by remember { mutableFloatStateOf(0f) }

    // Notification visuelle de reprise de lecture
    var showResumeToast by remember { mutableStateOf(initialPositionMs > 10_000L) }
    LaunchedEffect(showResumeToast) {
        if (showResumeToast) {
            delay(5000)
            showResumeToast = false
        }
    }

    // Protection anti-écran de veille Google (FLAG_KEEP_SCREEN_ON)
    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Libération immédiate du cache de pochettes Coil en mémoire pour maximiser la RAM disponible pour ExoPlayer
    LaunchedEffect(Unit) {
        try {
            coil.Coil.imageLoader(context).memoryCache?.clear()
        } catch (_: Throwable) {}
        System.gc()
    }

    // Sauvegarde optimisée de la position de lecture pour la reprise (toutes les 45 secondes au lieu de 5s pour ménager le CPU/flash)
    LaunchedEffect(isPlaying, isLive, contentId) {
        if (!isLive && contentId != null && sessionManager != null) {
            while (true) {
                delay(45_000)
                if (isPlaying && durationMs > 0L) {
                    val cur = playerEngine.currentPosition
                    sessionManager.savePlaybackResume(
                        contentId = contentId,
                        title = title,
                        positionMs = cur,
                        durationMs = durationMs
                    )
                }
            }
        }
    }

    // Sauvegarde immédiate lors de la mise en pause
    LaunchedEffect(isPlaying) {
        if (!isPlaying && !isLive && contentId != null && sessionManager != null) {
            val cur = playerEngine.currentPosition
            val dur = playerEngine.duration
            if (dur > 0L && cur > 0L) {
                sessionManager.savePlaybackResume(
                    contentId = contentId,
                    title = title,
                    positionMs = cur,
                    durationMs = dur
                )
            }
        }
    }

    // Sauvegarde finale à la fermeture du lecteur
    DisposableEffect(contentId) {
        onDispose {
            if (!isLive && contentId != null && sessionManager != null) {
                val cur = playerEngine.currentPosition
                val dur = playerEngine.duration
                if (dur > 0L) {
                    sessionManager.savePlaybackResume(
                        contentId = contentId,
                        title = title,
                        positionMs = cur,
                        durationMs = dur
                    )
                }
            }
        }
    }

    // Programme en cours et suivant pour la chaîne active
    val currentLiveProg = remember(channel?.id, epgPrograms) {
        channel?.let { EpgProvider.getCurrentProgram(it, epgPrograms) }
    }
    val upcomingProg = remember(channel?.id, epgPrograms, currentLiveProg) {
        channel?.let { chan ->
            val sched = EpgProvider.getChannelSchedule(chan, epgPrograms)
            sched.firstOrNull { it.id != currentLiveProg?.id && it.startEpochMs >= (currentLiveProg?.startEpochMs ?: 0L) }
        }
    }

    // Gestion de la touche Retour télécommande
    androidx.activity.compose.BackHandler(enabled = true) {
        if (showSettingsDialog) {
            showSettingsDialog = false
        } else if (isOsdVisible) {
            isOsdVisible = false
        } else {
            onBack()
        }
    }

    // Déverrouille les contraintes de prévisualisation (720p) et active l'auto-focus télécommande
    LaunchedEffect(Unit) {
        playerEngine.setPreviewMode(false)
        delay(150)
        runCatching { playerFocusRequester.requestFocus() }
    }

    // Rafraîchissement régulier de la position VOD UNIQUEMENT quand l'OSD est visible (zéro réévaluation en plein écran passif)
    LaunchedEffect(isPlaying, isOsdVisible) {
        if (isOsdVisible) {
            while (true) {
                if (!isUserScrubbing) {
                    currentPositionMs = playerEngine.currentPosition
                    durationMs = playerEngine.duration
                }
                delay(500)
            }
        }
    }

    // Masquage automatique de l'OSD après 4.5 secondes (si le dialogue n'est pas ouvert et qu'on ne navigue pas sur la timeline)
    var wasOsdVisible by remember { mutableStateOf(false) }
    LaunchedEffect(isOsdVisible, showSettingsDialog, isTimelineFocused, isUserScrubbing) {
        if (isOsdVisible && !showSettingsDialog) {
            if (!wasOsdVisible) {
                wasOsdVisible = true
                delay(100)
                runCatching { playPauseFocusRequester.requestFocus() }
            }
            if (!isTimelineFocused && !isUserScrubbing) {
                delay(4500)
                if (!showSettingsDialog && !isTimelineFocused && !isUserScrubbing) {
                    isOsdVisible = false
                }
            }
        } else if (!isOsdVisible) {
            wasOsdVisible = false
            runCatching { playerFocusRequester.requestFocus() }
        }
    }

    // Masquage automatique du mini-HUD de zapping après 3.5 secondes
    LaunchedEffect(showZapHud, channel?.id) {
        if (showZapHud) {
            delay(3500)
            showZapHud = false
        }
    }

    // Auto-effacement du retour visuel d'avance/retour rapide
    LaunchedEffect(seekFeedback) {
        if (seekFeedback != null) {
            delay(1200)
            seekFeedback = null
        }
    }

    // Auto-effacement de l'indicateur de format d'image
    LaunchedEffect(resizeToastText) {
        if (resizeToastText != null) {
            delay(2000)
            resizeToastText = null
        }
    }

    // Auto-effacement du toast de confirmation d'action
    LaunchedEffect(actionToastText) {
        if (actionToastText != null) {
            delay(2200)
            actionToastText = null
        }
    }

    // Mise à jour périodique des statistiques de lecture UNIQUEMENT si l'OSD ou le dialogue des paramètres est ouvert
    LaunchedEffect(isPlaying, showSettingsDialog, isOsdVisible) {
        if (isPlaying && (isOsdVisible || showSettingsDialog)) {
            while (true) {
                playerEngine.updatePlaybackStats()
                delay(1000)
            }
        }
    }

    // Traitement du tampon numérique télécommande avec debounce de 1 seconde
    LaunchedEffect(numericBuffer) {
        if (numericBuffer.isNotEmpty()) {
            delay(1000)
            val channelNum = numericBuffer.toIntOrNull()
            if (channelNum != null && channels.isNotEmpty()) {
                val targetChannel = channels.find { it.num == channelNum }
                    ?: channels.getOrNull(channelNum - 1)
                if (targetChannel != null) {
                    onSelectChannel?.invoke(targetChannel)
                    showZapHud = true
                }
            }
            numericBuffer = ""
        }
    }

    fun cycleAspectRatio() {
        resizeMode = when (resizeMode) {
            AspectRatioFrameLayout.RESIZE_MODE_FIT -> {
                resizeToastText = "Format : Zoom (16:9 Plein Écran)"
                AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            }
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> {
                resizeToastText = "Format : Étiré (100% Surface)"
                AspectRatioFrameLayout.RESIZE_MODE_FILL
            }
            else -> {
                resizeToastText = "Format : Ajusté (Original)"
                AspectRatioFrameLayout.RESIZE_MODE_FIT
            }
        }
    }

    // ------------------------------------------------------------------
    // ORIENTATION (mobile uniquement)
    //  - Téléphone retourné (paysage) : lecteur plein écran immersif
    //    (barres système masquées) + gestes tactiles actifs.
    //  - Téléphone vertical (portrait) : lecteur en haut (16:9) et
    //    Guide TV par chaîne en bas.
    // ------------------------------------------------------------------
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isPortraitMobile = isMobile && !isLandscape

    val view = LocalView.current
    val activity = view.context as? android.app.Activity

    // Immersion : aucune barre système en paysage mobile, restaurées sinon
    DisposableEffect(isMobile, isLandscape, view) {
        val window = activity?.window
        if (window == null) {
            onDispose { }
        } else {
            if (isMobile && isLandscape) {
                WindowCompat.setDecorFitsSystemWindows(window, false)
                WindowInsetsControllerCompat(window, view).apply {
                    systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    hide(WindowInsetsCompat.Type.systemBars())
                }
            } else if (isMobile) {
                WindowCompat.setDecorFitsSystemWindows(window, true)
                WindowInsetsControllerCompat(window, view).show(WindowInsetsCompat.Type.systemBars())
            }
            onDispose {
                if (isMobile) {
                    WindowCompat.setDecorFitsSystemWindows(window, true)
                    WindowInsetsControllerCompat(window, view).show(WindowInsetsCompat.Type.systemBars())
                }
            }
        }
    }

    // L'écran suit le capteur de rotation pendant la lecture mobile : c'est ce qui
    // permet de détecter le « flip » du téléphone et d'activer gestes + plein écran.
    DisposableEffect(isMobile, activity) {
        if (isMobile && activity != null) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
        }
        onDispose {
            if (isMobile && activity != null) {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                activity.window.attributes = activity.window.attributes.apply { screenBrightness = -1f }
            }
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Hauteur de la bande vidéo 16:9 en portrait : le guide commence en dessous,
        // ainsi son en-tête et ses groupes/catégories restent visibles (et non cachés par la vidéo).
        val playerHeightDp = if (isPortraitMobile) maxWidth * 9f / 16f else 0.dp
        // Guide TV : occupe tout ce qui se trouve SOUS la bande vidéo en portrait.
        if (isPortraitMobile && isLive && onSelectChannel != null && !isPipMode) {
            MobileTvGuidePanel(
                channels = channels,
                epgPrograms = epgPrograms,
                activeChannel = channel,
                onSelectChannel = { selected -> onSelectChannel.invoke(selected) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(1f)
                    .padding(top = playerHeightDp)
            )
        }
        Box(
        modifier = (if (isPortraitMobile) Modifier.align(Alignment.TopCenter).fillMaxWidth().aspectRatio(16f / 9f) else Modifier.fillMaxSize())
            .background(Color.Black)
            .focusRequester(playerFocusRequester)
            .focusable()
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.key) {
                        // OK / Entrée : bascule visibilité OSD
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                            if (!isOsdVisible && !showSettingsDialog) {
                                isOsdVisible = true
                                true
                            } else {
                                false
                            }
                        }
                        // Flèche Haut / Page Up
                        Key.DirectionUp, Key.PageUp -> {
                            if (!isOsdVisible) {
                                if (isLive && onPreviousChannel != null) {
                                    onPreviousChannel()
                                    showZapHud = true
                                    true
                                } else {
                                    isOsdVisible = true
                                    true
                                }
                            } else {
                                false
                            }
                        }
                        // Flèche Bas / Page Down
                        Key.DirectionDown, Key.PageDown -> {
                            if (!isOsdVisible) {
                                if (isLive && onNextChannel != null) {
                                    onNextChannel()
                                    showZapHud = true
                                    true
                                } else {
                                    isOsdVisible = true
                                    true
                                }
                            } else {
                                false
                            }
                        }
                        // Flèche Gauche : Recul rapide 10s en VOD ou zapping en Live
                        Key.DirectionLeft -> {
                            if (!isOsdVisible) {
                                if (!isLive) {
                                    playerEngine.seekBy(-10_000)
                                    currentPositionMs = playerEngine.currentPosition
                                    seekFeedback = "-10s"
                                    isOsdVisible = true
                                    true
                                } else if (onPreviousChannel != null) {
                                    onPreviousChannel()
                                    showZapHud = true
                                    true
                                } else false
                            } else {
                                false
                            }
                        }
                        // Flèche Droite : Avance rapide 30s en VOD ou zapping en Live
                        Key.DirectionRight -> {
                            if (!isOsdVisible) {
                                if (!isLive) {
                                    playerEngine.seekBy(30_000)
                                    currentPositionMs = playerEngine.currentPosition
                                    seekFeedback = "+30s"
                                    isOsdVisible = true
                                    true
                                } else if (onNextChannel != null) {
                                    onNextChannel()
                                    showZapHud = true
                                    true
                                } else false
                            } else {
                                false
                            }
                        }
                        // Touches télécommande TV spécialisées
                        Key.Info, Key.Guide -> {
                            selectedSettingsTab = PlayerSettingsTab.INFO
                            showSettingsDialog = true
                            true
                        }
                        Key.Captions -> {
                            selectedSettingsTab = PlayerSettingsTab.AUDIO_SUBS
                            showSettingsDialog = true
                            true
                        }
                        // Touche Retour
                        Key.Back, Key.Escape -> {
                            if (showSettingsDialog) {
                                showSettingsDialog = false
                                true
                            } else if (isOsdVisible) {
                                isOsdVisible = false
                                true
                            } else {
                                onBack()
                                true
                            }
                        }
                        // Boutons multimédia télécommande
                        Key.MediaPlay -> {
                            playerEngine.resume()
                            true
                        }
                        Key.MediaPause -> {
                            playerEngine.pause()
                            true
                        }
                        Key.MediaPlayPause, Key.Spacebar -> {
                            if (isPlaying) playerEngine.pause() else playerEngine.resume()
                            true
                        }
                        Key.MediaFastForward -> {
                            playerEngine.seekBy(30_000)
                            seekFeedback = "+30s"
                            true
                        }
                        Key.MediaRewind -> {
                            playerEngine.seekBy(-10_000)
                            seekFeedback = "-10s"
                            true
                        }
                        Key.MediaNext -> {
                            onNextChannel?.invoke()
                            showZapHud = true
                            true
                        }
                        Key.MediaPrevious -> {
                            onPreviousChannel?.invoke()
                            showZapHud = true
                            true
                        }
                        // Touches numériques 0 à 9 pour zapping direct
                        Key.Zero -> { numericBuffer += "0"; true }
                        Key.One -> { numericBuffer += "1"; true }
                        Key.Two -> { numericBuffer += "2"; true }
                        Key.Three -> { numericBuffer += "3"; true }
                        Key.Four -> { numericBuffer += "4"; true }
                        Key.Five -> { numericBuffer += "5"; true }
                        Key.Six -> { numericBuffer += "6"; true }
                        Key.Seven -> { numericBuffer += "7"; true }
                        Key.Eight -> { numericBuffer += "8"; true }
                        Key.Nine -> { numericBuffer += "9"; true }
                        else -> false
                    }
                } else false
            }
            .clickable {
                isOsdVisible = !isOsdVisible
                if (isLive && !isOsdVisible) {
                    showZapHud = false
                }
            }
    ) {
        // ------------------ 1. RENDU VIDÉO MATÉRIEL EXOPLAYER ------------------
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    player = playerEngine.exoPlayer
                    keepScreenOn = true
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { playerView ->
                playerView.resizeMode = resizeMode
                playerView.keepScreenOn = true
            }
        )

        // ------------------ 1.5. COUCHE GESTES TACTILES (MOBILE) ------------------
        // Tap = afficher/masquer l'OSD • Double-tap gauche/droite = ±10 s
        // Glissement vertical = volume/luminosité • horizontal = avance/recul
        if (isMobile && !isPipMode) {
            PlayerTouchGestureLayer(
                enabled = !showSettingsDialog,
                isLive = isLive,
                onToggleOsd = { isOsdVisible = !isOsdVisible },
                onTogglePlayPause = {
                    if (isPlaying) playerEngine.pause() else playerEngine.resume()
                },
                onSeekRelative = { deltaMs -> playerEngine.seekBy(deltaMs) },
                onNextChannel = if (isLive) onNextChannel else null,
                onPreviousChannel = if (isLive) onPreviousChannel else null,
                modifier = if (isPortraitMobile) {
                    // La couche de gestes ne couvre que la bande vidéo : le guide du bas
                    // garde ses propres scrolls (listes, groupes) sans être intercepté.
                    Modifier.align(Alignment.TopCenter).fillMaxWidth().aspectRatio(16f / 9f)
                } else {
                    Modifier.fillMaxSize()
                }
            )
        }

        // ------------------ 2. OVERLAY FORMAT D'IMAGE CHANGER ------------------
        AnimatedVisibility(
            visible = resizeToastText != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            resizeToastText?.let { text ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xCC0A0E17))
                        .border(1.5.dp, NoosCyan, RoundedCornerShape(16.dp))
                        .padding(horizontal = 24.dp, vertical = 14.dp)
                ) {
                    Text(text = text, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // ------------------ 2.5. OVERLAY CONFIRMATION ACTION (VITESSE / AUDIO / QUALITÉ) ------------------
        AnimatedVisibility(
            visible = actionToastText != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            actionToastText?.let { text ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xEE0A0E17))
                        .border(2.dp, NoosCyan, RoundedCornerShape(16.dp))
                        .padding(horizontal = 24.dp, vertical = 14.dp)
                ) {
                    Text(text = text, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // ------------------ 3. OVERLAY AVANCE / RECUL RAPIDE ------------------
        AnimatedVisibility(
            visible = seekFeedback != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            seekFeedback?.let { feedback ->
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color(0xBB080A0F))
                        .border(2.dp, NoosCyan, CircleShape)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = if (feedback.startsWith("+")) Icons.Default.FastForward else Icons.Default.FastRewind,
                            contentDescription = null,
                            tint = NoosCyan,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = feedback, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // ------------------ 3.5. BANDEAU DE REPRISE DE LECTURE ------------------
        AnimatedVisibility(
            visible = showResumeToast,
            enter = fadeIn() + slideInVertically(),
            exit = fadeOut() + slideOutVertically(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 70.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xE6162032))
                    .border(1.5.dp, Color(0xFF3888FF), RoundedCornerShape(14.dp))
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(20.dp))
                    Text(
                        text = "Reprise de lecture à ${formatDuration(initialPositionMs)}",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Button(
                        onClick = {
                            playerEngine.seekTo(0)
                            currentPositionMs = 0L
                            showResumeToast = false
                            contentId?.let { sessionManager?.clearPlaybackResume(it) }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF24334D)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.Replay, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Recommencer", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // ------------------ 4. OVERLAY CLAVIER NUMÉRIQUE EN DIRECT ------------------
        AnimatedVisibility(
            visible = numericBuffer.isNotEmpty(),
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(32.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xDD000000))
                    .border(2.dp, NoosCyan, RoundedCornerShape(14.dp))
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text(
                    text = "Chaîne : $numericBuffer",
                    color = NoosCyan,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }

        // ------------------ 5. MINI-HUD DE ZAPPING EN DIRECT (TV & MOBILE) ------------------
        AnimatedVisibility(
            visible = isLive && (showZapHud && !isOsdVisible) && channel != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp, start = 24.dp, end = 24.dp)
        ) {
            channel?.let { chan ->
                Card(
                    modifier = Modifier.fillMaxWidth(0.92f),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xE00C0F17))
                ) {
                    Column(
                        modifier = Modifier
                            .border(1.dp, CardBorderUnfocused, RoundedCornerShape(18.dp))
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Ligne 1 : Logo, Numéro, Nom, Badges
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                if (!chan.logoUrl.isNullOrBlank()) {
                                    SubcomposeAsyncImage(
                                        model = ImageRequest.Builder(LocalContext.current)
                                            .data(chan.logoUrl)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = chan.name,
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(8.dp)),
                                        contentScale = ContentScale.Fit
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(NoosBlue),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = chan.name.take(2).uppercase(),
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                    }
                                }

                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        chan.num?.let { num ->
                                            Text(
                                                text = "$num.",
                                                color = NoosCyan,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Black
                                            )
                                        }
                                        Text(
                                            text = chan.name,
                                            color = Color.White,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        LiveIndicatorBadge()
                                    }
                                    Text(
                                        text = chan.categoryName,
                                        color = TextSecondary,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (chan.isHdr) HdrBadge()
                                ResolutionBadge(resolution = chan.resolution)
                            }
                        }

                        // Ligne 2 : Programme en cours avec barre de progression
                        currentLiveProg?.let { prog ->
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "EN CE MOMENT : ${prog.title}",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = "${prog.timeSlotFormatted} (reste ${prog.remainingMinutes} min)",
                                        color = NoosCyan,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                LinearProgressIndicator(
                                    progress = { prog.progressFraction() },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp)),
                                    color = NoosCyan,
                                    trackColor = Color(0x33FFFFFF)
                                )
                            }
                        }

                        // Ligne 3 : Programme suivant
                        upcomingProg?.let { next ->
                            Text(
                                text = "À SUIVRE : ${next.title} (${next.timeSlotFormatted})",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // ------------------ 5.5 BANNIÈRE D'ERREUR DE LECTURE ------------------
        if (playerError != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xCC000000))
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(SurfaceDark)
                        .border(1.5.dp, AccentRed, RoundedCornerShape(20.dp))
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = AccentRed,
                        modifier = Modifier.size(44.dp)
                    )
                    Text(
                        text = "Erreur de lecture",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = playerError ?: "Impossible de lire la vidéo",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Button(
                        onClick = onBack,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NoosCyan,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Text("Retour au catalogue", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // ------------------ 6. OVERLAY OSD COMPLET ------------------
        AnimatedVisibility(
            visible = isOsdVisible && !isPipMode,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.85f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.90f)
                            )
                        )
                    )
                    .padding(if (isPortraitMobile) 12.dp else 24.dp)
            ) {
                // ==================== BARRE SUPÉRIEURE (TITRE, BADGES & HEURE) ====================
                val selectedVideoTrack = videoTracks.firstOrNull { it.isSelected }
                val effectiveWidth = selectedVideoTrack?.width?.takeIf { it > 0 }
                    ?: playbackStats.width.takeIf { it > 0 }
                val effectiveHeight = selectedVideoTrack?.height?.takeIf { it > 0 }
                    ?: playbackStats.height.takeIf { it > 0 }

                val displayRes = when {
                    selectedVideoTrack?.label?.contains("4K", ignoreCase = true) == true ||
                    (effectiveWidth != null && effectiveWidth >= 3200) ||
                    (effectiveHeight != null && effectiveHeight >= 1800) -> "4K"

                    selectedVideoTrack?.label?.contains("1080", ignoreCase = true) == true ||
                    selectedVideoTrack?.label?.contains("FHD", ignoreCase = true) == true ||
                    (effectiveWidth != null && effectiveWidth >= 1600) ||
                    (effectiveHeight != null && effectiveHeight >= 800) -> "1080p"

                    selectedVideoTrack?.label?.contains("720", ignoreCase = true) == true ||
                    selectedVideoTrack?.label?.contains("HD", ignoreCase = true) == true ||
                    (effectiveWidth != null && effectiveWidth >= 1100) ||
                    (effectiveHeight != null && effectiveHeight >= 600) -> "720p"

                    effectiveHeight != null && effectiveHeight in 360..599 -> "SD"
                    resolution.contains("4K", ignoreCase = true) || resolution.contains("UHD", ignoreCase = true) -> "4K"
                    resolution.contains("1080", ignoreCase = true) || resolution.contains("FHD", ignoreCase = true) -> "1080p"
                    resolution.contains("720", ignoreCase = true) || resolution.contains("HD", ignoreCase = true) -> "720p"
                    resolution.isNotBlank() -> resolution
                    else -> "HD"
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopStart),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        var isBackFocused by remember { mutableStateOf(false) }
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isBackFocused) FocusGlow else SurfaceDark.copy(alpha = 0.85f))
                                .border(1.5.dp, if (isBackFocused) NoosCyan else Color.Transparent, RoundedCornerShape(10.dp))
                                .onFocusChanged { isBackFocused = it.isFocused }
                        ) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour", tint = TextPrimary)
                        }

                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = title,
                                    color = TextPrimary,
                                    fontSize = if (isPortraitMobile) 16.sp else 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (isLive) LiveIndicatorBadge()
                                if (isHdr || playbackStats.hdrInfo.contains("HDR", ignoreCase = true)) {
                                    HdrBadge(text = if (playbackStats.hdrInfo.isNotBlank()) playbackStats.hdrInfo else "HDR10")
                                }
                                ResolutionBadge(resolution = displayRes)
                                if (playbackSpeed != 1.0f) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(NoosCyan.copy(alpha = 0.2f))
                                            .border(1.dp, NoosCyan, RoundedCornerShape(6.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text("${playbackSpeed}x", color = NoosCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            if (!isPortraitMobile) {
                                Text(
                                    text = buildString {
                                        if (subtitle.isNotBlank()) append("$subtitle • ")
                                        append(playbackStats.videoCodec)
                                        if (playbackStats.fps > 0) append(" @ ${playbackStats.fps.toInt()}fps")
                                        if (playbackStats.videoBitrate > 0) append(" • ${String.format(Locale.getDefault(), "%.1f", playbackStats.videoBitrate / 1_000_000f)} Mbps")
                                        append(" • Audio : ${playbackStats.activeAudioLabel}")
                                    },
                                    color = TextSecondary,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // Heure locale en haut à droite
                    val currentTimeString = remember {
                        val sdf = java.text.SimpleDateFormat("HH:mm", Locale.getDefault())
                        mutableStateOf(sdf.format(java.util.Date()))
                    }
                    LaunchedEffect(Unit) {
                        while (true) {
                            val sdf = java.text.SimpleDateFormat("HH:mm", Locale.getDefault())
                            currentTimeString.value = sdf.format(java.util.Date())
                            delay(30_000)
                        }
                    }
                    // En portrait (mobile), l'horloge de la barre d'état Android est déjà visible : on évite le doublon
                    if (!isPortraitMobile) {
                        Text(
                            text = currentTimeString.value,
                            color = TextSecondary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(SurfaceDark.copy(alpha = 0.8f))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }

                // ==================== BARRE INFÉRIEURE CONTRÔLES & OPTIONS OSD ====================
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Pour la VOD : Seekbar interactive avec focus télécommande TV et compteurs de temps
                    if (!isLive) {
                        val maxDur = if (durationMs > 0L) durationMs.toFloat() else 1f
                        val displayPos = (if (isUserScrubbing) scrubbedPositionMs else currentPositionMs.toFloat()).coerceIn(0f, maxDur)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.92f)
                                .focusRequester(timelineFocusRequester)
                                .focusable()
                                .onFocusChanged { isTimelineFocused = it.isFocused }
                                .onPreviewKeyEvent { event ->
                                    if (event.type == KeyEventType.KeyDown) {
                                        when (event.key) {
                                            Key.DirectionLeft -> {
                                                val base = if (isUserScrubbing) scrubbedPositionMs else currentPositionMs.toFloat()
                                                val step = if (durationMs > 0L) maxOf(10_000f, durationMs * 0.01f) else 10_000f // 10s ou 1%
                                                scrubbedPositionMs = maxOf(0f, base - step)
                                                isUserScrubbing = true
                                                seekFeedback = "-${(step / 1000).toInt()}s"
                                                true
                                            }
                                            Key.DirectionRight -> {
                                                val base = if (isUserScrubbing) scrubbedPositionMs else currentPositionMs.toFloat()
                                                val step = if (durationMs > 0L) maxOf(10_000f, durationMs * 0.01f) else 10_000f // 10s ou 1%
                                                scrubbedPositionMs = if (durationMs > 0L) minOf(durationMs.toFloat(), base + step) else (base + step)
                                                isUserScrubbing = true
                                                seekFeedback = "+${(step / 1000).toInt()}s"
                                                true
                                            }
                                            Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                                                if (isUserScrubbing) {
                                                    playerEngine.seekTo(scrubbedPositionMs.toLong())
                                                    currentPositionMs = scrubbedPositionMs.toLong()
                                                    isUserScrubbing = false
                                                } else {
                                                    if (isPlaying) playerEngine.pause() else playerEngine.resume()
                                                }
                                                true
                                            }
                                            Key.DirectionDown -> {
                                                if (isUserScrubbing) {
                                                    playerEngine.seekTo(scrubbedPositionMs.toLong())
                                                    currentPositionMs = scrubbedPositionMs.toLong()
                                                    isUserScrubbing = false
                                                }
                                                coroutineScope.launch {
                                                    runCatching { playPauseFocusRequester.requestFocus() }
                                                }
                                                true
                                            }
                                            else -> false
                                        }
                                    } else false
                                }
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isTimelineFocused) Color(0xFF1E2838) else Color(0x55000000))
                                .border(
                                    width = if (isTimelineFocused) 2.5.dp else 1.dp,
                                    color = if (isTimelineFocused) Color(0xFF3888FF) else Color(0x22FFFFFF),
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                // Badge d'aide et de statut TV
                                if (isTimelineFocused) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Color(0xFF3888FF))
                                                .padding(horizontal = 10.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "◄ ◄  ${formatDuration(displayPos.toLong())}  ► ►",
                                                color = Color.White,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Text(
                                            text = "Appuyez sur OK pour caler • ▼ pour les contrôles",
                                            color = Color(0xFF00E5FF),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = formatDuration(displayPos.toLong()),
                                        color = if (isTimelineFocused) Color(0xFF00E5FF) else Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = if (durationMs > 0L) formatDuration(durationMs) else "--:--",
                                        color = TextSecondary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                Slider(
                                    value = displayPos,
                                    onValueChange = { newValue ->
                                        isUserScrubbing = true
                                        scrubbedPositionMs = newValue
                                    },
                                    onValueChangeFinished = {
                                        playerEngine.seekTo(scrubbedPositionMs.toLong())
                                        currentPositionMs = scrubbedPositionMs.toLong()
                                        isUserScrubbing = false
                                    },
                                    valueRange = 0f..maxDur,
                                    colors = SliderDefaults.colors(
                                        thumbColor = if (isTimelineFocused) Color.White else NoosCyan,
                                        activeTrackColor = if (isTimelineFocused) Color(0xFF3888FF) else NoosCyan,
                                        inactiveTrackColor = Color(0x44FFFFFF)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .focusProperties { canFocus = false }
                                )
                            }
                        }
                    }

                    // Boutons de contrôle de lecture principaux (Play/Pause, Précédent, Suivant)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(if (isPortraitMobile) 16.dp else 24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isLive && onPreviousChannel != null) {
                            var isPrevFocused by remember { mutableStateOf(false) }
                            IconButton(
                                onClick = {
                                    onPreviousChannel()
                                    showZapHud = true
                                },
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isPrevFocused) SurfaceDarkVariant else Color.Transparent)
                                    .border(1.5.dp, if (isPrevFocused) NoosCyan else Color.Transparent, RoundedCornerShape(12.dp))
                                    .onFocusChanged { isPrevFocused = it.isFocused }
                            ) {
                                Icon(imageVector = Icons.Default.SkipPrevious, contentDescription = "Chaîne précédente", tint = TextPrimary, modifier = Modifier.size(if (isPortraitMobile) 28.dp else 34.dp))
                            }
                        } else if (!isLive) {
                            var isReplayFocused by remember { mutableStateOf(false) }
                            IconButton(
                                onClick = {
                                    playerEngine.seekBy(-10_000)
                                    currentPositionMs = playerEngine.currentPosition
                                    seekFeedback = "-10s"
                                },
                                modifier = Modifier
                                    .onPreviewKeyEvent { event ->
                                        if (!isLive && event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp) {
                                            runCatching { timelineFocusRequester.requestFocus() }
                                            true
                                        } else false
                                    }
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isReplayFocused) SurfaceDarkVariant else Color.Transparent)
                                    .border(1.5.dp, if (isReplayFocused) NoosCyan else Color.Transparent, RoundedCornerShape(12.dp))
                                    .onFocusChanged { isReplayFocused = it.isFocused }
                            ) {
                                Icon(imageVector = Icons.Default.Replay10, contentDescription = "Recul 10s", tint = TextPrimary, modifier = Modifier.size(if (isPortraitMobile) 28.dp else 34.dp))
                            }
                        }

                        // Bouton Play / Pause principal
                        var isPlayFocused by remember { mutableStateOf(false) }
                        IconButton(
                            onClick = {
                                if (isPlaying) playerEngine.pause() else playerEngine.resume()
                            },
                            modifier = Modifier
                                .size(if (isPortraitMobile) 48.dp else 56.dp)
                                .clip(RoundedCornerShape(28.dp))
                                .background(NoosCyan)
                                .focusRequester(playPauseFocusRequester)
                                .onPreviewKeyEvent { event ->
                                    if (!isLive && event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp) {
                                        runCatching { timelineFocusRequester.requestFocus() }
                                        true
                                    } else false
                                }
                                .onFocusChanged { isPlayFocused = it.isFocused }
                                .border(if (isPlayFocused) 3.dp else 0.dp, Color.White, RoundedCornerShape(28.dp))
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Lecture",
                                tint = Color.Black,
                                modifier = Modifier.size(if (isPortraitMobile) 28.dp else 34.dp)
                            )
                        }

                        if (isLive && onNextChannel != null) {
                            var isNextFocused by remember { mutableStateOf(false) }
                            IconButton(
                                onClick = {
                                    onNextChannel()
                                    showZapHud = true
                                },
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isNextFocused) SurfaceDarkVariant else Color.Transparent)
                                    .border(1.5.dp, if (isNextFocused) NoosCyan else Color.Transparent, RoundedCornerShape(12.dp))
                                    .onFocusChanged { isNextFocused = it.isFocused }
                            ) {
                                Icon(imageVector = Icons.Default.SkipNext, contentDescription = "Chaîne suivante", tint = TextPrimary, modifier = Modifier.size(if (isPortraitMobile) 28.dp else 34.dp))
                            }
                        } else if (!isLive) {
                            var isForwardFocused by remember { mutableStateOf(false) }
                            IconButton(
                                onClick = {
                                    playerEngine.seekBy(30_000)
                                    currentPositionMs = playerEngine.currentPosition
                                    seekFeedback = "+30s"
                                },
                                modifier = Modifier
                                    .onPreviewKeyEvent { event ->
                                        if (!isLive && event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp) {
                                            runCatching { timelineFocusRequester.requestFocus() }
                                            true
                                        } else false
                                    }
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isForwardFocused) SurfaceDarkVariant else Color.Transparent)
                                    .border(1.5.dp, if (isForwardFocused) NoosCyan else Color.Transparent, RoundedCornerShape(12.dp))
                                    .onFocusChanged { isForwardFocused = it.isFocused }
                            ) {
                                Icon(imageVector = Icons.Default.Forward30, contentDescription = "Avance 30s", tint = TextPrimary, modifier = Modifier.size(if (isPortraitMobile) 28.dp else 34.dp))
                            }
                        }
                    }

                    // ==================== OPTIONS DU LECTEUR EN BAS ====================
                    // En portrait (mobile), l'OSD 16:9 est trop court pour aligner 5 boutons avec le
                    // transport : on les range dans le dialogue Réglages et on n'affiche qu'un bouton
                    // "Paramètres" compact, pour garantir un placement propre de play/précédent/suivant.
                    if (isPortraitMobile) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (onEnterPip != null) {
                                var isPipFocused by remember { mutableStateOf(false) }
                                IconButton(
                                    onClick = { onEnterPip() },
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isPipFocused) SurfaceDarkVariant else SurfaceDark.copy(alpha = 0.85f))
                                        .border(1.5.dp, if (isPipFocused) NoosCyan else Color.Transparent, RoundedCornerShape(12.dp))
                                        .onFocusChanged { isPipFocused = it.isFocused }
                                ) {
                                    Icon(imageVector = Icons.Default.PictureInPicture, contentDescription = "Mode PiP", tint = NoosCyan, modifier = Modifier.size(20.dp))
                                }
                            }
                            var isSettingsFocused by remember { mutableStateOf(false) }
                            Button(
                                onClick = { showSettingsDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark.copy(alpha = 0.85f)),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                                modifier = Modifier
                                    .onFocusChanged { isSettingsFocused = it.isFocused }
                                    .border(1.5.dp, if (isSettingsFocused) NoosCyan else Color.Transparent, RoundedCornerShape(12.dp))
                            ) {
                                Icon(imageVector = Icons.Default.Settings, contentDescription = "Réglages", tint = NoosCyan, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Réglages", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    } else {
                        // FlowRow : sur écran étroit, les boutons se replient proprement
                        FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 0. Bouton Mode PiP (mobile uniquement, via le lecteur plein écran)
                        if (isMobile && onEnterPip != null) {
                            var isPipFocused by remember { mutableStateOf(false) }
                            Button(
                                onClick = { onEnterPip() },
                                colors = ButtonDefaults.buttonColors(containerColor = if (isPipFocused) FocusGlow else SurfaceDark.copy(alpha = 0.85f)),
                                border = if (isPipFocused) androidx.compose.foundation.BorderStroke(1.5.dp, NoosCyan) else null,
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.onFocusChanged { isPipFocused = it.isFocused }
                            ) {
                                Icon(imageVector = Icons.Default.PictureInPicture, contentDescription = "Mode PiP", tint = NoosCyan, modifier = Modifier.size(17.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("PiP", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        // 1. Bouton Infos de lecture
                        var isInfoFocused by remember { mutableStateOf(false) }
                        Button(
                            onClick = {
                                selectedSettingsTab = PlayerSettingsTab.INFO
                                showSettingsDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = if (isInfoFocused) FocusGlow else SurfaceDark.copy(alpha = 0.85f)),
                            border = if (isInfoFocused) androidx.compose.foundation.BorderStroke(1.5.dp, NoosCyan) else null,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.onFocusChanged { isInfoFocused = it.isFocused }
                        ) {
                            Icon(imageVector = Icons.Default.Info, contentDescription = "Infos", tint = NoosCyan, modifier = Modifier.size(17.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Infos", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }

                        // 2. Bouton Vitesse de lecture
                        var isSpeedFocused by remember { mutableStateOf(false) }
                        Button(
                            onClick = {
                                selectedSettingsTab = PlayerSettingsTab.SPEED
                                showSettingsDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = if (isSpeedFocused) FocusGlow else SurfaceDark.copy(alpha = 0.85f)),
                            border = if (isSpeedFocused) androidx.compose.foundation.BorderStroke(1.5.dp, NoosCyan) else null,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.onFocusChanged { isSpeedFocused = it.isFocused }
                        ) {
                            Icon(imageVector = Icons.Default.Speed, contentDescription = null, tint = NoosCyan, modifier = Modifier.size(17.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "Vitesse (${playbackSpeed}x)", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }

                        // 3. Bouton Qualité vidéo
                        var isQualityFocused by remember { mutableStateOf(false) }
                        val qualityBtnLabel = when {
                            selectedVideoTrack != null -> when {
                                selectedVideoTrack.label.contains("4K", ignoreCase = true) ||
                                selectedVideoTrack.width >= 3200 || selectedVideoTrack.height >= 1800 -> "4K"

                                selectedVideoTrack.label.contains("1080", ignoreCase = true) ||
                                selectedVideoTrack.label.contains("FHD", ignoreCase = true) ||
                                selectedVideoTrack.width >= 1600 || selectedVideoTrack.height >= 800 -> "1080p"

                                selectedVideoTrack.label.contains("720", ignoreCase = true) ||
                                selectedVideoTrack.label.contains("HD", ignoreCase = true) ||
                                selectedVideoTrack.width >= 1100 || selectedVideoTrack.height >= 600 -> "720p"

                                selectedVideoTrack.height > 0 -> "${selectedVideoTrack.height}p"
                                else -> selectedVideoTrack.label
                            }
                            playbackStats.width >= 3200 || playbackStats.height >= 1800 -> "Auto (4K)"
                            playbackStats.width >= 1600 || playbackStats.height >= 800 -> "Auto (1080p)"
                            playbackStats.width >= 1100 || playbackStats.height >= 600 -> "Auto (720p)"
                            playbackStats.height > 0 -> "Auto (${playbackStats.height}p)"
                            displayRes != "HD" -> "Auto ($displayRes)"
                            else -> "Qualité Auto"
                        }
                        Button(
                            onClick = {
                                selectedSettingsTab = PlayerSettingsTab.QUALITY
                                showSettingsDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = if (isQualityFocused) FocusGlow else SurfaceDark.copy(alpha = 0.85f)),
                            border = if (isQualityFocused) androidx.compose.foundation.BorderStroke(1.5.dp, NoosCyan) else null,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.onFocusChanged { isQualityFocused = it.isFocused }
                        ) {
                            Icon(imageVector = Icons.Default.HighQuality, contentDescription = null, tint = NoosCyan, modifier = Modifier.size(17.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "Qualité : $qualityBtnLabel", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }

                        // 4. Bouton Audio & Sous-titres
                        var isTrackBtnFocused by remember { mutableStateOf(false) }
                        Button(
                            onClick = {
                                selectedSettingsTab = PlayerSettingsTab.AUDIO_SUBS
                                showSettingsDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = if (isTrackBtnFocused) FocusGlow else SurfaceDark.copy(alpha = 0.85f)),
                            border = if (isTrackBtnFocused) androidx.compose.foundation.BorderStroke(1.5.dp, NoosCyan) else null,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.onFocusChanged { isTrackBtnFocused = it.isFocused }
                        ) {
                            Icon(imageVector = Icons.Default.Subtitles, contentDescription = null, tint = NoosCyan, modifier = Modifier.size(17.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Audio & Subs", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }

                        // 5. Bouton Format d'image (FIT / ZOOM / FILL)
                        var isAspectFocused by remember { mutableStateOf(false) }
                        Button(
                            onClick = { cycleAspectRatio() },
                            colors = ButtonDefaults.buttonColors(containerColor = if (isAspectFocused) FocusGlow else SurfaceDark.copy(alpha = 0.85f)),
                            border = if (isAspectFocused) androidx.compose.foundation.BorderStroke(1.5.dp, NoosCyan) else null,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.onFocusChanged { isAspectFocused = it.isFocused }
                        ) {
                            Icon(imageVector = Icons.Default.AspectRatio, contentDescription = "Format", tint = NoosCyan, modifier = Modifier.size(17.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = when (resizeMode) {
                                    AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "Format 16:9"
                                    AspectRatioFrameLayout.RESIZE_MODE_FILL -> "Format Étiré"
                                    else -> "Format Ajusté"
                                },
                                color = TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            }
                        }
                    }
                    }
                }
            }

        // ------------------ 7. DIALOGUE RÉGLAGES & INFORMATIONS (AUDIO, SUBS, VITESSE, QUALITÉ, STATS) ------------------
        if (showSettingsDialog) {
            PlayerSettingsDialog(
                initialTab = selectedSettingsTab,
                audioTracks = audioTracks,
                subtitleTracks = subtitleTracks,
                videoTracks = videoTracks,
                playbackSpeed = playbackSpeed,
                playbackStats = playbackStats,
                onSelectSpeed = { newSpeed ->
                    playerEngine.setPlaybackSpeed(newSpeed)
                    actionToastText = "Vitesse : ${newSpeed}x"
                },
                onSelectVideoTrack = { vTrack ->
                    playerEngine.selectVideoTrack(vTrack)
                    actionToastText = if (vTrack == null) "Qualité : Automatique" else "Qualité : ${vTrack.label}"
                },
                onSelectAudio = { aTrack ->
                    playerEngine.selectAudioTrack(aTrack)
                    actionToastText = "Audio : ${aTrack.label}"
                },
                onSelectSubtitle = { sTrack ->
                    playerEngine.selectSubtitleTrack(sTrack)
                    actionToastText = if (sTrack == null) "Sous-titres : Désactivés" else "Sous-titres : ${sTrack.label}"
                },
                onDismiss = { showSettingsDialog = false }
            )
        }
        } // fin Box lecteur
    } // fin Box racine
}

/**
 * Dialogue modulaire de réglages et d'informations de lecture (Audio, Sous-titres, Vitesse, Qualité, Stats techniques)
 * Entièrement compatible D-Pad Android TV et tactile Mobile.
 */
@Composable
fun PlayerSettingsDialog(
    initialTab: PlayerSettingsTab,
    audioTracks: List<TrackInfo>,
    subtitleTracks: List<TrackInfo>,
    videoTracks: List<VideoTrackInfo>,
    playbackSpeed: Float,
    playbackStats: PlaybackStats,
    onSelectSpeed: (Float) -> Unit,
    onSelectVideoTrack: (VideoTrackInfo?) -> Unit,
    onSelectAudio: (TrackInfo) -> Unit,
    onSelectSubtitle: (TrackInfo?) -> Unit,
    onDismiss: () -> Unit
) {
    var currentTab by remember { mutableStateOf(initialTab) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDark,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.widthIn(min = 520.dp, max = 680.dp),
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Options & Informations de lecture",
                        color = TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Fermer", tint = TextSecondary, modifier = Modifier.size(20.dp))
                    }
                }

                // Barre de navigation entre onglets
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceDarkVariant)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PlayerSettingsTab.values().forEach { tab ->
                        val isSelected = currentTab == tab
                        var isFocused by remember { mutableStateOf(false) }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    when {
                                        isSelected -> NoosCyan.copy(alpha = 0.25f)
                                        isFocused -> FocusGlow
                                        else -> Color.Transparent
                                    }
                                )
                                .border(
                                    1.dp,
                                    when {
                                        isFocused -> NoosCyan
                                        isSelected -> NoosCyan.copy(alpha = 0.6f)
                                        else -> Color.Transparent
                                    },
                                    RoundedCornerShape(8.dp)
                                )
                                .onFocusChanged { isFocused = it.isFocused }
                                .focusable()
                                .clickable { currentTab = tab }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = tab.label,
                                    tint = if (isSelected || isFocused) NoosCyan else TextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = tab.label,
                                    color = if (isSelected || isFocused) TextPrimary else TextSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 280.dp, max = 380.dp)
            ) {
                when (currentTab) {
                    PlayerSettingsTab.AUDIO_SUBS -> {
                        AudioSubsTabContent(
                            audioTracks = audioTracks,
                            subtitleTracks = subtitleTracks,
                            onSelectAudio = onSelectAudio,
                            onSelectSubtitle = onSelectSubtitle
                        )
                    }
                    PlayerSettingsTab.SPEED -> {
                        SpeedTabContent(
                            currentSpeed = playbackSpeed,
                            onSelectSpeed = onSelectSpeed
                        )
                    }
                    PlayerSettingsTab.QUALITY -> {
                        QualityTabContent(
                            videoTracks = videoTracks,
                            playbackStats = playbackStats,
                            onSelectVideoTrack = onSelectVideoTrack
                        )
                    }
                    PlayerSettingsTab.INFO -> {
                        PlaybackInfoTabContent(
                            stats = playbackStats
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = NoosCyan),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Fermer", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    )
}

/**
 * Contenu de l'onglet Audio et Sous-titres
 */
@Composable
private fun AudioSubsTabContent(
    audioTracks: List<TrackInfo>,
    subtitleTracks: List<TrackInfo>,
    onSelectAudio: (TrackInfo) -> Unit,
    onSelectSubtitle: (TrackInfo?) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Section Audio
        item {
            Text("Pistes Audio :", color = NoosCyan, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }

        if (audioTracks.isEmpty()) {
            item {
                Text("Piste audio principale par défaut (intégrée au flux)", color = TextSecondary, fontSize = 12.sp)
            }
        } else {
            items(audioTracks) { track ->
                var isItemFocused by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isItemFocused) FocusGlow else if (track.isSelected) SurfaceDarkVariant else Color(0x33222B3D))
                        .border(1.dp, if (isItemFocused) NoosCyan else if (track.isSelected) NoosCyan.copy(alpha = 0.5f) else Color.Transparent, RoundedCornerShape(8.dp))
                        .onFocusChanged { isItemFocused = it.isFocused }
                        .focusable()
                        .clickable { onSelectAudio(track) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(track.label, color = TextPrimary, fontSize = 13.sp, fontWeight = if (track.isSelected) FontWeight.Bold else FontWeight.Normal)
                        val audioDetails = listOfNotNull(track.codec?.takeIf { it.isNotBlank() }, track.channels?.takeIf { it.isNotBlank() }).joinToString(" • ")
                        if (audioDetails.isNotBlank()) {
                            Text(audioDetails, color = TextSecondary, fontSize = 11.sp)
                        }
                    }
                    if (track.isSelected) {
                        Text("✓ Active", color = NoosCyan, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }

        // Section Sous-titres
        item {
            Spacer(modifier = Modifier.height(6.dp))
            Text("Sous-titres :", color = NoosCyan, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }

        val isNoneSelected = subtitleTracks.none { it.isSelected }
        item {
            var isNoneFocused by remember { mutableStateOf(false) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isNoneFocused) FocusGlow else if (isNoneSelected) SurfaceDarkVariant else Color(0x33222B3D))
                    .border(1.dp, if (isNoneFocused) NoosCyan else if (isNoneSelected) NoosCyan.copy(alpha = 0.5f) else Color.Transparent, RoundedCornerShape(8.dp))
                    .onFocusChanged { isNoneFocused = it.isFocused }
                    .focusable()
                    .clickable { onSelectSubtitle(null) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Désactivés", color = TextSecondary, fontSize = 13.sp)
                if (isNoneSelected) {
                    Text("✓ Actif", color = NoosCyan, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }

        items(subtitleTracks) { track ->
            var isSubFocused by remember { mutableStateOf(false) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSubFocused) FocusGlow else if (track.isSelected) SurfaceDarkVariant else Color(0x33222B3D))
                    .border(1.dp, if (isSubFocused) NoosCyan else if (track.isSelected) NoosCyan.copy(alpha = 0.5f) else Color.Transparent, RoundedCornerShape(8.dp))
                .onFocusChanged { isSubFocused = it.isFocused }
                .focusable()
                .clickable { onSelectSubtitle(track) }
                .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(track.label, color = TextPrimary, fontSize = 13.sp, fontWeight = if (track.isSelected) FontWeight.Bold else FontWeight.Normal)
                if (track.isSelected) {
                    Text("✓ Actif", color = NoosCyan, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }
}

/**
 * Contenu de l'onglet Vitesse de lecture
 */
@Composable
private fun SpeedTabContent(
    currentSpeed: Float,
    onSelectSpeed: (Float) -> Unit
) {
    val speeds = listOf(
        0.5f to "0.5x (Ralenti x2)",
        0.75f to "0.75x (Ralenti)",
        1.0f to "1.0x (Vitesse normale)",
        1.25f to "1.25x (Légèrement accéléré)",
        1.5f to "1.5x (Accéléré)",
        2.0f to "2.0x (Rapide x2)"
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(speeds) { (speed, label) ->
            val isSelected = kotlin.math.abs(currentSpeed - speed) < 0.05f
            var isFocused by remember { mutableStateOf(false) }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isFocused) FocusGlow else if (isSelected) SurfaceDarkVariant else Color(0x33222B3D))
                    .border(1.dp, if (isFocused) NoosCyan else if (isSelected) NoosCyan.copy(alpha = 0.5f) else Color.Transparent, RoundedCornerShape(8.dp))
                    .onFocusChanged { isFocused = it.isFocused }
                    .focusable()
                    .clickable { onSelectSpeed(speed) }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = label, color = TextPrimary, fontSize = 13.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                if (isSelected) {
                    Text("✓ Actif", color = NoosCyan, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }
}

/**
 * Contenu de l'onglet Qualité vidéo
 */
@Composable
private fun QualityTabContent(
    videoTracks: List<VideoTrackInfo>,
    playbackStats: PlaybackStats,
    onSelectVideoTrack: (VideoTrackInfo?) -> Unit
) {
    val isAutoSelected = videoTracks.none { it.isSelected }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                text = "Sélection de la qualité du flux vidéo :",
                color = NoosCyan,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }

        // Option Automatique
        item {
            var isAutoFocused by remember { mutableStateOf(false) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isAutoFocused) FocusGlow else if (isAutoSelected) SurfaceDarkVariant else Color(0x33222B3D))
                    .border(1.dp, if (isAutoFocused) NoosCyan else if (isAutoSelected) NoosCyan.copy(alpha = 0.5f) else Color.Transparent, RoundedCornerShape(8.dp))
                    .onFocusChanged { isAutoFocused = it.isFocused }
                    .focusable()
                    .clickable { onSelectVideoTrack(null) }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Automatique (Recommandé)", color = TextPrimary, fontSize = 13.sp, fontWeight = if (isAutoSelected) FontWeight.Bold else FontWeight.Normal)
                    Text("Sélectionne automatiquement le profil optimal selon la connexion", color = TextSecondary, fontSize = 11.sp)
                }
                if (isAutoSelected) {
                    Text("✓ Actif", color = NoosCyan, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }

        if (videoTracks.isNotEmpty()) {
            items(videoTracks) { vTrack ->
                var isTrackFocused by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isTrackFocused) FocusGlow else if (vTrack.isSelected) SurfaceDarkVariant else Color(0x33222B3D))
                        .border(1.dp, if (isTrackFocused) NoosCyan else if (vTrack.isSelected) NoosCyan.copy(alpha = 0.5f) else Color.Transparent, RoundedCornerShape(8.dp))
                        .onFocusChanged { isTrackFocused = it.isFocused }
                        .focusable()
                        .clickable { onSelectVideoTrack(vTrack) }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(vTrack.label, color = TextPrimary, fontSize = 13.sp, fontWeight = if (vTrack.isSelected) FontWeight.Bold else FontWeight.Normal)
                        val brInfo = if (vTrack.bitrate > 0) String.format(Locale.getDefault(), "%.1f Mbps", vTrack.bitrate / 1_000_000f) else "Direct"
                        Text("${vTrack.width}x${vTrack.height} • $brInfo", color = TextSecondary, fontSize = 11.sp)
                    }
                    if (vTrack.isSelected) {
                        Text("✓ Sélectionné", color = NoosCyan, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        } else {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x22FFFFFF))
                        .padding(12.dp)
                ) {
                    Text(
                        text = "Flux natif unique : ${if (playbackStats.width > 0) "${playbackStats.width}x${playbackStats.height}" else "Détection en cours"}${if (playbackStats.fps > 0) " @ ${playbackStats.fps.toInt()}fps" else ""} (${playbackStats.videoCodec})\nLe lecteur utilise automatiquement la résolution maximale disponible du serveur.",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

/**
 * Contenu de l'onglet Informations de lecture en temps réel (Stats for nerds)
 */
@Composable
private fun PlaybackInfoTabContent(
    stats: PlaybackStats
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        val aspectRatio = if (stats.height > 0 && stats.width > 0) {
            val gcd = gcd(stats.width, stats.height)
            if (gcd > 0) "${stats.width / gcd}:${stats.height / gcd}" else "16:9"
        } else "16:9"

        val items = listOf(
            "Résolution vidéo" to "${if (stats.width > 0) "${stats.width} × ${stats.height}" else "Détection en cours"} ($aspectRatio)",
            "Fréquence d'images (FPS)" to if (stats.fps > 0) String.format(Locale.getDefault(), "%.1f ips", stats.fps) else "Variable",
            "Débit vidéo (Bitrate)" to if (stats.videoBitrate > 0) String.format(Locale.getDefault(), "%.2f Mbps", stats.videoBitrate / 1_000_000f) else "Direct",
            "Codec vidéo" to "${stats.videoCodec} (Décodage matériel)",
            "Gamme dynamique (HDR)" to stats.hdrInfo,
            "Piste audio active" to stats.activeAudioLabel,
            "Codec & Canaux audio" to "${stats.audioCodec} • ${stats.audioChannels}${if (stats.audioSampleRate > 0) " • ${stats.audioSampleRate / 1000} kHz" else ""}",
            "Débit audio" to if (stats.audioBitrate > 0) "${stats.audioBitrate / 1000} kbps" else "Standard",
            "Sous-titres" to stats.activeSubtitleLabel,
            "Vitesse de lecture" to "${stats.speed}x",
            "Santé du tampon (Buffer)" to "${String.format(Locale.getDefault(), "%.1f s", stats.bufferHealthSeconds)} en avance",
            "Moteur de rendu" to "Media3 ExoPlayer v1.3.1"
        )

        items(items) { (label, value) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0x221B2333))
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = label, color = TextSecondary, fontSize = 12.sp)
                Text(text = value, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

private fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)

/**
 * Formatage millisecondes en chaîne HH:MM:SS ou MM:SS
 */
private fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}
