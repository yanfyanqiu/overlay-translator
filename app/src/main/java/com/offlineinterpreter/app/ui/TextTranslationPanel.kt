package com.offlineinterpreter.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.offlineinterpreter.app.llm.ModelManager
import com.offlineinterpreter.app.mode.TranslationDirection
import com.offlineinterpreter.app.pipeline.TranslationPipeline
import kotlinx.coroutines.launch

@Composable
fun TextTranslationPanel(
    pipeline: TranslationPipeline,
    scope: kotlinx.coroutines.CoroutineScope,
    modelManager: ModelManager,
) {
    var inputText    by remember { mutableStateOf("") }
    val translated   by pipeline.translatedText.collectAsState()
    val direction    by pipeline.direction.collectAsState()
    val status       by pipeline.status.collectAsState()
    val loading      by remember { mutableStateOf(false) }

    val clipboard    = LocalClipboardManager.current
    val scroll       = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Input
        OutlinedTextField(
            value       = inputText,
            onValueChange = { inputText = it },
            label       = { Text("输入文本") },
            placeholder = { Text("在此输入或粘贴要翻译的文字") },
            modifier    = Modifier.fillMaxWidth().heightIn(min = 120.dp),
            maxLines    = 8,
            trailingIcon = {
                if (inputText.isNotEmpty()) {
                    IconButton(onClick = { inputText = "" }) {
                        Icon(Icons.Default.Clear, "清空")
                    }
                }
            },
        )

        // Translate button
        Button(
            onClick = {
                if (inputText.isBlank()) return@Button
                loading = true
                scope.launch {
                    pipeline.translateText(inputText, direction)
                    loading = false
                }
            },
            enabled = inputText.isNotBlank() && modelManager.isModelLoaded && !loading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(Modifier.width(8.dp))
            }
            Icon(Icons.Default.Translate, null)
            Spacer(Modifier.width(8.dp))
            Text("翻译")
        }

        // Translated output
        if (translated.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                ),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text   = "译文",
                            style  = MaterialTheme.typography.labelMedium,
                            color  = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        Row {
                            // Copy
                            IconButton(
                                onClick = {
                                    clipboard.setText(AnnotatedString(translated))
                                },
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(Icons.Default.ContentCopy, "复制",
                                    Modifier.size(18.dp))
                            }
                            // Speak
                            IconButton(
                                onClick = { pipeline.speakTranslated(direction.tgtCode) },
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(Icons.Default.VolumeUp, "朗读",
                                    Modifier.size(18.dp))
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text   = translated,
                        style  = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}
