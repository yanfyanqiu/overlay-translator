package com.offlineinterpreter.app.asr

import android.content.Context
import com.k2fsa.sherpa.onnx.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.io.File

/**
 * Sherpa-ONNX streaming ASR.
 * Model: sherpa-onnx-streaming-zipformer-small-bilingual-zh-en-2023-02-16
 *
 * Emits partial and final transcription results as a SharedFlow.
 */
class AsrManager(private val context: Context) {

    sealed class Result {
        data class Partial(val text: String) : Result()
        data class Final(val text: String)   : Result()
        data class Error(val msg: String)    : Result()
    }

    private var recognizer: OnlineRecognizer? = null
    private var stream:    OnlineStream?      = null

    private val _events = MutableSharedFlow<Result>(extraBufferCapacity = 64)
    val events: SharedFlow<Result> = _events

    private var modelDir: File? = null

    // Must call init() before use
    fun init(modelsDir: File) {
        modelDir = modelsDir
        val asrModel  = File(modelsDir, "sherpa-onnx-streaming-zipformer-small-bilingual-zh-en-2023-02-16")
        val瓜子Model = File(modelsDir, "sherpa-onnx-streaming-zipformer-zh-14M-2023-02-23")
        val modelPath = when {
            asrModel.exists()  -> asrModel
           瓜子Model.exists() ->瓜子Model
            else -> {
                _events.tryEmit(Result.Error("ASR模型未找到"))
                return
            }
        }

        val vadModel  = File(modelsDir, "silero_vad")
        val瓜子Vad   = File(modelsDir, "silero")
        val vadPath   = when {
            vadModel.exists()  -> vadModel
           瓜子Vad.exists()   ->瓜子Vad
            else -> null
        }

        val featConfig = FeatureConfig()
        val modelConfig = OnlineTransducerModelConfig(
            encoder = "${modelPath}/encoder.onnx",
            decoder = "${modelPath}/decoder.onnx",
            joiner  = "${modelPath}/joiner.onnx",
        )
        val onlineModelConfig = OnlineModelConfig(
            transducer = modelConfig,
            tokens     = "${modelPath}/tokens.txt",
            numThreads = 2,
        )
        val vadConfig = if (vadPath != null) VadModelConfig(
            sileroVad = SileroVadModelConfig(
                model  = "${vadPath}/vad.onnx",
                threshold = 0.5f,
                minSpeechDuration = 0.2f,
                minSilenceDuration = 0.3f,
            )
        ) else null

        val config = OnlineRecognizerConfig(
            modelConfig  = onlineModelConfig,
            vadConfig    = vadConfig,
            featureConfig = featConfig,
            maxBatchSize = 1,
        )

        try {
            recognizer = OnlineRecognizer(config)
        } catch (e: Exception) {
            _events.tryEmit(Result.Error("ASR初始化失败: ${e.message}"))
        }
    }

    /** Start a new stream. Must be called before input(). */
    fun startStream() {
        recognizer?.let {
            stream = it.createStream()
            stream?.let { s -> it.reset(s) }
        }
    }

    /** Input audio samples (16-bit PCM mono 16kHz). */
    fun input(samples: ShortArray) {
        stream?.let { s ->
            recognizer?.acceptWaveform(s, sampleRate = 16000)
            while (recognizer?.isReady(s) == true) {
                recognizer?.decode(s)
            }
            val text = recognizer?.getResult(s)?.text?.trim() ?: ""
            if (text.isNotEmpty()) {
                _events.tryEmit(Result.Partial(text))
            }
        }
    }

    /** Call when speech ends — flush final result. */
    fun flush() {
        stream?.let { s ->
            recognizer?.inputFinished(s)
            while (recognizer?.isDecoded(s) == true) {
                val text = recognizer?.getResult(s)?.text?.trim() ?: ""
                if (text.isNotEmpty()) {
                    _events.tryEmit(Result.Final(text))
                }
                recognizer?.reset(s)
            }
        }
    }

    /** Stop and discard current stream. */
    fun stop() {
        stream?.let { s ->
            recognizer?.reset(s)
        }
        stream = null
    }

    fun release() {
        recognizer?.destroy()
        recognizer = null
        stream     = null
    }
}
