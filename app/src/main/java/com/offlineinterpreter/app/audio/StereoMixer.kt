package com.offlineinterpreter.app.audio

import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * StereoMixer — for Mode B.
 * Takes two mono PCM streams (Chinese text / English text TTS output)
 * and mixes them into a stereo PCM buffer: left = Chinese, right = English.
 *
 * If only one language is available, the other channel is silence.
 */
class StereoMixer {

    sealed class Event {
        data class StereoBuffer(val left: ShortArray, val right: ShortArray, val sampleRate: Int) : Event()
    }

    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 8)
    val events: SharedFlow<Event> = _events

    /**
     * Mix two mono PCM buffers into stereo.
     * Both arrays must be same length. If null, that side is silent.
     */
    fun mix(leftMono: ShortArray?, rightMono: ShortArray?) {
        val len = maxOf(leftMono?.size ?: 0, rightMono?.size ?: 0)
        if (len == 0) return

        val left  = ShortArray(len) { i -> leftMono?.getOrElse(i)  { 0 } ?: 0 }
        val right = ShortArray(len) { i -> rightMono?.getOrElse(i) { 0 } ?: 0 }

        _events.tryEmit(Event.StereoBuffer(left, right, 16000))
    }

    /** Build interleaved stereo ShortArray from two mono arrays. */
    fun toStereoInterleaved(left: ShortArray, right: ShortArray): ShortArray {
        val len = maxOf(left.size, right.size)
        val stereo = ShortArray(len * 2)
        for (i in 0 until len) {
            stereo[i * 2]     = left.getOrElse(i) { 0 }
            stereo[i * 2 + 1] = right.getOrElse(i) { 0 }
        }
        return stereo
    }
}
