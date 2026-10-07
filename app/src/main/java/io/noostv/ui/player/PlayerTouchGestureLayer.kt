package io.noostv.ui.player

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.noostv.ui.theme.NoosCyan
import io.noostv.ui.theme.SurfaceDark
import io.noostv.ui.theme.TextPrimary
import kotlin.math.abs

/**
 * Couche de gestes tactiles mobile (téléphone / tablette) posée au-dessus du lecteur.
 *
 * Gestes pris en charge :
 *  - Simple tap              : affiche / masque l'OSD.
 *  - Double tap (gauche)     : recule de 10 s (VOD) / chaîne précédente (Live).
 *  - Double tap (centre)     : lecture / pause.
 *  - Double tap (droite)     : avance de 30 s (VOD) / chaîne suivante (Live).
 *  - Glissement vertical à gauche : luminosité de l'écran.
 *  - Glissement vertical à droite : volume multimédia.
 *  - Glissement horizontal   : scrubbing (VOD) / zapping par balayage (Live).
 *
 * Le composant ne fait rien si [enabled] est faux (mode TV/box : D-Pad uniquement).
 */
@Composable
fun PlayerTouchGestureLayer(
    enabled: Boolean,
    isLive: Boolean,
    onToggleOsd: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekRelative: (Long) -> Unit,
    onNextChannel: (() -> Unit)?,
    onPreviousChannel: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    if (!enabled) return

    val context = LocalContext.current
    val activity = context as? Activity
    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }

    // Indicateur central (luminosité / volume)
    var hudLabel by remember { mutableStateOf<String?>(null) }
    var hudProgress by remember { mutableFloatStateOf(0f) }
    var hudIsVolume by remember { mutableStateOf(false) }

    // Indicateur de déplacement horizontal (scrubbing / zapping)
    var scrubLabel by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(hudLabel) {
        if (hudLabel != null) {
            kotlinx.coroutines.delay(1100)
            hudLabel = null
        }
    }
    LaunchedEffect(scrubLabel) {
        if (scrubLabel != null) {
            kotlinx.coroutines.delay(900)
            scrubLabel = null
        }
    }

    fun stepBrightness(increase: Boolean) {
        val win = activity?.window ?: return
        val attrs = win.attributes
        val current = if (attrs.screenBrightness < 0f) 0.5f else attrs.screenBrightness
        val next = (current + if (increase) 0.04f else -0.04f).coerceIn(0.05f, 1f)
        attrs.screenBrightness = next
        win.attributes = attrs
        hudIsVolume = false
        hudProgress = next
        hudLabel = "Luminosité ${(next * 100).toInt()}%"
    }

    fun stepVolume(increase: Boolean) {
        val am = audioManager ?: return
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val current = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        val next = (current + if (increase) 1 else -1).coerceIn(0, max)
        if (next == current) return
        am.setStreamVolume(AudioManager.STREAM_MUSIC, next, 0)
        hudIsVolume = true
        hudProgress = next.toFloat() / max.toFloat()
        hudLabel = "Volume ${(hudProgress * 100).toInt()}%"
    }

    Box(
        modifier = modifier
            // ------------------ TAP / DOUBLE TAP ------------------
            .pointerInput(enabled, isLive) {
                detectTapGestures(
                    onTap = { onToggleOsd() },
                    onDoubleTap = { offset ->
                        val w = size.width.toFloat()
                        when {
                            offset.x < w / 3f -> {
                                if (isLive) onPreviousChannel?.invoke() else onSeekRelative(-10_000L)
                            }
                            offset.x > (w * 2f) / 3f -> {
                                if (isLive) onNextChannel?.invoke() else onSeekRelative(30_000L)
                            }
                            else -> onTogglePlayPause()
                        }
                    }
                )
            }
            // ------------------ GLISSEMENT VERTICAL (LUMINOSITÉ / VOLUME) ------------------
            .pointerInput(enabled) {
                var leftSide = false
                var accumulated = 0f
                detectVerticalDragGestures(
                    onDragStart = { offset ->
                        leftSide = offset.x < size.width / 2f
                        accumulated = 0f
                    },
                    onDragEnd = { accumulated = 0f },
                    onDragCancel = { accumulated = 0f },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        accumulated += dragAmount
                        if (abs(accumulated) >= 26f) {
                            val increase = accumulated < 0f
                            if (leftSide) stepBrightness(increase) else stepVolume(increase)
                            accumulated = 0f
                        }
                    }
                )
            }
            // ------------------ GLISSEMENT HORIZONTAL (SCRUB / ZAP) ------------------
            .pointerInput(enabled, isLive) {
                var accumulated = 0f
                detectHorizontalDragGestures(
                    onDragStart = {
                        accumulated = 0f
                        if (!isLive) onToggleOsd()
                    },
                    onDragEnd = {
                        if (isLive) {
                            if (accumulated > 70f) onNextChannel?.invoke()
                            else if (accumulated < -70f) onPreviousChannel?.invoke()
                        } else if (accumulated != 0f) {
                            // ~2 minutes de vidéo pour une largeur d'écran balayée
                            val deltaMs = (accumulated / size.width.toFloat() * 120_000f).toLong()
                            if (abs(deltaMs) >= 3_000L) onSeekRelative(deltaMs)
                        }
                        accumulated = 0f
                        scrubLabel = null
                    },
                    onDragCancel = {
                        accumulated = 0f
                        scrubLabel = null
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        accumulated += dragAmount
                        if (!isLive) {
                            val seconds = (accumulated / size.width.toFloat() * 120f).toInt()
                            scrubLabel = when {
                                seconds > 0 -> "+$seconds s"
                                seconds < 0 -> "$seconds s"
                                else -> "0 s"
                            }
                        } else {
                            scrubLabel = if (accumulated > 40f) {
                                "Chaîne suivante ▶"
                            } else if (accumulated < -40f) {
                                "◀ Chaîne précédente"
                            } else null
                        }
                    }
                )
            }
    )

    // ------------------ INDICATEUR VERTICAL (LUMINOSITÉ / VOLUME) ------------------
    AnimatedVisibility(
        visible = hudLabel != null,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xDD0A0E17))
                    .border(1.5.dp, NoosCyan, RoundedCornerShape(18.dp))
                    .padding(horizontal = 22.dp, vertical = 16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (hudIsVolume) Icons.Default.VolumeUp else Icons.Default.Brightness6,
                        contentDescription = null,
                        tint = NoosCyan,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = hudLabel ?: "",
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { hudProgress.coerceIn(0f, 1f) },
                    color = NoosCyan,
                    trackColor = SurfaceDark,
                    modifier = Modifier.width(140.dp).height(5.dp).clip(RoundedCornerShape(3.dp))
                )
            }
        }
    }

    // ------------------ INDICATEUR DE SCRUBBING / ZAPPING ------------------
    AnimatedVisibility(
        visible = scrubLabel != null,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xCC080A0F))
                    .border(1.5.dp, NoosCyan, RoundedCornerShape(16.dp))
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                val label = scrubLabel ?: ""
                if (label.startsWith("+")) {
                    Icon(Icons.Default.FastForward, contentDescription = null, tint = NoosCyan, modifier = Modifier.size(20.dp))
                } else if (label.startsWith("-")) {
                    Icon(Icons.Default.FastRewind, contentDescription = null, tint = NoosCyan, modifier = Modifier.size(20.dp))
                } else {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = NoosCyan, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = label, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}