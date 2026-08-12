package com.offlineinterpreter.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.offlineinterpreter.app.mode.TranslationDirection
import com.offlineinterpreter.app.pipeline.TranslationPipeline

@Composable
fun PttPanel(
    pipeline: TranslationPipeline,
    isRunning: Boolean,
    partialText: String,
) {
    val status    by pipeline.status.collectAsState()
    val direction by pipeline.direction.collectAsState()
    val srcText   by pipeline.sourceText.collectAsState()
    val tgtText   by pipeline.translatedText.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly,
    ) {
        // Direction hint
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (direction == TranslationDirection.ZH_EN)
                    Icons.Default.RecordVoiceOver else Icons.Default.SpeakerNotes,
                null, Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (direction == TranslationDirection.ZH_EN)
                    "识别语言：中文 → 译为：英文"
                else "识别语言：英文 → 译为：中文",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Recognition display
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 80.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (isRunning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text   = partialText.ifEmpty { "正在聆听..." },
                        style  = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                    )
                } else if (srcText.isNotEmpty()) {
                    Text("原文：", style = MaterialTheme.typography.labelSmall)
                    Text(srcText, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    Text("译文：", style = MaterialTheme.typography.labelSmall)
                    Text(
                        tgtText,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Text(
                        text   = "按住按钮开始说话",
                        style  = MaterialTheme.typography.bodyLarge,
                        color  = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // PTT Button
        Box(
            modifier = Modifier
                .size(160.dp)
                .clip(CircleShape)
                .background(
                    if (isRunning) Color(0xFFE53935)
                    else MaterialTheme.colorScheme.primary
                )
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            pipeline.startPtt()
                            tryAwaitRelease()
                            pipeline.stopPtt()
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    if (isRunning) Icons.Default.Stop else Icons.Default.Mic,
                    null,
                    modifier = Modifier.size(56.dp),
                    tint = Color.White,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text   = if (isRunning) "松开结束" else "按住说话",
                    color  = Color.White,
                    style  = MaterialTheme.typography.bodyMedium,
                )
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
