package io.noostv.ui.tv

import android.content.Context
import androidx.annotation.OptIn
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
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
import io.noostv.data.model.Channel
import io.noostv.data.model.EpgProgram
import io.noostv.core.storage.SessionManager
import io.noostv.ui.common.HdrBadge
import io.noostv.ui.common.LiveIndicatorBadge
import io.noostv.ui.common.ResolutionBadge
import io.noostv.ui.theme.*
import kotlinx.coroutines.launch

/**
 * Vue split immersive de prévisualisation Live TV sur 1/4 d'écran
 * avec le Guide TV de la chaîne sélectionnée sous le lecteur vidéo.
 */
@OptIn(UnstableApi::class)
@Composable
fun TvChannelPreviewContent(
    channels: List<Channel>,
    selectedChannel: Channel,
    epgPrograms: List<EpgProgram>,
    playerEngine: PlayerEngine,
    sessionManager: SessionManager? = null,
    contentFocusRequester: FocusRequester? = null,
    onChannelChanged: (Channel) -> Unit,
    onOpenFullscreen: (Channel) -> Unit,
    onClosePreview: () -> Unit,
    onNavigateLeftToSidebar: () -> Unit,
    onFavoriteToggled: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Focus requesters pour navigation D-Pad
    val playerFocusRequester = remember { FocusRequester() }
    val channelListFocusRequester = contentFocusRequester ?: remember { FocusRequester() }

    // Démarre la lecture du flux dans le PlayerEngine lors de la sélection de la chaîne
    LaunchedEffect(selectedChannel.id) {
        playerEngine.playStream(
            url = selectedChannel.streamUrl,
            title = selectedChannel.name,
            isHdrStream = selectedChannel.isHdr,
            is4K = selectedChannel.resolution.contains("4K")
        )
    }

    val channelSchedule = remember(selectedChannel.id, epgPrograms) {
        EpgProvider.getChannelSchedule(selectedChannel, epgPrograms)
    }
    val currentLiveProgram = remember(selectedChannel.id, epgPrograms) {
        EpgProvider.getCurrentProgram(selectedChannel, epgPrograms)
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 4.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ==================== COLONNE GAUCHE (LISTE DES CHAÎNES EN ZAPPING) ====================
        Column(
            modifier = Modifier
                .weight(0.42f)
                .fillMaxHeight()
        ) {
            // En-tête avec bouton retour grille
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "CHAÎNES EN DIRECT (${channels.size})",
                    color = NoosCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                // Bouton retour grille
                Button(
                    onClick = onClosePreview,
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceDarkVariant),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Icon(imageVector = Icons.Default.GridView, contentDescription = null, tint = NoosCyan, modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Grille complète", color = TextPrimary, fontSize = 10.sp)
                }
            }

            val listState = rememberLazyListState()
            val selectedIndex = channels.indexOfFirst { it.id == selectedChannel.id }.coerceAtLeast(0)

            LaunchedEffect(Unit) {
                listState.scrollToItem(selectedIndex.coerceAtLeast(0))
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(channels) { index, ch ->
                    val isCurrent = ch.id == selectedChannel.id
                    val isFirstItem = index == 0
                    val liveProg = EpgProvider.getCurrentProgram(ch, epgPrograms)

                    TvChannelListItem(
                        channel = ch,
                        currentProgram = liveProg,
                        isSelected = isCurrent,
                        focusRequester = if (isFirstItem) channelListFocusRequester else null,
                        onNavigateLeft = onNavigateLeftToSidebar,
                        onNavigateRight = { runCatching { playerFocusRequester.requestFocus() } },
                        onClick = {
                            if (!isCurrent) {
                                onChannelChanged(ch)
                            } else {
                                onOpenFullscreen(ch)
                            }
                        }
                    )
                }
            }
        }

        // ==================== COLONNE DROITE (1/4 ÉCRAN LECTEUR + GUIDE TV EN DESSOUS) ====================
        Column(
            modifier = Modifier
                .weight(0.58f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ----------------- 1. PRÉVISUALISATION 1/4 D'ÉCRAN (PLAYER EXOPLAYER) -----------------
            var isPlayerFocused by remember { mutableStateOf(false) }
            val playerScale by animateFloatAsState(targetValue = if (isPlayerFocused) 1.02f else 1.0f, label = "mini_player_scale")

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f) // Ratio 16:9 cinématographique représentant 1/4 d'écran
                    .scale(playerScale)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black)
                    .focusRequester(playerFocusRequester)
                    .onFocusChanged { isPlayerFocused = it.isFocused }
                    .focusable()
                    .clickable { onOpenFullscreen(selectedChannel) }
                    .onPreviewKeyEvent { keyEvent ->
                        if (keyEvent.type == KeyEventType.KeyDown) {
                            when (keyEvent.key) {
                                Key.Enter, Key.DirectionCenter, Key.Spacebar -> {
                                    onOpenFullscreen(selectedChannel)
                                    true
                                }
                                Key.DirectionLeft -> {
                                    runCatching { channelListFocusRequester.requestFocus() }
                                    true
                                }
                                else -> false
                            }
                        } else false
                    }
                    .border(
                        width = if (isPlayerFocused) 3.5.dp else 1.5.dp,
                        color = if (isPlayerFocused) FocusGlow else CardBorderUnfocused,
                        shape = RoundedCornerShape(20.dp)
                    )
            ) {
                // Rendu vidéo PlayerView ExoPlayer
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            useController = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            player = playerEngine.exoPlayer
                        }
                    },
                    update = { pv ->
                        if (pv.player != playerEngine.exoPlayer) {
                            pv.player = playerEngine.exoPlayer
                        }
                    }
                )

                // Superposition d'informations Direct & Badges
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color(0x99080A0F),
                                    Color.Transparent,
                                    Color(0xDD080A0F)
                                )
                            )
                        )
                        .padding(12.dp)
                ) {
                    // Haut : Nom de la chaîne, Direct & Résolution
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            LiveIndicatorBadge()
                            Text(
                                text = selectedChannel.name,
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (selectedChannel.isHdr) HdrBadge() else ResolutionBadge(resolution = selectedChannel.resolution)
                    }

                    // Bas : Boutons Favoris et Plein Écran interactif
                    var isFavorite by remember(selectedChannel.id) {
                        mutableStateOf(sessionManager?.isFavoriteChannel(selectedChannel.id) ?: false)
                    }

                    Row(
                        modifier = Modifier.align(Alignment.BottomEnd),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (sessionManager != null) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(if (isFavorite) Color(0x55FFD700) else Color(0x66000000))
                                    .border(1.dp, if (isFavorite) Color(0xFFFFD700) else Color(0x44FFFFFF), RoundedCornerShape(50))
                                    .clickable {
                                        isFavorite = sessionManager.toggleFavoriteChannel(selectedChannel.id)
                                        onFavoriteToggled?.invoke()
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                                    contentDescription = "Favori",
                                    tint = if (isFavorite) Color(0xFFFFD700) else Color.White,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = if (isFavorite) "Favori" else "Ajouter",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(if (isPlayerFocused) NoosBlue else Color(0x88000000))
                                .border(1.dp, if (isPlayerFocused) FocusGlow else Color(0x44FFFFFF), RoundedCornerShape(50))
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fullscreen,
                                contentDescription = "Plein écran",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Plein écran (OK)",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // ----------------- 2. GUIDE TV DE LA CHAÎNE EN DESSOUS DU LECTEUR -----------------
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(SurfaceDark)
                    .border(1.dp, CardBorderUnfocused, RoundedCornerShape(20.dp))
                    .padding(14.dp)
            ) {
                // En-tête du guide pour la chaîne
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(imageVector = Icons.Default.CalendarToday, contentDescription = null, tint = NoosCyan, modifier = Modifier.size(15.dp))
                        Text(
                            text = "GUIDE TV • ${selectedChannel.name.uppercase()}",
                            color = NoosCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }

                    Text(
                        text = "Aujourd'hui",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Liste verticale des programmes de la chaîne
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Programme en cours
                    item {
                        TvLiveScheduleCard(
                            program = currentLiveProgram,
                            isLiveNow = true
                        )
                    }

                    // Programmes suivants
                    val upcoming = channelSchedule.filter { it.id != currentLiveProgram.id && it.startEpochMs >= currentLiveProgram.startEpochMs }
                    itemsIndexed(upcoming) { _, prog ->
                        TvLiveScheduleCard(
                            program = prog,
                            isLiveNow = false
                        )
                    }
                }
            }
        }
    }
}

