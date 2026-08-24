package com.squelch.app.translate

import android.util.Log
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.CancellationException
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
        private const val PER_MODEL_TIMEOUT_MS = 120_000L

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
    private var retryOnly = false

    private val modelManager = RemoteModelManager.getInstance()
    private val conditions = DownloadConditions.Builder().build()

    suspend fun preloadAllModels(preferredLang: String = "en", onProgress: ((Float, Int, Int) -> Unit)? = null) {
        if (_isDownloading.value) return
        _isDownloading.value = true
        _isPaused.value = false

        val languagesToDownload = if (retryOnly && _failedModels.value.isNotEmpty()) {
            _failedModels.value.toList()
        } else {
            _failedModels.value = emptyList()
            downloadedModels = 0
            currentIndex = 0
            SUPPORTED_LANGUAGES.toList()
        }
        retryOnly = false

        totalModels = languagesToDownload.size
        _statusText.value = "Downloading translation models..."
        Log.d(TAG, "Preloading $totalModels language models")

        val failed = mutableListOf<String>()

        withContext(Dispatchers.IO) {
            for (code in languagesToDownload) {
                ensureActive()

                while (_isPaused.value) {
                    _statusText.value = "Paused (${downloadedModels}/${totalModels})"
                    kotlinx.coroutines.delay(500)
                }

                _currentModel.value = code
                _statusText.value = "Downloading $code (${downloadedModels + 1}/${totalModels})"

                try {
                    val lang = TranslateLanguage.fromLanguageTag(code)
                    if (lang == null) {
                        Log.w(TAG, "Unknown language code: $code, skipping")
                        downloadedModels++
                        _progress.value = downloadedModels.toFloat() / totalModels
                        continue
                    }

                    val model = TranslateRemoteModel.Builder(lang).build()
                    val result = withTimeoutOrNull(PER_MODEL_TIMEOUT_MS) {
                        modelManager.download(model, conditions).await()
                    }

                    if (result == null) {
                        Log.w(TAG, "Model download timed out for $code after ${PER_MODEL_TIMEOUT_MS}ms")
                        failed.add(code)
                        _failedModels.value = failed.toList()
                    } else {
                        Log.d(TAG, "Downloaded model: $code (${downloadedModels + 1}/${totalModels})")
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to download model $code: ${e.message} (${e.javaClass.simpleName})")
                    failed.add(code)
                    _failedModels.value = failed.toList()
                }

                downloadedModels++
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
        retryOnly = true
        _progress.value = 0f
        downloadedModels = 0
    }
}
