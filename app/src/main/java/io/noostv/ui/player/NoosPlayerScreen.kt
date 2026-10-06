package io.noostv.ui.player

import android.view.ViewGroup
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import io.noostv.core.player.PlayerEngine
import io.noostv.core.player.TrackInfo
import io.noostv.data.model.Channel
import io.noostv.data.model.EpgProgram
import io.noostv.ui.common.HdrBadge
import io.noostv.ui.common.LiveIndicatorBadge
import io.noostv.ui.common.ResolutionBadge
import io.noostv.ui.theme.*
import io.noostv.ui.tv.EpgProvider
import kotlinx.coroutines.delay
import java.util.Locale

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
    channel: Channel? = null,
    channels: List<Channel> = emptyList(),
    epgPrograms: List<EpgProgram> = emptyList(),
    onBack: () -> Unit,
    onNextChannel: (() -> Unit)? = null,
    onPreviousChannel: (() -> Unit)? = null,
    onSelectChannel: ((Channel) -> Unit)? = null
) {
    val context = LocalContext.current
    var isOsdVisible by remember { mutableStateOf(true) }
    var showTrackDialog by remember { mutableStateOf(false) }
    var showZapHud by remember { mutableStateOf(isLive) }
    var seekFeedback by remember { mutableStateOf<String?>(null) }
    var resizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var resizeToastText by remember { mutableStateOf<String?>(null) }

    // Saisie numérique directe télécommande (ex: "1", "12")
    var numericBuffer by remember { mutableStateOf("") }

    val isPlaying by playerEngine.isPlaying.collectAsState()
    val audioTracks by playerEngine.availableAudioTracks.collectAsState()
    val subtitleTracks by playerEngine.availableSubtitleTracks.collectAsState()

    // Focus Requester pour capturer les boutons télécommande TV
    val playerFocusRequester = remember { FocusRequester() }
    val playPauseFocusRequester = remember { FocusRequester() }

    // Position et durée VOD
    var currentPositionMs by remember { mutableLongStateOf(playerEngine.currentPosition) }
    var durationMs by remember { mutableLongStateOf(playerEngine.duration) }
    var isUserScrubbing by remember { mutableStateOf(false) }
    var scrubbedPositionMs by remember { mutableFloatStateOf(0f) }

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
        if (showTrackDialog) {
            showTrackDialog = false
        } else if (isOsdVisible) {
            isOsdVisible = false
        } else {
            onBack()
        }
    }

    // Auto-focus sur le lecteur au chargement pour la télécommande TV
    LaunchedEffect(Unit) {
        delay(150)
        runCatching { playerFocusRequester.requestFocus() }
    }

    // Rafraîchissement régulier de la position VOD
    LaunchedEffect(isPlaying) {
        while (true) {
            if (!isUserScrubbing) {
                currentPositionMs = playerEngine.currentPosition
                durationMs = playerEngine.duration
            }
            delay(500)
        }
    }

    // Masquage automatique de l'OSD après 4.5 secondes
    LaunchedEffect(isOsdVisible) {
        if (isOsdVisible) {
            delay(100)
            runCatching { playPauseFocusRequester.requestFocus() }
            delay(4400)
            isOsdVisible = false
        } else {
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(playerFocusRequester)
            .focusable()
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.key) {
                        // OK / Entrée : bascule visibilité OSD
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                            if (!isOsdVisible && !showTrackDialog) {
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
                        // Touche Retour
                        Key.Back, Key.Escape -> {
                            if (showTrackDialog) {
                                showTrackDialog = false
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
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { playerView ->
                playerView.resizeMode = resizeMode
            }
        )

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

        // ------------------ 6. OVERLAY OSD COMPLET ------------------
        AnimatedVisibility(
            visible = isOsdVisible,
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
                    .padding(24.dp)
            ) {
                // ==================== BARRE SUPÉRIEURE OSD ====================
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopStart),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
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
                            Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Retour", tint = TextPrimary)
                        }

                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = title,
                                    color = TextPrimary,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (isLive) LiveIndicatorBadge()
                                if (isHdr) HdrBadge(text = "HDR10 / Dolby Vision")
                                ResolutionBadge(resolution = resolution)
                            }
                            Text(
                                text = "$subtitle • Codec: ${codec.uppercase()} (Matériel)",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }

                    // Boutons d'actions rapides : Format d'image & Audio/Sous-titres
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Bouton Format d'image (FIT / ZOOM / FILL)
                        var isAspectFocused by remember { mutableStateOf(false) }
                        Button(
                            onClick = { cycleAspectRatio() },
                            colors = ButtonDefaults.buttonColors(containerColor = if (isAspectFocused) FocusGlow else SurfaceDark.copy(alpha = 0.85f)),
                            border = if (isAspectFocused) androidx.compose.foundation.BorderStroke(1.5.dp, NoosCyan) else null,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.onFocusChanged { isAspectFocused = it.isFocused }
                        ) {
                            Icon(imageVector = Icons.Default.AspectRatio, contentDescription = "Format", tint = NoosCyan, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = when (resizeMode) {
                                    AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "Zoom 16:9"
                                    AspectRatioFrameLayout.RESIZE_MODE_FILL -> "Étiré"
                                    else -> "Ajusté"
                                },
                                color = TextPrimary,
                                fontSize = 12.sp
                            )
                        }

                        // Bouton Pistes Audio / Sous-titres
                        var isTrackBtnFocused by remember { mutableStateOf(false) }
                        Button(
                            onClick = { showTrackDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = if (isTrackBtnFocused) FocusGlow else SurfaceDark.copy(alpha = 0.85f)),
                            border = if (isTrackBtnFocused) androidx.compose.foundation.BorderStroke(1.5.dp, NoosCyan) else null,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.onFocusChanged { isTrackBtnFocused = it.isFocused }
                        ) {
                            Icon(imageVector = Icons.Default.Subtitles, contentDescription = null, tint = NoosCyan, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Audio & Sous-titres", color = TextPrimary, fontSize = 12.sp)
                        }
                    }
                }

                // ==================== BARRE INFÉRIEURE CONTRÔLES OSD ====================
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Pour la VOD : Seekbar interactive et compteurs de temps
                    if (!isLive && durationMs > 0L) {
                        Column(
                            modifier = Modifier.fillMaxWidth(0.92f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = formatDuration(if (isUserScrubbing) scrubbedPositionMs.toLong() else currentPositionMs),
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = formatDuration(durationMs),
                                    color = TextSecondary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            Slider(
                                value = if (isUserScrubbing) scrubbedPositionMs else currentPositionMs.toFloat(),
                                onValueChange = { newValue ->
                                    isUserScrubbing = true
                                    scrubbedPositionMs = newValue
                                },
                                onValueChangeFinished = {
                                    playerEngine.seekTo(scrubbedPositionMs.toLong())
                                    currentPositionMs = scrubbedPositionMs.toLong()
                                    isUserScrubbing = false
                                },
                                valueRange = 0f..durationMs.toFloat(),
                                colors = SliderDefaults.colors(
                                    thumbColor = NoosCyan,
                                    activeTrackColor = NoosCyan,
                                    inactiveTrackColor = Color(0x44FFFFFF)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    // Boutons de contrôle de lecture
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
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
                                Icon(imageVector = Icons.Default.SkipPrevious, contentDescription = "Chaîne précédente", tint = TextPrimary, modifier = Modifier.size(34.dp))
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
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isReplayFocused) SurfaceDarkVariant else Color.Transparent)
                                    .border(1.5.dp, if (isReplayFocused) NoosCyan else Color.Transparent, RoundedCornerShape(12.dp))
                                    .onFocusChanged { isReplayFocused = it.isFocused }
                            ) {
                                Icon(imageVector = Icons.Default.Replay10, contentDescription = "Recul 10s", tint = TextPrimary, modifier = Modifier.size(34.dp))
                            }
                        }

                        // Bouton Play / Pause principal
                        var isPlayFocused by remember { mutableStateOf(false) }
                        IconButton(
                            onClick = {
                                if (isPlaying) playerEngine.pause() else playerEngine.resume()
                            },
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(28.dp))
                                .background(NoosCyan)
                                .focusRequester(playPauseFocusRequester)
                                .onFocusChanged { isPlayFocused = it.isFocused }
                                .border(if (isPlayFocused) 3.dp else 0.dp, Color.White, RoundedCornerShape(28.dp))
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Lecture",
                                tint = Color.Black,
                                modifier = Modifier.size(34.dp)
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
                                Icon(imageVector = Icons.Default.SkipNext, contentDescription = "Chaîne suivante", tint = TextPrimary, modifier = Modifier.size(34.dp))
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
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isForwardFocused) SurfaceDarkVariant else Color.Transparent)
                                    .border(1.5.dp, if (isForwardFocused) NoosCyan else Color.Transparent, RoundedCornerShape(12.dp))
                                    .onFocusChanged { isForwardFocused = it.isFocused }
                            ) {
                                Icon(imageVector = Icons.Default.Forward30, contentDescription = "Avance 30s", tint = TextPrimary, modifier = Modifier.size(34.dp))
                            }
                        }
                    }
                }
            }
        }

        // ------------------ 7. DIALOGUE AUDIO & SOUS-TITRES D-PAD READY ------------------
        if (showTrackDialog) {
            TrackSelectionDialog(
                audioTracks = audioTracks,
                subtitleTracks = subtitleTracks,
                onSelectAudio = {
                    playerEngine.selectAudioTrack(it)
                    showTrackDialog = false
                },
                onSelectSubtitle = {
                    playerEngine.selectSubtitleTrack(it)
                    showTrackDialog = false
                },
                onDismiss = { showTrackDialog = false }
            )
        }
    }
}

