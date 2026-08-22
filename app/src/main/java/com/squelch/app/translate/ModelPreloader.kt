package com.squelch.app.translate

import android.util.Log
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModelPreloader @Inject constructor() {

    companion object {
        private const val TAG = "ModelPreloader"

        val SUPPORTED_LANGUAGES = listOf(
            "af", "ar", "be", "bg", "bn", "ca", "cs", "cy", "da", "de", "el", "en",
            "eo", "es", "et", "fa", "fi", "fr", "ga", "gl", "gu", "he", "hi", "hr",
            "ht", "hu", "id", "is", "it", "ja", "ka", "kn", "ko", "lt", "lv", "mk",
            "mr", "ms", "mt", "nl", "no", "pl", "pt", "ro", "ru", "sk", "sl", "sq",
            "sv", "sw", "ta", "te", "th", "tl", "tr", "uk", "ur", "vi", "zh"
        )
    }

    var progress: Float = 0f
        private set
    var isDownloading = false
        private set
    var totalModels = 0
        private set
    var downloadedModels = 0
        private set

    suspend fun preloadAllModels(onProgress: ((Float, Int, Int) -> Unit)? = null) {
        if (isDownloading) return
        isDownloading = true
        downloadedModels = 0

        totalModels = SUPPORTED_LANGUAGES.size
        Log.d(TAG, "Preloading $totalModels language models")

        withContext(Dispatchers.IO) {
            for ((index, code) in SUPPORTED_LANGUAGES.withIndex()) {
                if (code == "en") {
                    downloadedModels++
                    progress = downloadedModels.toFloat() / totalModels
                    continue
                }
                try {
                    val lang = TranslateLanguage.fromLanguageTag(code)
                    if (lang == null) {
                        Log.w(TAG, "Unknown language code: $code, skipping")
                        downloadedModels++
                        progress = downloadedModels.toFloat() / totalModels
                        continue
                    }
                    val options = TranslatorOptions.Builder()
                        .setSourceLanguage(lang)
                        .setTargetLanguage(TranslateLanguage.ENGLISH)
                        .build()
                    val translator = Translation.getClient(options)
                    translator.downloadModelIfNeeded().await()
                    translator.close()
                    downloadedModels++
                    progress = downloadedModels.toFloat() / totalModels
                    Log.d(TAG, "Downloaded model: $code (${downloadedModels}/${totalModels})")
                    onProgress?.invoke(progress, downloadedModels, totalModels)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to download model $code: ${e.message}")
                    downloadedModels++
                    progress = downloadedModels.toFloat() / totalModels
                    onProgress?.invoke(progress, downloadedModels, totalModels)
                }
            }
        }

        isDownloading = false
        Log.d(TAG, "All models preloaded: $downloadedModels/$totalModels")
    }

    fun cancel() {
        isDownloading = false
    }
}
