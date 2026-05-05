package com.hypergallery

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache

class HyperGalleryApplication : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    // PERF: 20% da RAM disponível — equilibrio entre cache e espaço para ML Kit
                    .maxSizePercent(0.20)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    // PERF: 150MB de cache em disco para evitar redecodificações frequentes
                    .maxSizeBytes(150L * 1024 * 1024)
                    .build()
            }
            .components {
                add(VideoFrameDecoder.Factory())
            }
            .crossfade(true)
            // PERF: Permite que o Coil use mais threads para decodificação paralela
            .fetcherDispatcher(kotlinx.coroutines.Dispatchers.IO)
            .build()
    }
}