/**
 * Dialogue de sélection des pistes audio et sous-titres avec support D-Pad Android TV
 */
@Composable
fun TrackSelectionDialog(
    audioTracks: List<TrackInfo>,
    subtitleTracks: List<TrackInfo>,
    onSelectAudio: (TrackInfo) -> Unit,
    onSelectSubtitle: (TrackInfo?) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDark,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text("Pistes Audio & Sous-titres", color = TextPrimary, fontWeight = FontWeight.Bold)
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Section Audio
                item {
                    Text("Pistes Audio Disponibles :", color = NoosCyan, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }

                if (audioTracks.isEmpty()) {
                    item {
                        Text("Piste audio principale active", color = TextSecondary, fontSize = 12.sp)
                    }
                } else {
                    items(audioTracks) { track ->
                        var isItemFocused by remember { mutableStateOf(false) }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isItemFocused) SurfaceDarkVariant else Color.Transparent)
                                .border(1.dp, if (isItemFocused) NoosCyan else Color.Transparent, RoundedCornerShape(10.dp))
                                .onFocusChanged { isItemFocused = it.isFocused }
                                .focusable()
                                .clickable { onSelectAudio(track) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(track.label, color = TextPrimary, fontSize = 13.sp, fontWeight = if (track.isSelected) FontWeight.Bold else FontWeight.Normal)
                            if (track.isSelected) {
                                Text("✓ Active", color = NoosCyan, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }

                // Section Sous-titres
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Sous-titres :", color = NoosCyan, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }

                item {
                    var isNoneFocused by remember { mutableStateOf(false) }
                    val isNoneSelected = subtitleTracks.none { it.isSelected }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isNoneFocused) SurfaceDarkVariant else Color.Transparent)
                            .border(1.dp, if (isNoneFocused) NoosCyan else Color.Transparent, RoundedCornerShape(10.dp))
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
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSubFocused) SurfaceDarkVariant else Color.Transparent)
                            .border(1.dp, if (isSubFocused) NoosCyan else Color.Transparent, RoundedCornerShape(10.dp))
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
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Fermer", color = NoosCyan)
            }
        }
    )
}

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
