package com.offlineinterpreter.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.offlineinterpreter.app.audio.DeviceRouter
import com.offlineinterpreter.app.mode.Mode
import com.offlineinterpreter.app.mode.TranslationDirection
import com.offlineinterpreter.app.pipeline.TranslationPipeline

@Composable
fun BluetoothPanel(
    pipeline: TranslationPipeline,
    isRunning: Boolean,
    btConnected: Boolean,
    mode: Mode,
    deviceRouter: DeviceRouter,
) {
    val direction  by pipeline.direction.collectAsState()
    val status     by pipeline.status.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly,
    ) {
        Icon(
            if (btConnected) Icons.Default.Headphones
            else Icons.Default.BluetoothDisabled,
            null,
            modifier = Modifier.size(64.dp),
            tint = if (btConnected) MaterialTheme.colorScheme.primary
                   else MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (!btConnected) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                ),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Warning, null,
                        tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text   = "未检测到蓝牙耳机，请先连接蓝牙设备",
                        style  = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        // Mode B: Stereo preview
        if (mode == Mode.BT_STEREO && btConnected) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("蓝牙双声道模式", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text   = "左声道：${if (direction == TranslationDirection.ZH_EN) "中文译文" else "英文译文"}",
                        style  = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text   = "右声道：${if (direction == TranslationDirection.ZH_EN) "英文译文" else "中文译文"}",
                        style  = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text   = "切换方向会交换左右声道",
                        style  = MaterialTheme.typography.bodySmall,
                        color  = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Mode C: Speaker split preview
        if (mode == Mode.BT_SPEAKER_SPLIT && btConnected) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("耳机+扬声器分离模式", style = MaterialTheme.typography.titleMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Icon(Icons.Default.Headphones, null, Modifier.size(20.dp))
                            Text(
                                text   = if (direction == TranslationDirection.ZH_EN) "耳机：中文" else "耳机：英文",
                                style  = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        Column {
                            Icon(Icons.Default.Speaker, null, Modifier.size(20.dp))
                            Text(
                                text   = if (direction == TranslationDirection.ZH_EN) "扬声器：英文" else "扬声器：中文",
                                style  = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                    Text(
                        text   = "注意：外放可能影响麦克风收音效果",
                        style  = MaterialTheme.typography.bodySmall,
                        color  = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        // Status
        Text(
            text   = status,
            style  = MaterialTheme.typography.bodySmall,
            color  = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
