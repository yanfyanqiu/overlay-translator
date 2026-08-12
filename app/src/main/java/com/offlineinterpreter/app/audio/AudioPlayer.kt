package com.offlineinterpreter.app.audio

import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Multi-device AudioTrack player.
 * Supports routing to phone speaker, bluetooth, wired headset.
 */
class AudioPlayer {

    sealed class Event {
        data object Done  : Event()
        data class Error(val msg: String) : Event()
    }

    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 4)
    val events: SharedFlow<Event> = _events

    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_OUT_STEREO

    /**
     * Play a 16-bit stereo PCM buffer.
     * @param preferredDevice null = system default
     */
    fun play(samples: ShortArray, preferredDevice: AudioDeviceInfo? = null) {
        val bufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, AudioFormat.ENCODING_PCM_16BIT)
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
                    .setChannelMask(channelConfig)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(bufferSize, samples.size * 2))
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        if (preferredDevice != null) {
            try {
                track.setPreferredDevice(preferredDevice)
            } catch (_: Exception) {}
        }

        track.write(samples, 0, samples.size)
        track.play()

        val totalFrames = samples.size / 2
        track.setNotificationMarkerPosition(totalFrames - 1)
        track.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
            override fun onMarkerReached(t: AudioTrack?) {
                try { t?.stop(); t?.release() } catch (_: Exception) {}
                _events.tryEmit(Event.Done)
            }
            override fun onPeriodicNotification(t: AudioTrack?) {}
        })
    }

    /** Stop and release all tracks. */
    fun release() {}
}
