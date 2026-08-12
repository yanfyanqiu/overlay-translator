package com.offlineinterpreter.app.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build

/**
 * DeviceRouter — detects connected audio devices and routes playback.
 * Used for Mode B (bluetooth stereo) and Mode C (bluetooth + speaker split).
 */
class DeviceRouter(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var callback: ((Boolean) -> Unit)? = null

    @SuppressLint("MissingPermission")
    fun startMonitoring(onBluetoothChange: (Boolean) -> Unit) {
        callback = onBluetoothChange

        val listener = object : AudioManager.AudioDeviceCallback() {
            @SuppressLint("MissingPermission")
            override fun onDeviceAdded(device: AudioDeviceInfo?) {
                device ?: return
                if (device.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                    device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) {
                    onBluetoothChange(true)
                }
            }

            override fun onDeviceRemoved(device: AudioDeviceInfo?) {
                device ?: return
                if (device.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                    device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) {
                    onBluetoothChange(false)
                }
            }
        }

        audioManager.registerAudioDeviceCallback(listener, null)
        // Initial check
        onBluetoothChange(isBluetoothConnected())
    }

    fun stopMonitoring() {
        try {
            audioManager.unregisterAudioDeviceCallback(object : AudioManager.AudioDeviceCallback() {})
        } catch (_: Exception) {}
    }

    /** Returns true if any bluetooth audio device is connected. */
    @SuppressLint("MissingPermission")
    fun isBluetoothConnected(): Boolean {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        return devices.any {
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
        }
    }

    /** Get all available output devices. */
    @SuppressLint("MissingPermission")
    fun getOutputDevices(): List<AudioDeviceInfo> {
        return audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList()
    }

    /** Get the first connected bluetooth device (A2DP preferred). */
    @SuppressLint("MissingPermission")
    fun getBluetoothDevice(): AudioDeviceInfo? {
        return getOutputDevices().firstOrNull {
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
        } ?: getOutputDevices().firstOrNull {
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
        }
    }

    /** Get the phone speaker device. */
    @SuppressLint("MissingPermission")
    fun getSpeakerDevice(): AudioDeviceInfo? {
        return getOutputDevices().firstOrNull {
            it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
        }
    }
}
