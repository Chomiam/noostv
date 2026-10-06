package io.noostv.ui.player

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import io.noostv.core.player.PlayerEngine
import io.noostv.core.player.TrackInfo
import io.noostv.data.model.Channel
import io.noostv.ui.common.HdrBadge
import io.noostv.ui.common.LiveIndicatorBadge
import io.noostv.ui.common.ResolutionBadge
import io.noostv.ui.theme.*
import kotlinx.coroutines.delay

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
    onBack: () -> Unit,
    onNextChannel: (() -> Unit)? = null,
    onPreviousChannel: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var isOsdVisible by remember { mutableStateOf(true) }
    var showTrackDialog by remember { mutableStateOf(false) }

    val isPlaying by playerEngine.isPlaying.collectAsState()
    val audioTracks by playerEngine.availableAudioTracks.collectAsState()
    val subtitleTracks by playerEngine.availableSubtitleTracks.collectAsState()

    // Masquage automatique de l'OSD après 4 secondes sur TV et Mobile
    LaunchedEffect(isOsdVisible) {
        if (isOsdVisible) {
            delay(4000)
            isOsdVisible = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable { isOsdVisible = !isOsdVisible }
    ) {
        // Rendu vidéo matériel ExoPlayer via PlayerView
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false // On utilise l'OSD Compose personnalisé NoosTV
                    player = playerEngine.exoPlayer
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            }
        )

        // Overlay OSD (On-Screen Display)
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
                                Color.Black.copy(alpha = 0.75f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.85f)
                            )
                        )
                    )
                    .padding(24.dp)
            ) {
                // Barre Supérieure OSD
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
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(SurfaceDark.copy(alpha = 0.8f))
                        ) {
                            Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Retour", tint = TextPrimary)
                        }

                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = title,
                                    color = TextPrimary,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
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

                    // Bouton Pistes Audio / Sous-titres
                    Button(
                        onClick = { showTrackDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark.copy(alpha = 0.8f))
                    ) {
                        Icon(imageVector = Icons.Default.Subtitles, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Audio & Sous-titres", color = TextPrimary, fontSize = 12.sp)
                    }
                }

                // Barre Inférieure Contrôles OSD
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (onPreviousChannel != null) {
                            IconButton(onClick = onPreviousChannel) {
                                Icon(imageVector = Icons.Default.SkipPrevious, contentDescription = "Chaîne précédente", tint = TextPrimary, modifier = Modifier.size(32.dp))
                            }
                        }

                        IconButton(
                            onClick = {
                                if (isPlaying) playerEngine.pause() else playerEngine.resume()
                            },
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(28.dp))
                                .background(NeonCyan)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Lecture",
                                tint = Color.Black,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        if (onNextChannel != null) {
                            IconButton(onClick = onNextChannel) {
                                Icon(imageVector = Icons.Default.SkipNext, contentDescription = "Chaîne suivante", tint = TextPrimary, modifier = Modifier.size(32.dp))
                            }
                        }
                    }
                }
            }
        }

        // Dialogue de sélection des pistes audio et sous-titres
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
        title = {
            Text("Pistes Audio & Sous-titres", color = TextPrimary, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Section Audio
                Text("Pistes Audio Disponibles :", color = NeonCyan, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                if (audioTracks.isEmpty()) {
                    Text("Piste audio par défaut active", color = TextSecondary, fontSize = 12.sp)
                } else {
                    audioTracks.forEach { track ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectAudio(track) }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(track.label, color = TextPrimary, fontSize = 13.sp)
                            if (track.isSelected) {
                                Text("✓ Active", color = NeonCyan, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }

                // Section Sous-titres
                Text("Sous-titres :", color = NeonCyan, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectSubtitle(null) }
                        .padding(vertical = 4.dp)
                ) {
                    Text("Désactivés", color = TextSecondary, fontSize = 13.sp)
                }
                subtitleTracks.forEach { track ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectSubtitle(track) }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(track.label, color = TextPrimary, fontSize = 13.sp)
                        if (track.isSelected) {
                            Text("✓ Actif", color = NeonCyan, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Fermer", color = NeonCyan)
            }
        }
    )
}
