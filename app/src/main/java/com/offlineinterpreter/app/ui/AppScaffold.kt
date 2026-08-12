package com.offlineinterpreter.app.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.offlineinterpreter.app.asr.AsrManager
import com.offlineinterpreter.app.audio.AudioPlayer
import com.offlineinterpreter.app.audio.DeviceRouter
import com.offlineinterpreter.app.llm.ModelManager
import com.offlineinterpreter.app.mode.Mode
import com.offlineinterpreter.app.mode.TranslationDirection
import com.offlineinterpreter.app.pipeline.TranslationPipeline
import com.offlineinterpreter.app.tts.TtsManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineInterpreterApp(
    pipeline: TranslationPipeline,
    modelManager: ModelManager,
    asrManager: AsrManager,
    ttsManager: TtsManager,
    audioPlayer: AudioPlayer,
    deviceRouter: DeviceRouter,
    scope: CoroutineScope,
) {
    val currentMode    by pipeline.currentMode.collectAsState()
    val direction      by pipeline.direction.collectAsState()
    val status         by pipeline.status.collectAsState()
    val sourceText     by pipeline.sourceText.collectAsState()
    val translatedText by pipeline.translatedText.collectAsState()
    val partialText    by pipeline.partialText.collectAsState()
    val isRunning      by pipeline.isRunning.collectAsState()
    val btConnected    by pipeline.btConnected.collectAsState()

    var showModeSheet  by remember { mutableStateOf(false) }
    val context        = LocalContext.current
    val activity       = context as? Activity

    // Model loading state
    var modelLoading   by remember { mutableStateOf(false) }
    var modelError     by remember { mutableStateOf<String?>(null) }

    // SAF picker for GGUF
    val ggufPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            modelLoading = true
            modelError   = null
            val path = modelManager.importModel(uri)
            if (path != null) {
                modelManager.loadModel().onFailure {
                    modelError = it.message
                }
            } else {
                modelError = "导入模型失败"
            }
            modelLoading = false
        }
    }

    // Set pipeline scope
    LaunchedEffect(scope) {
        pipeline.setScope(scope)
    }

    // Observe ASR
    LaunchedEffect(Unit) {
        asrManager.events.collectLatest { event ->
            when (event) {
                is AsrManager.Result.Partial -> pipeline.partialText.let {}
                is AsrManager.Result.Final   -> {}
                is AsrManager.Result.Error   -> {}
            }
        }
    }

    OfflineInterpreterTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("离线同声传译", fontWeight = FontWeight.Bold) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = Color.White,
                    ),
                    actions = {
                        // Model status indicator
                        Box(
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(
                                    if (modelManager.isModelLoaded) Color(0xFF4CAF50)
                                    else Color(0xFFFF5252)
                                )
                        )
                        IconButton(onClick = { ggufPicker.launch(arrayOf("*/*")) }) {
                            Icon(Icons.Default.FolderOpen, "选择模型", tint = Color.White)
                        }
                    },
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // ── Direction toggle ───────────────────────────────────────────
                DirectionChip(
                    direction = direction,
                    onToggle  = { pipeline.toggleDirection() },
                )

                Spacer(Modifier.height(12.dp))

                // ── Mode selector ─────────────────────────────────────────────
                OutlinedButton(
                    onClick = { showModeSheet = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Dashboard, null)
                    Spacer(Modifier.width(8.dp))
                    Text(ModeDesc.of(currentMode))
                }

                Spacer(Modifier.height(12.dp))

                // ── Status ────────────────────────────────────────────────────
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors  = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (isRunning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            text   = status,
                            style  = MaterialTheme.typography.bodyMedium,
                            color  = if (isRunning) MaterialTheme.colorScheme.primary
                                     else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // ── Main content area (mode-specific) ─────────────────────────
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when (currentMode) {
                        Mode.TEXT_TRANSLATION -> TextTranslationPanel(
                            pipeline      = pipeline,
                            scope         = scope,
                            modelManager  = modelManager,
                        )
                        Mode.PUSH_TO_TALK -> PttPanel(
                            pipeline      = pipeline,
                            isRunning     = isRunning,
                            partialText   = partialText,
                        )
                        Mode.SIMULTANEOUS -> SimultaneousPanel(
                            pipeline      = pipeline,
                            isRunning     = isRunning,
                            sourceText    = sourceText,
                            translatedText= translatedText,
                        )
                        Mode.BT_STEREO, Mode.BT_SPEAKER_SPLIT -> {
                            BluetoothPanel(
                                pipeline      = pipeline,
                                isRunning     = isRunning,
                                btConnected   = btConnected,
                                mode          = currentMode,
                                deviceRouter  = deviceRouter,
                            )
                        }
                    }
                }

                // ── Model status / error ──────────────────────────────────────
                if (modelError != null) {
                    Text(
                        text   = "⚠ ${modelError}",
                        color  = Color.Red,
                        fontSize= 12.sp,
                    )
                    Spacer(Modifier.height(4.dp))
                }
                if (modelLoading) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(6.dp))
                        Text("加载模型中...", fontSize = 12.sp)
                    }
                } else if (modelManager.isModelLoaded) {
                    Text(
                        text   = "✅ 模型已就绪",
                        fontSize= 12.sp,
                        color  = Color(0xFF388E3C),
                    )
                } else {
                    Text(
                        text   = "⚠ 请先选择本地 GGUF 模型文件",
                        fontSize= 12.sp,
                        color  = Color(0xFFFF6F00),
                    )
                }
            }
        }

        // Mode selection bottom sheet
        if (showModeSheet) {
            ModalBottomSheet(onDismissRequest = { showModeSheet = false }) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                ) {
                    Text(
                        text   = "选择模式",
                        style  = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(bottom = 16.dp),
                    )
                    Mode.entries.forEach { mode ->
                        ListItem(
                            headlineContent = { Text(ModeDesc.of(mode)) },
                            supportingContent = { Text(ModeDesc.detail(mode)) },
                            leadingContent = {
                                Icon(
                                    ModeDesc.icon(mode),
                                    null,
                                    tint = if (mode == currentMode)
                                        MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            trailingContent = {
                                if (mode == currentMode) {
                                    Icon(Icons.Default.Check, null,
                                        tint = MaterialTheme.colorScheme.primary)
                                }
                            },
                            modifier = Modifier.clickable {
                                pipeline.setMode(mode)
                                showModeSheet = false
                            },
                        )
                    }
                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
fun DirectionChip(direction: TranslationDirection, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text   = if (direction == TranslationDirection.ZH_EN) "🇨🇳 中文 → 英文 🇬🇧"
                     else "🇬🇧 英文 → 中文 🇨🇳",
            style  = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.width(8.dp))
        Icon(Icons.Default.SwapHoriz, "切换", Modifier.size(20.dp))
    }
}

object ModeDesc {
    fun of(m: Mode) = when (m) {
        Mode.TEXT_TRANSLATION   -> "文本翻译"
        Mode.PUSH_TO_TALK       -> "按住说话"
        Mode.SIMULTANEOUS       -> "同声传译"
        Mode.BT_STEREO          -> "蓝牙双声道"
        Mode.BT_SPEAKER_SPLIT   -> "耳机+扬声器分离"
    }
    fun detail(m: Mode) = when (m) {
        Mode.TEXT_TRANSLATION   -> "输入文本，一键翻译并朗读"
        Mode.PUSH_TO_TALK       -> "按住说话，松开自动翻译并播放"
        Mode.SIMULTANEOUS       -> "持续收音，自动翻译，手机扬声器播译文"
        Mode.BT_STEREO          -> "蓝牙耳机左右声道各播一种语言译文"
        Mode.BT_SPEAKER_SPLIT   -> "蓝牙耳机播一种语言，扬声器播另一种"
    }
    fun icon(m: Mode) = when (m) {
        Mode.TEXT_TRANSLATION   -> Icons.Default.Translate
        Mode.PUSH_TO_TALK       -> Icons.Default.Mic
        Mode.SIMULTANEOUS       -> Icons.Default.Campaign
        Mode.BT_STEREO          -> Icons.Default.Headphones
        Mode.BT_SPEAKER_SPLIT   -> Icons.Default.Speaker
    }
}
