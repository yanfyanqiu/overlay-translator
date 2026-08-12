package com.offlineinterpreter.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.offlineinterpreter.app.audio.AudioPlayer
import com.offlineinterpreter.app.audio.DeviceRouter
import com.offlineinterpreter.app.asr.AsrManager
import com.offlineinterpreter.app.llm.ModelManager
import com.offlineinterpreter.app.mode.Mode
import com.offlineinterpreter.app.mode.TranslationDirection
import com.offlineinterpreter.app.pipeline.TranslationPipeline
import com.offlineinterpreter.app.tts.TtsManager
import com.offlineinterpreter.app.ui.*
import com.offlineinterpreter.lib.InferenceEngine
import kotlinx.coroutines.*

class MainActivity : ComponentActivity() {

    private lateinit var pipeline: TranslationPipeline
    private lateinit var modelManager: ModelManager
    private lateinit var asrManager: AsrManager
    private lateinit var ttsManager: TtsManager
    private lateinit var audioPlayer: AudioPlayer
    private lateinit var deviceRouter: DeviceRouter

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.values.all { it }
        if (!allGranted) {
            Toast.makeText(this, "需要麦克风权限才能使用语音功能", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        modelManager  = ModelManager(this)
        asrManager    = AsrManager(this)
        ttsManager    = TtsManager(this)
        audioPlayer   = AudioPlayer()
        deviceRouter  = DeviceRouter(this)
        pipeline      = TranslationPipeline(
            inferenceEngine = InferenceEngine,
            asrManager      = asrManager,
            ttsManager      = ttsManager,
            audioPlayer     = audioPlayer,
        )

        checkAndRequestPermissions()
        observeBluetooth()

        setContent {
            OfflineInterpreterApp(
                pipeline       = pipeline,
                modelManager   = modelManager,
                asrManager     = asrManager,
                ttsManager     = ttsManager,
                audioPlayer    = audioPlayer,
                deviceRouter   = deviceRouter,
                scope          = scope,
            )
        }
    }

    private fun checkAndRequestPermissions() {
        val perms = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            perms.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        val notGranted = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (notGranted.isNotEmpty()) {
            permissionLauncher.launch(notGranted.toTypedArray())
        }
    }

    private fun observeBluetooth() {
        deviceRouter.startMonitoring { connected ->
            if (connected) {
                Toast.makeText(this, "检测到蓝牙耳机已连接", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        asrManager.release()
        ttsManager.release()
        audioPlayer.release()
        deviceRouter.stopMonitoring()
    }
}
