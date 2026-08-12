package com.offlineinterpreter.app.pipeline

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioTrack
import com.offlineinterpreter.app.asr.AsrManager
import com.offlineinterpreter.app.audio.AudioPlayer
import com.offlineinterpreter.app.audio.StereoMixer
import com.offlineinterpreter.app.mode.Mode
import com.offlineinterpreter.app.mode.TranslationDirection
import com.offlineinterpreter.app.tts.TtsManager
import com.offlineinterpreter.lib.InferenceEngine
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * TranslationPipeline — orchestrates the full translation pipeline.
 *
 * Supports Mode A (simultaneous), Mode B (BT stereo), Mode C (BT+speaker split),
 * Mode D (text), Mode E (PTT).
 *
 * All speech playback = translated text only. Never plays source speech.
 */
class TranslationPipeline(
    private val inferenceEngine: Any,  // InferenceEngine companion object
    private val asrManager: AsrManager,
    private val ttsManager: TtsManager,
    private val audioPlayer: AudioPlayer,
) {
    // State
    private val _currentMode    = MutableStateFlow(Mode.TEXT_TRANSLATION)
    val currentMode: StateFlow<Mode> = _currentMode.asStateFlow()

    private val _direction      = MutableStateFlow(TranslationDirection.ZH_EN)
    val direction: StateFlow<TranslationDirection> = _direction.asStateFlow()

    private val _status         = MutableStateFlow("就绪")
    val status: StateFlow<String> = _status.asStateFlow()

    private val _partialText    = MutableStateFlow("")
    val partialText: StateFlow<String> = _partialText.asStateFlow()

    private val _sourceText     = MutableStateFlow("")
    val sourceText: StateFlow<String> = _sourceText.asStateFlow()

    private val _translatedText = MutableStateFlow("")
    val translatedText: StateFlow<String> = _translatedText.asStateFlow()

    private val _isRunning      = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _btConnected    = MutableStateFlow(false)
    val btConnected: StateFlow<Boolean> = _btConnected.asStateFlow()

    // Stereo mixer for mode B
    private val stereoMixer = StereoMixer()

    private var scope: CoroutineScope? = null
    private var pipelineJob: Job?      = null

    // Sentence buffer for Mode A
    private val sentenceBuffer = StringBuilder()
    private var lastSentenceEnd = 0L

    fun setScope(scope: CoroutineScope) { this.scope = scope }

    fun setMode(mode: Mode) {
        if (_isRunning.value) stop()
        _currentMode.value = mode
    }

    fun setDirection(dir: TranslationDirection) {
        _direction.value = dir
    }

    fun toggleDirection() {
        _direction.value = _direction.value.toggle()
    }

    fun setBluetoothConnected(connected: Boolean) {
        _btConnected.value = connected
    }

    // ─── Mode A: Simultaneous interpretation ────────────────────────────────
    fun startSimultaneous() {
        if (_isRunning.value) return
        _isRunning.value = true
        _status.value = "同声传译中..."
        _sourceText.value = ""
        _translatedText.value = ""

        pipelineJob = scope?.launch(Dispatchers.Default) {
            asrManager.startStream()
            asrManager.events.filterIsInstance<AsrManager.Result.Final>()
                .collect { result ->
                    translateAndSpeak(result.text, _direction.value)
                }
        }
    }

    fun stopSimultaneous() {
        pipelineJob?.cancel()
        pipelineJob = null
        asrManager.stop()
        _isRunning.value = false
        _status.value = "已停止"
    }

    // ─── Mode E: Push-to-Talk ───────────────────────────────────────────────
    fun startPtt() {
        if (_isRunning.value) return
        _isRunning.value = true
        _status.value = "正在听..."
        _partialText.value = ""
        _sourceText.value = ""
        _translatedText.value = ""

        pipelineJob = scope?.launch(Dispatchers.Default) {
            asrManager.startStream()
            asrManager.events.collect { event ->
                when (event) {
                    is AsrManager.Result.Partial -> {
                        _partialText.value = event.text
                    }
                    is AsrManager.Result.Final -> {
                        _partialText.value = ""
                        _sourceText.value = event.text
                        translateAndSpeak(event.text, _direction.value)
                    }
                    is AsrManager.Result.Error -> {
                        _status.value = "识别错误: ${event.msg}"
                        _isRunning.value = false
                    }
                }
            }
        }
    }

    fun stopPtt() {
        pipelineJob?.cancel()
        pipelineJob = null
        asrManager.flush()
        _isRunning.value = false
        _status.value = "处理中..."
    }

    // ─── Mode D: Text translation ───────────────────────────────────────────
    suspend fun translateText(text: String, dir: TranslationDirection): String {
        if (text.isBlank()) return ""
        _status.value = "翻译中..."
        val result = InferenceEngine.translate(text, dir.srcLang, dir.tgtLang)
        _translatedText.value = result
        _status.value = "就绪"
        return result
    }

    fun speakTranslated(langCode: String) {
        val text = _translatedText.value
        if (text.isNotBlank()) {
            ttsManager.speak(text, langCode)
        }
    }

    // ─── Core translation + TTS (all modes) ─────────────────────────────────
    private suspend fun translateAndSpeak(text: String, dir: TranslationDirection) {
        if (text.isBlank()) return
        _sourceText.value = text
        _status.value = "翻译中..."
        val translated = InferenceEngine.translate(text, dir.srcLang, dir.tgtLang)
        _translatedText.value = translated
        _status.value = "播放中..."
        ttsManager.speak(translated, dir.tgtCode)
        _status.value = "同声传译中..."
    }

    // ─── Mode B: Bluetooth stereo ───────────────────────────────────────────
    fun playStereo(leftSamples: ShortArray, rightSamples: ShortArray) {
        val stereo = stereoMixer.toStereoInterleaved(leftSamples, rightSamples)
        audioPlayer.play(stereo)
    }

    // ─── Mode C: BT + speaker split ─────────────────────────────────────────
    fun playOnDevice(samples: ShortArray, device: AudioDeviceInfo?) {
        audioPlayer.play(samples, device)
    }

    fun stop() {
        stopSimultaneous()
        ttsManager.stop()
    }
}
