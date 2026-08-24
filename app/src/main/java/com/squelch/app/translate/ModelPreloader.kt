package com.squelch.app.translate

import android.util.Log
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModelPreloader @Inject constructor() {

    companion object {
        private const val TAG = "ModelPreloader"
        private const val PER_MODEL_TIMEOUT_MS = 30_000L

        val SUPPORTED_LANGUAGES = listOf(
            "af", "ar", "be", "bg", "bn", "ca", "cs", "cy", "da", "de", "el", "en",
            "eo", "es", "et", "fa", "fi", "fr", "ga", "gl", "gu", "he", "hi", "hr",
            "ht", "hu", "id", "is", "it", "ja", "ka", "kn", "ko", "lt", "lv", "mk",
            "mr", "ms", "mt", "nl", "no", "pl", "pt", "ro", "ru", "sk", "sl", "sq",
            "sv", "sw", "ta", "te", "th", "tl", "tr", "uk", "ur", "vi", "zh"
        )
    }

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading.asStateFlow()

    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    private val _currentModel = MutableStateFlow("")
    val currentModel: StateFlow<String> = _currentModel.asStateFlow()

    private val _statusText = MutableStateFlow("")
    val statusText: StateFlow<String> = _statusText.asStateFlow()

    private val _failedModels = MutableStateFlow<List<String>>(emptyList())
    val failedModels: StateFlow<List<String>> = _failedModels.asStateFlow()

    var totalModels = 0
        private set
    var downloadedModels = 0
        private set

    private var currentIndex = 0

    suspend fun preloadAllModels(onProgress: ((Float, Int, Int) -> Unit)? = null) {
        if (_isDownloading.value) return
        _isDownloading.value = true
        _isPaused.value = false
        _failedModels.value = emptyList()
        downloadedModels = 0
        currentIndex = 0

        totalModels = SUPPORTED_LANGUAGES.size
        _statusText.value = "Downloading translation models..."
        Log.d(TAG, "Preloading $totalModels language models")

        val failed = mutableListOf<String>()

        withContext(Dispatchers.IO) {
            while (currentIndex < SUPPORTED_LANGUAGES.size) {
                ensureActive()

                while (_isPaused.value) {
                    _statusText.value = "Paused (${downloadedModels}/${totalModels})"
                    kotlinx.coroutines.delay(500)
                }

                val code = SUPPORTED_LANGUAGES[currentIndex]

                if (code == "en") {
                    downloadedModels++
                    currentIndex++
                    _progress.value = downloadedModels.toFloat() / totalModels
                    continue
                }

                _currentModel.value = code
                _statusText.value = "Downloading $code (${downloadedModels + 1}/${totalModels})"

                try {
                    val lang = TranslateLanguage.fromLanguageTag(code)
                    if (lang == null) {
                        Log.w(TAG, "Unknown language code: $code, skipping")
                        downloadedModels++
                        currentIndex++
                        _progress.value = downloadedModels.toFloat() / totalModels
                        continue
                    }
                    val options = TranslatorOptions.Builder()
                        .setSourceLanguage(lang)
                        .setTargetLanguage(TranslateLanguage.ENGLISH)
                        .build()
                    val translator = Translation.getClient(options)

                    val downloadResult = withTimeoutOrNull(PER_MODEL_TIMEOUT_MS) {
                        translator.downloadModelIfNeeded().await()
                    }

                    if (downloadResult == null) {
                        Log.w(TAG, "Model download timed out for $code")
                        failed.add(code)
                        _failedModels.value = failed.toList()
                    } else {
                        Log.d(TAG, "Downloaded model: $code (${downloadedModels + 1}/${totalModels})")
                    }

                    translator.close()
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to download model $code: ${e.message}")
                    failed.add(code)
                    _failedModels.value = failed.toList()
                }

                downloadedModels++
                currentIndex++
                _progress.value = downloadedModels.toFloat() / totalModels
                onProgress?.invoke(_progress.value, downloadedModels, totalModels)
            }
        }

        _isDownloading.value = false
        _currentModel.value = ""
        _statusText.value = if (failed.isEmpty()) {
            "All models ready"
        } else {
            "${failed.size} models failed - tap refresh to retry"
        }
        Log.d(TAG, "All models preloaded: $downloadedModels/$totalModels (${failed.size} failed)")
    }

    fun pause() {
        _isPaused.value = true
        _statusText.value = "Pausing..."
    }

    fun resume() {
        _isPaused.value = false
    }

    fun refresh() {
        if (_isDownloading.value) return
        currentIndex = 0
        downloadedModels = 0
        _progress.value = 0f
        _failedModels.value = emptyList()
    }
}