/**
 * Ligne de chaîne compacte pour la colonne de zapping dans le mode split preview
 */
@Composable
fun TvChannelListItem(
    channel: Channel,
    currentProgram: EpgProgram,
    isSelected: Boolean,
    focusRequester: FocusRequester? = null,
    onNavigateLeft: (() -> Unit)? = null,
    onNavigateRight: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(targetValue = if (isFocused) 1.02f else 1.0f, label = "channel_list_scale")

    var mod = Modifier
        .fillMaxWidth()
        .scale(scale)
        .clip(RoundedCornerShape(14.dp))

    if (focusRequester != null) {
        mod = mod.focusRequester(focusRequester)
    }

    Box(
        modifier = mod
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable { onClick() }
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.key) {
                        Key.DirectionLeft -> {
                            if (onNavigateLeft != null) {
                                onNavigateLeft()
                                true
                            } else false
                        }
                        Key.DirectionRight -> {
                            if (onNavigateRight != null) {
                                onNavigateRight()
                                true
                            } else false
                        }
                        else -> false
                    }
                } else false
            }
            .background(
                when {
                    isFocused -> Color(0xFF1E2838)
                    isSelected -> NoosBlue.copy(alpha = 0.25f)
                    else -> SurfaceDarkVariant
                }
            )
            .border(
                width = if (isFocused) 2.5.dp else if (isSelected) 1.5.dp else 1.dp,
                color = when {
                    isFocused -> FocusGlow
                    isSelected -> NoosBlue
                    else -> CardBorderUnfocused
                },
                shape = RoundedCornerShape(14.dp)
            )
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Logo de la chaîne
            if (!channel.logoUrl.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(SurfaceDark),
                    contentAlignment = Alignment.Center
                ) {
                    SubcomposeAsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(channel.logoUrl)
                            .crossfade(true)
                            .allowHardware(false)
                            .build(),
                        contentDescription = channel.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(3.dp)
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(SurfaceDark),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = Icons.Default.Tv, contentDescription = null, tint = NoosCyan, modifier = Modifier.size(20.dp))
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = channel.name,
                        color = if (isFocused || isSelected) Color.White else TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isSelected) {
                        Text(text = "ACTIF", color = NoosCyan, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Titre du programme
                Text(
                    text = currentProgram.title,
                    color = if (isSelected) NoosCyan else TextSecondary,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Carte de programme dans le Guide TV sous le lecteur
 */
@Composable
fun TvLiveScheduleCard(
    program: EpgProgram,
    isLiveNow: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (isLiveNow) Color(0x183888FF) else SurfaceDarkVariant)
            .border(
                width = if (isLiveNow) 1.5.dp else 1.dp,
                color = if (isLiveNow) NoosBlue.copy(alpha = 0.8f) else Color(0x22FFFFFF),
                shape = RoundedCornerShape(14.dp)
            )
            .padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // Ligne 1 : Plage horaire + Badge d'état
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = program.timeSlotFormatted,
                        color = if (isLiveNow) NoosCyan else TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    program.category?.let {
                        Text(
                            text = "• $it",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }

                if (isLiveNow) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(NoosBlue)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "EN CE MOMENT",
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else if (program.hasCatchup) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color(0x223888FF))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "REPLAY",
                            color = NoosCyan,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Ligne 2 : Titre du programme
            Text(
                text = program.title,
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )

            // Ligne 3 : Jauge de progression si en cours
            if (isLiveNow) {
                val progress = program.progressFraction()
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(50)),
                    color = NoosCyan,
                    trackColor = Color(0x333888FF)
                )
            }

            // Ligne 4 : Description / Résumé
            if (!program.description.isNullOrBlank()) {
                Text(
                    text = program.description,
                    color = TextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    maxLines = if (isLiveNow) 3 else 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
