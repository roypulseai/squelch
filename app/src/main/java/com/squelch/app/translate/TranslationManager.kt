package com.squelch.app.translate

import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap

object TranslationManager {

    private const val TAG = "TranslationManager"
    private const val DETECT_TIMEOUT_MS = 8_000L
    private const val TRANSLATE_TIMEOUT_MS = 15_000L
    private const val MIN_TEXT_LENGTH = 2

    private val languageIdentifier = LanguageIdentification.getClient()
    private val translators = ConcurrentHashMap<String, Translator>()

    fun isLanguageSupported(langCode: String): Boolean {
        return TranslateLanguage.fromLanguageTag(langCode) != null
    }

    suspend fun detectLanguage(text: String): String? = withContext(Dispatchers.IO) {
        if (text.isBlank() || text.length < MIN_TEXT_LENGTH) return@withContext null
        try {
            val result = withTimeoutOrNull(DETECT_TIMEOUT_MS) {
                Tasks.await(languageIdentifier.identifyLanguage(text))
            }
            if (result == null || result == "und") null else result
        } catch (e: Exception) {
            Log.w(TAG, "Language detection failed: ${e.message}")
            null
        }
    }

    suspend fun translate(text: String, sourceLang: String, targetLang: String): String? = withContext(Dispatchers.IO) {
        if (text.isBlank() || text.length < MIN_TEXT_LENGTH) return@withContext null
        if (sourceLang == targetLang) return@withContext text

        val sourceTranslateLang = TranslateLanguage.fromLanguageTag(sourceLang)
        val targetTranslateLang = TranslateLanguage.fromLanguageTag(targetLang)

        if (sourceTranslateLang == null || targetTranslateLang == null) {
            Log.w(TAG, "Unsupported language pair: $sourceLang -> $targetLang")
            return@withContext null
        }

        val key = "${sourceTranslateLang}_$targetTranslateLang"
        val translator = translators.getOrPut(key) {
            val options = TranslatorOptions.Builder()
                .setSourceLanguage(sourceTranslateLang)
                .setTargetLanguage(targetTranslateLang)
                .build()
            Translation.getClient(options)
        }

        try {
            withTimeoutOrNull(TRANSLATE_TIMEOUT_MS) {
                Tasks.await(translator.downloadModelIfNeeded())
                Tasks.await(translator.translate(text))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Translation failed: ${e.message}")
            null
        }
    }

    suspend fun translateIfNeeded(
        text: String,
        preferredLang: String
    ): TranslationResult {
        if (text.isBlank() || preferredLang.isBlank() || text.length < MIN_TEXT_LENGTH) {
            return TranslationResult(original = text, translated = null, sourceLang = null)
        }

        val detectedLang = detectLanguage(text)

        if (detectedLang == null || detectedLang == preferredLang) {
            return TranslationResult(original = text, translated = null, sourceLang = detectedLang)
        }

        val translated = translate(text, detectedLang, preferredLang)
        return TranslationResult(
            original = text,
            translated = translated,
            sourceLang = detectedLang
        )
    }

    fun close() {
        translators.values.forEach { it.close() }
        translators.clear()
    }
}

data class TranslationResult(
    val original: String,
    val translated: String?,
    val sourceLang: String?
)
