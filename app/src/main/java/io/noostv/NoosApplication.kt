package io.noostv

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache

import android.graphics.Bitmap
import coil.size.Precision

/**
 * Application centrale NoosTV avec configuration globale du cache d'images Coil
 * optimisée pour Android TV (Xiaomi Mi Box, Google TV Streamer).
 */
class NoosApplication : Application(), ImageLoaderFactory {

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25) // 25% de la mémoire max allouée
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(150L * 1024 * 1024) // 150 Mo de cache disque
                    .build()
            }
            // RGB_565 divise par 2 l'empreinte mémoire RAM de chaque miniature (2 octets/pixel au lieu de 4)
            .bitmapConfig(Bitmap.Config.RGB_565)
            .precision(Precision.INEXACT)
            .respectCacheHeaders(false) // Permet de conserver les affiches même si le serveur IPTV envoie 'no-cache'
            // Indispensable sur Android TV (Mali GPU) pour éviter les crashs de rendu matériel
            .allowHardware(false)
            .crossfade(true)
            .build()
    }
}
