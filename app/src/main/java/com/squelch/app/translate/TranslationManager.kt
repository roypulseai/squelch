package com.squelch.app.translate

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

object TranslationManager {

    private const val TAG = "TranslationManager"

    private val languageIdentifier = LanguageIdentification.getClient()

    private val translators = ConcurrentHashMap<String, Translator>()

    private val supportedLanguages = TranslateLanguage.getAllLanguages().toSet()

    fun isLanguageSupported(langCode: String): Boolean {
        return TranslateLanguage.fromLanguageTag(langCode) != null
    }

    fun getSupportedLanguages(): List<Pair<String, String>> {
        return TranslateLanguage.getAllLanguages().map { code ->
            code to TranslateLanguage.fromLanguageTag(code).toString()
        }
    }

    suspend fun detectLanguage(text: String): String? = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext null
        try {
            val result = Tasks.await(languageIdentifier.identifyLanguage(text))
            if (result == "und") null else result
        } catch (e: Exception) {
            Log.w(TAG, "Language detection failed: ${e.message}")
            null
        }
    }

    suspend fun translate(text: String, sourceLang: String, targetLang: String): String? = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext null
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
            Tasks.await(translator.downloadModelIfNeeded())
            Tasks.await(translator.translate(text))
        } catch (e: Exception) {
            Log.e(TAG, "Translation failed: ${e.message}")
            null
        }
    }

    suspend fun translateIfNeeded(
        text: String,
        preferredLang: String
    ): TranslationResult = withContext(Dispatchers.IO) {
        if (text.isBlank() || preferredLang.isBlank()) {
            return@withContext TranslationResult(original = text, translated = null, sourceLang = null)
        }

        val detectedLang = detectLanguage(text)

        if (detectedLang == null || detectedLang == preferredLang) {
            return@withContext TranslationResult(original = text, translated = null, sourceLang = detectedLang)
        }

        val translated = translate(text, detectedLang, preferredLang)
        TranslationResult(
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
