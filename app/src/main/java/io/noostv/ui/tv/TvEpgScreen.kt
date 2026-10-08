package io.noostv.ui.tv

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.noostv.data.model.Channel
import io.noostv.data.model.EpgProgram
import io.noostv.ui.common.LiveIndicatorBadge
import io.noostv.ui.theme.*

@Composable
fun TvEpgScreen(
    channels: List<Channel>,
    epgPrograms: List<EpgProgram>,
    onSelectChannel: (Channel) -> Unit,
    onBack: () -> Unit
) {
    val currentTime = remember { System.currentTimeMillis() }
    var selectedChannel by remember { mutableStateOf(channels.firstOrNull()) }

    MacOsDarkGlassBackground(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
        // En-tête Guide TV
        Row(
            modifier = Modifier.fillMaxWidth(),
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
                        .background(SurfaceDark)
                ) {
                    Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Retour", tint = TextPrimary)
                }
                Text(
                    text = "Guide TV Interactif (EPG 7 Jours)",
                    color = TextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = "Grille Temporelle & Replay (Catch-up)",
                color = NeonCyan,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Grille des chaînes et de leurs programmes EPG
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(channels) { channel ->
                val programsForChannel = epgPrograms.filter { it.channelId == channel.epgChannelId }

                TvEpgChannelRow(
                    channel = channel,
                    programs = programsForChannel,
                    currentTime = currentTime,
                    onPlayCurrent = { onSelectChannel(channel) }
                )
            }
        }
    }
}
}

@Composable
private fun TvEpgChannelRow(
    channel: Channel,
    programs: List<EpgProgram>,
    currentTime: Long,
    onPlayCurrent: () -> Unit
) {
    var isChannelFocused by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // En-tête de la chaîne
        Box(
            modifier = Modifier
                .width(180.dp)
                .height(86.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (isChannelFocused) SurfaceDarkVariant else SurfaceDark)
                .border(
                    width = if (isChannelFocused) 2.dp else 1.dp,
                    color = if (isChannelFocused) NeonCyan else Color.White.copy(alpha = 0.05f),
                    shape = RoundedCornerShape(10.dp)
                )
                .focusable()
                .onFocusChanged { isChannelFocused = it.isFocused }
                .clickable { onPlayCurrent() }
                .padding(10.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Column {
                Text(
                    text = channel.name,
                    color = if (isChannelFocused) NeonCyan else TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${channel.resolution} • ${channel.videoCodec.uppercase()}",
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Programmes horaires pour cette chaîne
        if (programs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(86.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(SurfaceDark.copy(alpha = 0.5f))
                    .padding(16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = "Aucune information EPG disponible pour cette chaîne",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
        } else {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(programs) { program ->
                    TvEpgProgramCard(
                        program = program,
                        currentTime = currentTime,
                        onPlay = onPlayCurrent
                    )
                }
            }
        }
    }
}

@Composable
private fun TvEpgProgramCard(
    program: EpgProgram,
    currentTime: Long,
    onPlay: () -> Unit
) {
    val isLive = program.isLiveNow(currentTime)
    val progress = program.progressFraction(currentTime)
    var isFocused by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(targetValue = if (isFocused) 1.05f else 1.0f, label = "epg_scale")

    Box(
        modifier = Modifier
            .width(260.dp)
            .height(86.dp)
            .scale(scale)
            .clip(RoundedCornerShape(10.dp))
            .background(if (isLive) Color(0xFF132238) else SurfaceDark)
            .border(
                width = if (isFocused) 2.dp else if (isLive) 1.dp else 0.dp,
                color = if (isFocused) FocusGlow else if (isLive) NeonCyan.copy(alpha = 0.5f) else Color.Transparent,
                shape = RoundedCornerShape(10.dp)
            )
            .focusable()
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { onPlay() }
            .padding(10.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = program.timeSlotFormatted,
                    color = if (isLive) NeonCyan else TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )

                if (isLive) {
                    LiveIndicatorBadge()
                } else if (program.hasCatchup) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Replay, contentDescription = "Replay", tint = GoldVip, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("Revoir", color = GoldVip, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Text(
                text = program.title,
                color = if (isFocused) NeonCyan else TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Barre de progression du direct
            if (isLive) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = NeonCyan,
                    trackColor = SurfaceDarkVariant
                )
            } else {
                Text(
                    text = program.category ?: "Programme",
                    color = TextSecondary,
                    fontSize = 10.sp
                )
            }
        }
    }
}
