package io.noostv.core.player

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import io.noostv.core.entitlement.EntitlementManager
import io.noostv.core.entitlement.Feature
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Piste audio ou sous-titre disponible dans le flux
 */
data class TrackInfo(
    val groupIndex: Int,
    val trackIndex: Int,
    val id: String,
    val label: String,
    val language: String,
    val isSelected: Boolean
)

/**
 * Moteur de lecture vidéo NoosTV basé sur AndroidX Media3 ExoPlayer.
 * Gère le zapping rapide, le décodage matériel HDR10/Dolby Vision et les restrictions SaaS 4K.
 */
@OptIn(UnstableApi::class)
class PlayerEngine(
    private val context: Context,
    private val entitlementManager: EntitlementManager
) {

    private val trackSelector = DefaultTrackSelector(context).apply {
        setParameters(
            buildUponParameters()
                .setPreferredAudioLanguage("fra")
                .setPreferredTextLanguage("fra")
                .setForceHighestSupportedBitrate(true)
        )
    }

    // LoadControl optimisé pour zapping IPTV ultra-rapide (démarre dès 500ms de buffer)
    private val liveLoadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(
            15_000, // minBufferMs
            30_000, // maxBufferMs
            500,    // bufferForPlaybackMs
            1_500   // bufferForPlaybackAfterRebufferMs
        )
        .build()

    val exoPlayer: ExoPlayer by lazy {
        ExoPlayer.Builder(context)
            .setTrackSelector(trackSelector)
            .setLoadControl(liveLoadControl)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                /* handleAudioFocus = */ true
            )
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()
    }

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _availableAudioTracks = MutableStateFlow<List<TrackInfo>>(emptyList())
    val availableAudioTracks: StateFlow<List<TrackInfo>> = _availableAudioTracks.asStateFlow()

    private val _availableSubtitleTracks = MutableStateFlow<List<TrackInfo>>(emptyList())
    val availableSubtitleTracks: StateFlow<List<TrackInfo>> = _availableSubtitleTracks.asStateFlow()

    private val _isHdrActive = MutableStateFlow(false)
    val isHdrActive: StateFlow<Boolean> = _isHdrActive.asStateFlow()

    init {
        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
            }

            override fun onTracksChanged(tracks: Tracks) {
                extractTracks(tracks)
            }
        })
    }

    /**
     * Lance la lecture d'un flux IPTV ou VOD avec vérification de l'accès 4K HDR SaaS
     */
    fun playStream(url: String, title: String, isHdrStream: Boolean = false, is4K: Boolean = false): Boolean {
        // Garde-fou SaaS : vérifie si l'utilisateur a droit aux flux 4K / HDR
        if ((isHdrStream || is4K) && !entitlementManager.isFeatureAllowed(Feature.HDR_4K_STREAMING)) {
            return false // Requiert la mise à niveau vers NoosTV Premium
        }

        _isHdrActive.value = isHdrStream

        val mediaItem = MediaItem.Builder()
            .setUri(Uri.parse(url))
            .setMediaId(title)
            .build()

        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
        return true
    }

    val currentPosition: Long
        get() = runCatching { exoPlayer.currentPosition }.getOrDefault(0L)

    val duration: Long
        get() = runCatching {
            val dur = exoPlayer.duration
            if (dur == androidx.media3.common.C.TIME_UNSET) 0L else dur
        }.getOrDefault(0L)

    fun seekBy(deltaMs: Long) {
        val dur = duration
        val cur = currentPosition
        val target = if (dur > 0L) {
            (cur + deltaMs).coerceIn(0L, dur)
        } else {
            (cur + deltaMs).coerceAtLeast(0L)
        }
        seekTo(target)
    }

    fun pause() {
        exoPlayer.pause()
    }

    fun resume() {
        exoPlayer.play()
    }

    fun seekTo(positionMs: Long) {
        exoPlayer.seekTo(positionMs)
    }

    fun stop() {
        exoPlayer.stop()
    }

    fun release() {
        exoPlayer.release()
    }

    /**
     * Sélectionne une piste audio spécifique
     */
    fun selectAudioTrack(trackInfo: TrackInfo) {
        val currentTracks = exoPlayer.currentTracks
        for (group in currentTracks.groups) {
            if (group.type == C.TRACK_TYPE_AUDIO) {
                val override = TrackSelectionOverride(group.mediaTrackGroup, trackInfo.trackIndex)
                trackSelector.setParameters(
                    trackSelector.buildUponParameters().setOverrideForType(override)
                )
                break
            }
        }
    }

    /**
     * Sélectionne une piste de sous-titres spécifique ou désactive les sous-titres
     */
    fun selectSubtitleTrack(trackInfo: TrackInfo?) {
        if (trackInfo == null) {
            trackSelector.setParameters(
                trackSelector.buildUponParameters().setIgnoredTextSelectionFlags(C.SELECTION_FLAG_DEFAULT)
            )
            return
        }

        val currentTracks = exoPlayer.currentTracks
        for (group in currentTracks.groups) {
            if (group.type == C.TRACK_TYPE_TEXT) {
                val override = TrackSelectionOverride(group.mediaTrackGroup, trackInfo.trackIndex)
                trackSelector.setParameters(
                    trackSelector.buildUponParameters().setOverrideForType(override)
                )
                break
            }
        }
    }

    private fun extractTracks(tracks: Tracks) {
        val audioList = mutableListOf<TrackInfo>()
        val subtitleList = mutableListOf<TrackInfo>()

        for ((groupIndex, group) in tracks.groups.withIndex()) {
            val trackGroup = group.mediaTrackGroup
            for (trackIndex in 0 until trackGroup.length) {
                val format = trackGroup.getFormat(trackIndex)
                val isSelected = group.isTrackSelected(trackIndex)
                val lang = format.language ?: "und"
                val label = format.label ?: format.language ?: "Piste ${trackIndex + 1}"

                val info = TrackInfo(
                    groupIndex = groupIndex,
                    trackIndex = trackIndex,
                    id = format.id ?: "$groupIndex-$trackIndex",
                    label = label,
                    language = lang,
                    isSelected = isSelected
                )

                when (group.type) {
                    C.TRACK_TYPE_AUDIO -> audioList.add(info)
                    C.TRACK_TYPE_TEXT -> subtitleList.add(info)
                }
            }
        }

        _availableAudioTracks.value = audioList
        _availableSubtitleTracks.value = subtitleList
    }
}
