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

import android.util.Log
import androidx.media3.common.PlaybackException
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory

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

    private val httpDataSourceFactory = DefaultHttpDataSource.Factory()
        .setUserAgent("IPTVSmartersPro/3.1.5 (Linux; Android TV)")
        .setAllowCrossProtocolRedirects(true)
        .setConnectTimeoutMs(20_000)
        .setReadTimeoutMs(30_000)

    private val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)

    private val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

    private val renderersFactory = DefaultRenderersFactory(context)
        .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
        .setEnableDecoderFallback(true)

    // LoadControl optimisé pour zapping IPTV ultra-rapide et streaming VOD résilient
    private val defaultLoadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(
            20_000, // minBufferMs
            50_000, // maxBufferMs
            1_500,  // bufferForPlaybackMs
            3_000   // bufferForPlaybackAfterRebufferMs
        )
        .setPrioritizeTimeOverSizeThresholds(true)
        .build()

    val exoPlayer: ExoPlayer by lazy {
        ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setTrackSelector(trackSelector)
            .setLoadControl(defaultLoadControl)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                /* handleAudioFocus = */ true
            )
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(30_000)
            .build()
    }

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _playerError = MutableStateFlow<String?>(null)
    val playerError: StateFlow<String?> = _playerError.asStateFlow()

    private var currentStreamUrl: String = ""
    private var currentStreamTitle: String = ""
    private var hasAttemptedFallback: Boolean = false

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
                if (isPlaying) {
                    _playerError.value = null
                }
            }

            override fun onTracksChanged(tracks: Tracks) {
                extractTracks(tracks)
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e("NoosPlayer", "Erreur ExoPlayer (${error.errorCodeName}): ${error.message}", error)

                if (!hasAttemptedFallback && currentStreamUrl.isNotBlank()) {
                    val fallbackUrl = when {
                        currentStreamUrl.endsWith(".mp4", ignoreCase = true) -> currentStreamUrl.dropLast(4) + ".mkv"
                        currentStreamUrl.endsWith(".mkv", ignoreCase = true) -> currentStreamUrl.dropLast(4) + ".mp4"
                        currentStreamUrl.endsWith(".ts", ignoreCase = true) -> currentStreamUrl.dropLast(3) + ".m3u8"
                        else -> null
                    }
                    if (fallbackUrl != null) {
                        hasAttemptedFallback = true
                        Log.i("NoosPlayer", "Tentative de repli vers : ${io.noostv.core.security.CryptoManager.sanitizeUrl(fallbackUrl)}")
                        playStream(fallbackUrl, currentStreamTitle, _isHdrActive.value, false)
                        return
                    }
                }

                val userMessage = when (error.errorCode) {
                    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "Accès refusé ou flux indisponible (Erreur HTTP)"
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "Délai de connexion dépassé au serveur IPTV"
                    PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
                    PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED -> "Format vidéo ou conteneur non supporté"
                    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED -> "Décodeur matériel non compatible pour ce flux"
                    else -> "Impossible de lire la vidéo (${error.errorCodeName})"
                }
                _playerError.value = userMessage
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

        _playerError.value = null
        currentStreamUrl = url
        currentStreamTitle = title
        hasAttemptedFallback = false
        _isHdrActive.value = isHdrStream

        Log.i("NoosPlayer", "playStream: '$title' -> ${io.noostv.core.security.CryptoManager.sanitizeUrl(url)}")

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
