package io.noostv.core.player

import android.content.Context
import android.net.Uri
import android.net.wifi.WifiManager
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
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.VideoSize
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import java.util.Locale

/**
 * Piste audio ou sous-titre disponible dans le flux
 */
data class TrackInfo(
    val groupIndex: Int,
    val trackIndex: Int,
    val id: String,
    val label: String,
    val language: String,
    val isSelected: Boolean,
    val codec: String? = null,
    val channels: String? = null,
    val bitrate: Long = 0L,
    val sampleRate: Int = 0
)

/**
 * Piste de qualité vidéo disponible dans le flux
 */
data class VideoTrackInfo(
    val groupIndex: Int,
    val trackIndex: Int,
    val id: String,
    val width: Int,
    val height: Int,
    val bitrate: Long,
    val frameRate: Float,
    val label: String,
    val isSelected: Boolean
)

/**
 * Statistiques et informations techniques détaillées de lecture en temps réel
 */
data class PlaybackStats(
    val width: Int = 0,
    val height: Int = 0,
    val fps: Float = 0f,
    val videoBitrate: Long = 0L,
    val videoCodec: String = "",
    val hdrInfo: String = "",
    val audioCodec: String = "",
    val audioChannels: String = "",
    val audioSampleRate: Int = 0,
    val audioBitrate: Long = 0L,
    val activeAudioLabel: String = "",
    val activeSubtitleLabel: String = "",
    val speed: Float = 1.0f,
    val bufferHealthSeconds: Float = 0f
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

    // High-Performance WifiLock pour éliminer les micro-saccades et micro-rebuffering en veille Wi-Fi
    private val wifiLock: WifiManager.WifiLock? by lazy {
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            wifiManager?.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "NoosTV:PlaybackWifiLock")?.apply {
                setReferenceCounted(false)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun acquireWifiLock() {
        try {
            if (wifiLock?.isHeld == false) {
                wifiLock?.acquire()
            }
        } catch (e: Exception) {
            Log.w("NoosPlayer", "Impossible d'acquérir le WifiLock", e)
        }
    }

    private fun releaseWifiLock() {
        try {
            if (wifiLock?.isHeld == true) {
                wifiLock?.release()
            }
        } catch (e: Exception) {
            Log.w("NoosPlayer", "Impossible de libérer le WifiLock", e)
        }
    }

    // Tunneling matériel activé pour relier directement le décodeur vidéo au pipeline d'affichage TV
    private val trackSelector = DefaultTrackSelector(context).apply {
        setParameters(
            buildUponParameters()
                .setPreferredAudioLanguage("fra")
                .setPreferredTextLanguage("fra")
                .setForceHighestSupportedBitrate(true)
                .setTunnelingEnabled(true)
        )
    }

    private val httpDataSourceFactory = DefaultHttpDataSource.Factory()
        .setUserAgent("IPTVSmartersPro/3.1.5 (Linux; Android TV)")
        .setAllowCrossProtocolRedirects(true)
        .setConnectTimeoutMs(20_000)
        .setReadTimeoutMs(30_000)

    private val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)

    private val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

    // RenderersFactory avec file asynchrone MediaCodec pour désengorger le CPU sur les SoC TV quad-core
    private val renderersFactory = DefaultRenderersFactory(context)
        .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
        .setEnableDecoderFallback(true)
        .forceEnableMediaCodecAsynchronousQueueing()

    // LoadControl optimisé pour Android TV (économie RAM, 0 saccade, 25MB max)
    private val defaultLoadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(
            8_000,  // minBufferMs (8s au lieu de 15s : économise ~50MB RAM sans risque de buffer underrun)
            20_000, // maxBufferMs (20s au lieu de 45s : évite d'accumuler 150MB en mémoire vive)
            1_000,  // bufferForPlaybackMs (1s : zapping ultra-rapide)
            2_000   // bufferForPlaybackAfterRebufferMs (2s)
        )
        .setTargetBufferBytes(25 * 1024 * 1024) // Plafonne la mémoire tampon à 25 Mo
        .setBackBuffer(0, false) // 0s de back-buffer pour libérer immédiatement les trames passées
        .setPrioritizeTimeOverSizeThresholds(false)
        .build()

    val exoPlayer: ExoPlayer by lazy {
        ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setTrackSelector(trackSelector)
            .setLoadControl(defaultLoadControl)
            .setWakeMode(C.WAKE_MODE_NETWORK)
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

    private val _availableVideoTracks = MutableStateFlow<List<VideoTrackInfo>>(emptyList())
    val availableVideoTracks: StateFlow<List<VideoTrackInfo>> = _availableVideoTracks.asStateFlow()

    private val _availableAudioTracks = MutableStateFlow<List<TrackInfo>>(emptyList())
    val availableAudioTracks: StateFlow<List<TrackInfo>> = _availableAudioTracks.asStateFlow()

    private val _availableSubtitleTracks = MutableStateFlow<List<TrackInfo>>(emptyList())
    val availableSubtitleTracks: StateFlow<List<TrackInfo>> = _availableSubtitleTracks.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _playbackStats = MutableStateFlow(PlaybackStats())
    val playbackStats: StateFlow<PlaybackStats> = _playbackStats.asStateFlow()

    private val _isHdrActive = MutableStateFlow(false)
    val isHdrActive: StateFlow<Boolean> = _isHdrActive.asStateFlow()

    init {
        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
                if (isPlaying) {
                    _playerError.value = null
                    acquireWifiLock()
                } else {
                    releaseWifiLock()
                }
                updatePlaybackStats()
            }

            override fun onTracksChanged(tracks: Tracks) {
                extractTracks(tracks)
                updatePlaybackStats()
            }

            override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
                _playbackSpeed.value = playbackParameters.speed
                updatePlaybackStats()
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                updatePlaybackStats()
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
     * Active ou désactive le mode prévisualisation.
     * En mode prévisualisation :
     * - Limite la résolution vidéo maximale à 720p (1280x720) pour alléger le décodage et la bande passante
     * - Limite le débit maximal à 2.5 Mbps
     * - Désactive le forçage du débit maximal
     * Cela élimine les saccades/gels et accélère instantanément l'affichage du flux lors du zapping.
     */
    fun setPreviewMode(enabled: Boolean) {
        val builder = trackSelector.buildUponParameters()
        if (enabled) {
            builder
                .setMaxVideoSize(1280, 720)
                .setMaxVideoBitrate(2_500_000)
                .setForceHighestSupportedBitrate(false)
                .setTunnelingEnabled(false)
                .setExceedVideoConstraintsIfNecessary(true)
        } else {
            builder
                .clearVideoSizeConstraints()
                .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                .setMaxVideoBitrate(Int.MAX_VALUE)
                .setForceHighestSupportedBitrate(true)
                .setTunnelingEnabled(true)
                .setExceedVideoConstraintsIfNecessary(true)
        }
        trackSelector.setParameters(builder)
    }

    /**
     * Lance la lecture d'un flux IPTV ou VOD avec vérification de l'accès 4K HDR SaaS
     */
    fun playStream(
        url: String,
        title: String,
        isHdrStream: Boolean = false,
        is4K: Boolean = false,
        isPreview: Boolean = false,
        startPositionMs: Long = 0L
    ): Boolean {
        // Garde-fou SaaS : vérifie si l'utilisateur a droit aux flux 4K / HDR (hors prévisualisation bridée à 720p)
        if (!isPreview && (isHdrStream || is4K) && !entitlementManager.isFeatureAllowed(Feature.HDR_4K_STREAMING)) {
            return false // Requiert la mise à niveau vers NoosTV Premium
        }

        setPreviewMode(isPreview)

        _playerError.value = null
        currentStreamUrl = url
        currentStreamTitle = title
        hasAttemptedFallback = false
        _isHdrActive.value = isHdrStream

        // Réinitialise les métriques et pistes pour ne pas hériter des données d'un flux précédent
        _availableVideoTracks.value = emptyList()
        _availableAudioTracks.value = emptyList()
        _availableSubtitleTracks.value = emptyList()
        _playbackStats.value = PlaybackStats()

        Log.i("NoosPlayer", "playStream: '$title' (startAt=${startPositionMs}ms) -> ${io.noostv.core.security.CryptoManager.sanitizeUrl(url)}")

        val mediaItem = MediaItem.Builder()
            .setUri(Uri.parse(url))
            .setMediaId(title)
            .build()

        if (startPositionMs > 0L) {
            exoPlayer.setMediaItem(mediaItem, startPositionMs)
        } else {
            exoPlayer.setMediaItem(mediaItem)
        }
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
        releaseWifiLock()
        exoPlayer.stop()
    }

    fun release() {
        releaseWifiLock()
        exoPlayer.release()
    }

    /**
     * Définit la vitesse de lecture (ex: 0.5x, 0.75x, 1.0x, 1.25x, 1.5x, 2.0x)
     */
    fun setPlaybackSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.25f, 3.0f)
        exoPlayer.setPlaybackSpeed(clamped)
        _playbackSpeed.value = clamped
        updatePlaybackStats()
    }

    /**
     * Sélectionne une qualité vidéo spécifique ou repasse en Automatique si null
     */
    fun selectVideoTrack(trackInfo: VideoTrackInfo?) {
        val builder = trackSelector.buildUponParameters()
            .clearVideoSizeConstraints()
            .setMaxVideoBitrate(Int.MAX_VALUE)
            .setForceHighestSupportedBitrate(trackInfo == null)
            .setTunnelingEnabled(true)
            .setExceedVideoConstraintsIfNecessary(true)

        if (trackInfo == null) {
            builder.clearOverridesOfType(C.TRACK_TYPE_VIDEO)
        } else {
            val currentTracks = exoPlayer.currentTracks
            val group = currentTracks.groups.getOrNull(trackInfo.groupIndex)
                ?: currentTracks.groups.firstOrNull { it.type == C.TRACK_TYPE_VIDEO }
            if (group != null && trackInfo.trackIndex < group.mediaTrackGroup.length) {
                val override = TrackSelectionOverride(group.mediaTrackGroup, trackInfo.trackIndex)
                builder.setOverrideForType(override)
            }
        }
        trackSelector.setParameters(builder)

        // Met à jour la sélection locale immédiatement pour synchroniser l'UI
        _availableVideoTracks.value = _availableVideoTracks.value.map {
            it.copy(isSelected = (trackInfo != null && it.id == trackInfo.id))
        }
        updatePlaybackStats()
    }

    /**
     * Sélectionne une piste audio spécifique
     */
    fun selectAudioTrack(trackInfo: TrackInfo) {
        val currentTracks = exoPlayer.currentTracks
        val group = currentTracks.groups.getOrNull(trackInfo.groupIndex)
            ?: currentTracks.groups.firstOrNull { it.type == C.TRACK_TYPE_AUDIO }
        if (group != null && trackInfo.trackIndex < group.mediaTrackGroup.length) {
            val override = TrackSelectionOverride(group.mediaTrackGroup, trackInfo.trackIndex)
            trackSelector.setParameters(
                trackSelector.buildUponParameters().setOverrideForType(override)
            )
        }
        _availableAudioTracks.value = _availableAudioTracks.value.map {
            it.copy(isSelected = (it.id == trackInfo.id))
        }
        updatePlaybackStats()
    }

    /**
     * Sélectionne une piste de sous-titres spécifique ou désactive les sous-titres
     */
    fun selectSubtitleTrack(trackInfo: TrackInfo?) {
        if (trackInfo == null) {
            trackSelector.setParameters(
                trackSelector.buildUponParameters()
                    .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                    .setIgnoredTextSelectionFlags(C.SELECTION_FLAG_DEFAULT)
            )
        } else {
            val currentTracks = exoPlayer.currentTracks
            val group = currentTracks.groups.getOrNull(trackInfo.groupIndex)
                ?: currentTracks.groups.firstOrNull { it.type == C.TRACK_TYPE_TEXT }
            if (group != null && trackInfo.trackIndex < group.mediaTrackGroup.length) {
                val override = TrackSelectionOverride(group.mediaTrackGroup, trackInfo.trackIndex)
                trackSelector.setParameters(
                    trackSelector.buildUponParameters().setOverrideForType(override)
                )
            }
        }
        _availableSubtitleTracks.value = _availableSubtitleTracks.value.map {
            it.copy(isSelected = (trackInfo != null && it.id == trackInfo.id))
        }
        updatePlaybackStats()
    }

    private fun extractTracks(tracks: Tracks) {
        val videoList = mutableListOf<VideoTrackInfo>()
        val audioList = mutableListOf<TrackInfo>()
        val subtitleList = mutableListOf<TrackInfo>()

        for ((groupIndex, group) in tracks.groups.withIndex()) {
            val trackGroup = group.mediaTrackGroup
            for (trackIndex in 0 until trackGroup.length) {
                val format = trackGroup.getFormat(trackIndex)
                val isSelected = group.isTrackSelected(trackIndex)

                when (group.type) {
                    C.TRACK_TYPE_VIDEO -> {
                        val resLabel = if (format.width > 0 && format.height > 0) {
                            when {
                                format.height >= 1800 || format.width >= 3200 -> "4K UHD (${format.width}×${format.height})"
                                format.height >= 800 || format.width >= 1600 -> "1080p FHD (${format.width}×${format.height})"
                                format.height >= 600 || format.width >= 1100 -> "720p HD (${format.width}×${format.height})"
                                format.height >= 576 -> "576p SD"
                                format.height >= 480 -> "480p SD"
                                else -> "${format.width}×${format.height}"
                            }
                        } else {
                            format.label ?: "Qualité ${trackIndex + 1}"
                        }
                        val fpsStr = if (format.frameRate > 0) " • ${format.frameRate.toInt()} fps" else ""
                        val brStr = if (format.bitrate > 0) " • ${String.format(Locale.US, "%.1f", format.bitrate / 1_000_000f)} Mbps" else ""

                        videoList.add(
                            VideoTrackInfo(
                                groupIndex = groupIndex,
                                trackIndex = trackIndex,
                                id = format.id ?: "$groupIndex-$trackIndex",
                                width = format.width,
                                height = format.height,
                                bitrate = format.bitrate.toLong().coerceAtLeast(0L),
                                frameRate = format.frameRate,
                                label = "$resLabel$fpsStr$brStr",
                                isSelected = isSelected
                            )
                        )
                    }
                    C.TRACK_TYPE_AUDIO -> {
                        val lang = format.language ?: "und"
                        val baseLabel = format.label ?: format.language?.let {
                            runCatching {
                                Locale(it).getDisplayLanguage(Locale.FRENCH).replaceFirstChar { c -> c.uppercase() }
                            }.getOrNull()
                        } ?: "Piste ${trackIndex + 1}"

                        val chanStr = formatAudioChannels(format.channelCount)
                        val codecStr = formatAudioCodec(format.sampleMimeType)
                        val displayLabel = "$baseLabel ($chanStr • $codecStr)"

                        audioList.add(
                            TrackInfo(
                                groupIndex = groupIndex,
                                trackIndex = trackIndex,
                                id = format.id ?: "$groupIndex-$trackIndex",
                                label = displayLabel,
                                language = lang,
                                isSelected = isSelected,
                                codec = codecStr,
                                channels = chanStr,
                                bitrate = format.bitrate.toLong().coerceAtLeast(0L),
                                sampleRate = format.sampleRate
                            )
                        )
                    }
                    C.TRACK_TYPE_TEXT -> {
                        val lang = format.language ?: "und"
                        val baseLabel = format.label ?: format.language?.let {
                            runCatching {
                                Locale(it).getDisplayLanguage(Locale.FRENCH).replaceFirstChar { c -> c.uppercase() }
                            }.getOrNull()
                        } ?: "Sous-titres ${trackIndex + 1}"

                        subtitleList.add(
                            TrackInfo(
                                groupIndex = groupIndex,
                                trackIndex = trackIndex,
                                id = format.id ?: "$groupIndex-$trackIndex",
                                label = baseLabel,
                                language = lang,
                                isSelected = isSelected
                            )
                        )
                    }
                }
            }
        }

        _availableVideoTracks.value = videoList
        _availableAudioTracks.value = audioList
        _availableSubtitleTracks.value = subtitleList
    }

    /**
     * Met à jour les métriques en temps réel de lecture vidéo et audio
     */
    fun updatePlaybackStats() {
        val vFmt = exoPlayer.videoFormat
        val aFmt = exoPlayer.audioFormat
        val speed = exoPlayer.playbackParameters.speed
        val bufferSec = (exoPlayer.totalBufferedDuration.coerceAtLeast(0L) / 1000f)

        val selectedVideoTrack = _availableVideoTracks.value.firstOrNull { it.isSelected }

        val vWidth = selectedVideoTrack?.width?.takeIf { it > 0 }
            ?: vFmt?.width?.takeIf { it > 0 }
            ?: exoPlayer.videoSize.width
        val vHeight = selectedVideoTrack?.height?.takeIf { it > 0 }
            ?: vFmt?.height?.takeIf { it > 0 }
            ?: exoPlayer.videoSize.height
        val fps = selectedVideoTrack?.frameRate?.takeIf { it > 0 }
            ?: vFmt?.frameRate?.takeIf { it > 0 }
            ?: 0f
        val vBitrate = selectedVideoTrack?.bitrate?.takeIf { it > 0 }
            ?: vFmt?.bitrate?.toLong()?.coerceAtLeast(0L)
            ?: 0L

        val vCodec = formatCodecName(vFmt?.sampleMimeType, vFmt?.codecs)
        val hdr = formatHdr(vFmt)

        val aCodec = formatAudioCodec(aFmt?.sampleMimeType)
        val aChannels = formatAudioChannels(aFmt?.channelCount ?: 0)
        val aSampleRate = aFmt?.sampleRate ?: 0
        val aBitrate = aFmt?.bitrate?.toLong()?.coerceAtLeast(0L) ?: 0L

        val activeAudio = _availableAudioTracks.value.firstOrNull { it.isSelected }?.label
            ?: (aFmt?.label ?: aFmt?.language ?: "Principale")
        val activeSub = _availableSubtitleTracks.value.firstOrNull { it.isSelected }?.label
            ?: "Désactivés"

        _playbackStats.value = PlaybackStats(
            width = vWidth,
            height = vHeight,
            fps = fps,
            videoBitrate = vBitrate,
            videoCodec = vCodec,
            hdrInfo = hdr,
            audioCodec = aCodec,
            audioChannels = aChannels,
            audioSampleRate = aSampleRate,
            audioBitrate = aBitrate,
            activeAudioLabel = activeAudio,
            activeSubtitleLabel = activeSub,
            speed = speed,
            bufferHealthSeconds = bufferSec
        )
    }

    private fun formatCodecName(mimeType: String?, codecs: String?): String {
        return when {
            codecs?.contains("hvc", ignoreCase = true) == true ||
            codecs?.contains("hev", ignoreCase = true) == true ||
            mimeType?.contains("hevc", ignoreCase = true) == true ||
            mimeType?.contains("h265", ignoreCase = true) == true -> "HEVC (H.265)"
            codecs?.contains("avc", ignoreCase = true) == true ||
            mimeType?.contains("avc", ignoreCase = true) == true ||
            mimeType?.contains("h264", ignoreCase = true) == true -> "AVC (H.264)"
            codecs?.contains("av01", ignoreCase = true) == true ||
            mimeType?.contains("av01", ignoreCase = true) == true ||
            mimeType?.contains("av1", ignoreCase = true) == true -> "AV1"
            codecs?.contains("vp9", ignoreCase = true) == true ||
            mimeType?.contains("vp9", ignoreCase = true) == true -> "VP9"
            else -> codecs ?: mimeType?.substringAfter('/')?.uppercase() ?: "Inconnu"
        }
    }

    private fun formatAudioCodec(mimeType: String?): String {
        return when {
            mimeType == null -> "Audio Standard"
            mimeType.contains("eac3", ignoreCase = true) -> "Dolby Digital Plus (E-AC-3)"
            mimeType.contains("ac3", ignoreCase = true) -> "Dolby Digital (AC-3)"
            mimeType.contains("ac4", ignoreCase = true) -> "Dolby AC-4"
            mimeType.contains("dts", ignoreCase = true) -> "DTS"
            mimeType.contains("truehd", ignoreCase = true) -> "Dolby TrueHD"
            mimeType.contains("flac", ignoreCase = true) -> "FLAC"
            mimeType.contains("opus", ignoreCase = true) -> "Opus"
            mimeType.contains("mp4a", ignoreCase = true) || mimeType.contains("aac", ignoreCase = true) -> "AAC"
            mimeType.contains("mp3", ignoreCase = true) || mimeType.contains("mpeg", ignoreCase = true) -> "MP3"
            else -> mimeType.substringAfter('/').uppercase()
        }
    }

    private fun formatAudioChannels(channels: Int): String {
        return when (channels) {
            1 -> "Mono 1.0"
            2 -> "Stéréo 2.0"
            6 -> "Surround 5.1"
            8 -> "Surround 7.1"
            else -> if (channels > 0) "$channels canaux" else "Stéréo"
        }
    }

    private fun formatHdr(format: androidx.media3.common.Format?): String {
        val color = format?.colorInfo ?: return if (_isHdrActive.value) "HDR10" else "SDR (Standard)"
        return when (color.colorTransfer) {
            C.COLOR_TRANSFER_ST2084 -> "HDR10 / Dolby Vision"
            C.COLOR_TRANSFER_HLG -> "HLG"
            else -> if (color.colorSpace == C.COLOR_SPACE_BT2020) "HDR BT.2020" else "SDR (Rec.709)"
        }
    }
}
