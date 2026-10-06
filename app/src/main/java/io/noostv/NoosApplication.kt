package io.noostv

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache

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
                    .maxSizeBytes(100L * 1024 * 1024) // 100 Mo de cache disque
                    .build()
            }
            // Indispensable sur Android TV (Mali GPU) pour éviter les crashs de rendu matériel
            .allowHardware(false)
            .crossfade(true)
            .build()
    }
}
