package com.offlineinterpreter.app.audio

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Raw PCM audio recorder: 16-bit mono 16 kHz.
 * Emits buffers as ShortArray.
 */
class AudioRecorder {

    sealed class Event {
        data class Buffer(val data: ShortArray) : Event()
        data class Error(val msg: String)       : Event()
    }

    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 64)
    val events: SharedFlow<Event> = _events

    private var recorder: AudioRecord?  = null
    private var job:      Job?          = null
    private var recording = false

    private val sampleRate   = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat   = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize    = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

    fun start(scope: CoroutineScope) {
        if (recording) return
        if (ContextCompat.checkSelfPermission(
                kotlinx.coroutines.CoroutineScope::class.java as Any as android.content.Context,
                Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        try {
            recorder = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize,
            )
            recorder?.startRecording()
            recording = true
            job = scope.launch(Dispatchers.IO) {
                val buf = ShortArray(bufferSize / 2)
                while (isActive && recording) {
                    val n = recorder?.read(buf, 0, buf.size) ?: -1
                    if (n > 0) {
                        _events.emit(Event.Buffer(buf.copyOf(n)))
                    } else if (n < 0) {
                        _events.emit(Event.Error("AudioRecord error: $n"))
                        break
                    }
                }
            }
        } catch (e: Exception) {
            _events.tryEmit(Event.Error("录音启动失败: ${e.message}"))
        }
    }

    fun stop() {
        recording = false
        job?.cancel()
        try { recorder?.stop() } catch (_: Exception) {}
        recorder?.release()
        recorder = null
    }
}
