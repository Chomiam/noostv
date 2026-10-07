package io.noostv.core.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.staticCompositionLocalOf
import io.noostv.R
import io.noostv.core.storage.SessionManager

val LocalSoundEffectManager = staticCompositionLocalOf<SoundEffectManager?> { null }

/**
 * Gestionnaire d'effets sonores de navigation d'interface (style Apple TV).
 *
 * Utilise un SoundPool à très faible latence avec limitation de cadence (rate limiting)
 * pour un rendu fluide, discret et élégant lors de la navigation à la télécommande TV ou au tactile.
 */
class SoundEffectManager(
    private val context: Context,
    private val sessionManager: SessionManager
) {
    private var soundPool: SoundPool? = null
    private var focusSoundId: Int = 0
    private var selectSoundId: Int = 0
    private var backSoundId: Int = 0
    private var bumpSoundId: Int = 0

    private var isLoaded: Boolean = false
    private var lastFocusPlayTimeMs: Long = 0L

    companion object {
        private const val TAG = "SoundEffectManager"
        private const val FOCUS_RATE_LIMIT_MS = 45L // Empêche la saturation lors du défilement continu
    }

    init {
        try {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val pool = SoundPool.Builder()
                .setMaxStreams(4)
                .setAudioAttributes(audioAttributes)
                .build()

            soundPool = pool

            var loadedCount = 0
            pool.setOnLoadCompleteListener { _, sampleId, status ->
                Log.d(TAG, "Sample loaded: id=$sampleId, status=$status")
                if (status == 0) {
                    loadedCount++
                    if (loadedCount >= 4) {
                        isLoaded = true
                        Log.d(TAG, "All sound effects loaded successfully")
                    }
                }
            }

            focusSoundId = pool.load(context, R.raw.nav_focus, 1)
            selectSoundId = pool.load(context, R.raw.nav_select, 1)
            backSoundId = pool.load(context, R.raw.nav_back, 1)
            bumpSoundId = pool.load(context, R.raw.nav_bump, 1)
        } catch (e: Exception) {
            Log.e(TAG, "Erreur initialisation SoundPool", e)
        }
    }

    /**
     * Bruit de déplacement de curseur / focus (tick discret Apple TV)
     */
    fun playFocus() {
        if (!sessionManager.isSoundEffectsEnabled) return
        val now = SystemClock.uptimeMillis()
        if (now - lastFocusPlayTimeMs < FOCUS_RATE_LIMIT_MS) return
        lastFocusPlayTimeMs = now

        val pool = soundPool ?: return
        if (focusSoundId != 0) {
            val vol = sessionManager.soundEffectsVolume.coerceIn(0.05f, 1.0f)
            val streamId = pool.play(focusSoundId, vol, vol, 1, 0, 1.0f)
            Log.d(TAG, "playFocus streamId=$streamId (vol=$vol)")
        }
    }

    /**
     * Bruit de confirmation / clic (tap / pop Apple TV)
     */
    fun playSelect() {
        if (!sessionManager.isSoundEffectsEnabled) return
        val pool = soundPool ?: return
        if (selectSoundId != 0) {
            val vol = (sessionManager.soundEffectsVolume * 1.15f).coerceIn(0.05f, 1.0f)
            val streamId = pool.play(selectSoundId, vol, vol, 2, 0, 1.0f)
            Log.d(TAG, "playSelect streamId=$streamId (vol=$vol)")
        }
    }

    /**
     * Bruit de retour / fermeture (descente douce Apple TV)
     */
    fun playBack() {
        if (!sessionManager.isSoundEffectsEnabled) return
        val pool = soundPool ?: return
        if (backSoundId != 0) {
            val vol = (sessionManager.soundEffectsVolume * 0.95f).coerceIn(0.05f, 1.0f)
            val streamId = pool.play(backSoundId, vol, vol, 2, 0, 1.0f)
            Log.d(TAG, "playBack streamId=$streamId (vol=$vol)")
        }
    }

    /**
     * Bruit de butée / limite
     */
    fun playBump() {
        if (!sessionManager.isSoundEffectsEnabled) return
        val pool = soundPool ?: return
        if (bumpSoundId != 0) {
            val vol = (sessionManager.soundEffectsVolume * 0.85f).coerceIn(0.05f, 1.0f)
            pool.play(bumpSoundId, vol, vol, 1, 0, 1.0f)
        }
    }

    fun release() {
        try {
            soundPool?.release()
            soundPool = null
            isLoaded = false
        } catch (e: Exception) {
            Log.e(TAG, "Erreur libération SoundPool", e)
        }
    }
}
