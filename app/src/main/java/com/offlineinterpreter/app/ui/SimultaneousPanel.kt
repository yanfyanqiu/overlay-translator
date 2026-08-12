package com.offlineinterpreter.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.offlineinterpreter.app.mode.TranslationDirection
import com.offlineinterpreter.app.pipeline.TranslationPipeline

@Composable
fun SimultaneousPanel(
    pipeline: TranslationPipeline,
    isRunning: Boolean,
    sourceText: String,
    translatedText: String,
) {
    val direction by pipeline.direction.collectAsState()
    val status    by pipeline.status.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        // Direction
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.SwapHoriz, null, Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text(
                text   = if (direction == TranslationDirection.ZH_EN)
                    "🇨🇳 中文 → 英文 🇬🇧"
                else "🇬🇧 英文 → 中文 🇨🇳",
                style  = MaterialTheme.typography.bodyMedium,
            )
        }

        // Live transcription display
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Source
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                ),
                shape = RoundedCornerShape(12.dp),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("原文", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text   = sourceText.ifEmpty { "等待识别..." },
                        style  = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Start,
                    )
                }
            }

            // Translated
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                ),
                shape = RoundedCornerShape(12.dp),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("译文", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text   = translatedText.ifEmpty { "等待翻译..." },
                        style  = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Medium,
                        ),
                        textAlign = TextAlign.Start,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        // Start/Stop button
        Box(
            modifier = Modifier
                .size(140.dp)
                .clip(CircleShape)
                .background(
                    if (isRunning) Color(0xFFE53935)
                    else MaterialTheme.colorScheme.primary
                ),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(
                    onClick = {
                        if (isRunning) pipeline.stopSimultaneous()
                        else pipeline.startSimultaneous()
                    },
                    modifier = Modifier.size(64.dp),
                ) {
                    Icon(
                        if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                        null,
                        modifier = Modifier.size(48.dp),
                        tint = Color.White,
                    )
                }
                Text(
                    text   = if (isRunning) "停止" else "开始",
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
