package com.jerometranslator.app

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Wraps the system TextToSpeech engine, preferring the highest-quality
 * voice available for the target language to sound as natural as possible.
 */
class TtsHelper(context: Context, private val onReady: (Boolean) -> Unit) {

    private var tts: TextToSpeech? = null
    var ready: Boolean = false
        private set

    init {
        tts = TextToSpeech(context) { status ->
            ready = status == TextToSpeech.SUCCESS
            onReady(ready)
        }
    }

    fun speak(text: String, language: Locale) {
        val t = tts ?: return
        if (!ready || text.isBlank()) return
        try {
            t.language = language
            // Prefer the highest quality voice for a more natural voice
            val best = t.voices
                ?.filter { it.locale.language == language.language }
                ?.maxWithOrNull(compareBy({ it.quality }, { -it.latency }))
            if (best != null) {
                t.voice = best
            }
            t.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tts-${System.currentTimeMillis()}")
        } catch (_: Exception) {
            // ignore TTS errors
        }
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.shutdown()
    }
}
