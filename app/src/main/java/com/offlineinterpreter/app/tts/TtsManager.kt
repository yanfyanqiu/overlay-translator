package com.offlineinterpreter.app.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.k2fsa.sherpa.onnx.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.io.File
import java.util.*

/**
 * TTS using Sherpa-ONNX + Piper models.
 * Supports Chinese and English, outputs PCM for stereo / multi-device routing.
 *
 * Falls back to Android TTS if Piper models are not bundled.
 */
class TtsManager(private val context: Context) {

    sealed class Event {
        data object Started   : Event()
        data object Done      : Event()
        data class Error(val msg: String) : Event()
    }

    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 4)
    val events: SharedFlow<Event> = _events

    private var androidTts: TextToSpeech? = null
    private var piperTts: OfflineTts?     = null
    private var usePiper = false

    private var modelsDir: File? = null

    init {
        // Always init Android TTS as fallback
        androidTts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                androidTts?.language = Locale.CHINA
            }
        }
    }

    fun init(modelsDir: File) {
        this.modelsDir = modelsDir
        try {
            val zhPiper = File(modelsDir, "piper/zh_CN-xiao_ya-medium.onnx")
            val enPiper = File(modelsDir, "piper/en_US-lessac-medium.onnx")
            if (zhPiper.exists() || enPiper.exists()) {
                val config = OfflineTtsConfig(
                    model   = OfflineModelConfig(
                        piper = if (zhPiper.exists()) "${zhPiper}" else "${enPiper}"
                    ),
                    verbosity = 0,
                )
                piperTts = OfflineTts(config)
                usePiper = true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            usePiper = false
        }
    }

    /**
     * Speak text. Language code "zh" or "en".
     * Uses AudioTrack for PCM output (stereo/multi-device capable).
     */
    fun speak(text: String, langCode: String, speed: Float = 1.0f) {
        if (text.isBlank()) return

        if (usePiper && piperTts != null) {
            speakPiper(text, langCode, speed)
        } else {
            speakAndroid(text, langCode)
        }
    }

    private fun speakPiper(text: String, langCode: String, speed: Float) {
        try {
            val modelFile = modelsDir?.let {
                val zh = File(it, "piper/zh_CN-xiao_ya-medium.onnx")
                val en = File(it, "piper/en_US-lessac-medium.onnx")
                when (langCode) {
                    "zh" -> if (zh.exists()) zh else null
                    "en" -> if (en.exists()) en else null
                    else -> null
                }
            }
            if (modelFile == null) {
                speakAndroid(text, langCode)
                return
            }

            // Reload with correct model if needed (TBD: handle model switching)
            val config = OfflineTtsConfig(
                model = OfflineModelConfig(piper = "${modelFile}"),
                verbosity = 0,
            )
            val tts = try { OfflineTts(config) } catch (_: Exception) { null } ?: run {
                speakAndroid(text, langCode); return
            }

            val generated = tts.generate(text, OfflineTtsConfig.generateMaxNumWords(1000))
            if (generated.samples.isEmpty()) {
                speakAndroid(text, langCode); return
            }

            val sampleRate = generated.sampleRate.toInt()
            val samples    = generated.samples

            // Play via AudioTrack (16-bit mono → convert to stereo PCM)
            val stereo = ShortArray(samples.size * 2)
            for (i in samples.indices) {
                val s = samples[i].toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                stereo[i * 2]     = s   // left channel
                stereo[i * 2 + 1] = s   // right channel
            }

            playPcm(stereo, sampleRate)
        } catch (e: Exception) {
            e.printStackTrace()
            speakAndroid(text, langCode)
        }
    }

    private fun playPcm(samples: ShortArray, sampleRate: Int) {
        val bufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(bufferSize, samples.size * 2))
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        _events.tryEmit(Event.Started)
        track.write(samples, 0, samples.size)
        track.play()
        track.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
            override fun onMarkerReached(track: AudioTrack?) {
                track?.stop()
                track?.release()
                _events.tryEmit(Event.Done)
            }
            override fun onPeriodicNotification(track: AudioTrack?) {}
        })
        track.setNotificationMarkerPosition(samples.size / 2)
    }

    private fun speakAndroid(text: String, langCode: String) {
        val tts = androidTts ?: return
        val locale = if (langCode == "zh") Locale.CHINA else Locale.US
        tts.language = locale
        val utteranceId = UUID.randomUUID().toString()
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) { _events.tryEmit(Event.Started) }
            override fun onDone(id: String?)            { _events.tryEmit(Event.Done) }
            override fun onError(utteranceId: String?)  { _events.tryEmit(Event.Error("TTS error")) }
        })
        tts.setSpeechRate(1.0f)
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    fun stop() {
        androidTts?.stop()
    }

    fun release() {
        androidTts?.shutdown()
        androidTts = null
        piperTts?.destroy()
        piperTts = null
    }
}
