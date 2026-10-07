package io.noostv.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import io.noostv.core.player.PlayerEngine
import io.noostv.ui.theme.SurfaceDark
import io.noostv.ui.theme.TextPrimary

/**
 * Bandeau "mini-lecteur" affiché en bas de l'écran d'accueil mobile quand l'utilisateur
 * revient du lecteur via le bouton Retour : il partage le MÊME exoPlayer que le plein
 * écran (continuité de lecture garantie) et propose Lecture/Pause + Fermer.
 *
 * - Tap sur la barre (ou la vignette) : revient au plein écran (onExpand).
 * - Bouton ✕ : stoppe la lecture et retire le bandeau (onClose).
 */
@Composable
fun MiniPlayerBar(
    playerEngine: PlayerEngine,
    title: String,
    subtitle: String,
    onExpand: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isPlaying by playerEngine.isPlaying.collectAsState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(SurfaceDark.copy(alpha = 0.97f))
            .clickable(onClick = onExpand),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Vignette vidéo : même PlayerView attaché au même exoPlayer -> lecture continue.
        Box(
            modifier = Modifier
                .width(120.dp)
                .fillMaxHeight()
                .background(Color.Black)
                .clickable(onClick = onExpand)
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        player = playerEngine.exoPlayer
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = TextPrimary.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        IconButton(onClick = { if (isPlaying) playerEngine.pause() else playerEngine.resume() }) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Lecture",
                tint = TextPrimary
            )
        }
        IconButton(onClick = onClose) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Fermer",
                tint = TextPrimary
            )
        }
    }
}
