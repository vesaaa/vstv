package com.vesaa.mytv.ui.screens.leanback.settings.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

data class StreamingSourceInput(
    val iptvUrl: String,
    val iptvSubscribeUa: String,
    val iptvChannelUa: String,
    val epgUrl: String,
    val epgUa: String,
)

@Composable
fun LeanbackTouchSourceInputDialog(
    showDialogProvider: () -> Boolean = { false },
    onDismissRequest: () -> Unit = {},
    initialInputProvider: () -> StreamingSourceInput = { StreamingSourceInput("", "", "", "", "") },
    onConfirm: (StreamingSourceInput) -> Unit = {},
) {
    if (!showDialogProvider()) return

    val initial = initialInputProvider()
    var iptvUrl by remember(initial.iptvUrl) { mutableStateOf(initial.iptvUrl) }
    var iptvSubscribeUa by remember(initial.iptvSubscribeUa) { mutableStateOf(initial.iptvSubscribeUa) }
    var iptvChannelUa by remember(initial.iptvChannelUa) { mutableStateOf(initial.iptvChannelUa) }
    var epgUrl by remember(initial.epgUrl) { mutableStateOf(initial.epgUrl) }
    var epgUa by remember(initial.epgUa) { mutableStateOf(initial.epgUa) }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .widthIn(max = 900.dp),
            shape = RoundedCornerShape(14.dp),
            tonalElevation = 6.dp,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("添加直播源", style = MaterialTheme.typography.titleMedium)
                SourceInputField("直播源地址", iptvUrl) { iptvUrl = it }
                SourceInputField("拉取订阅UA", iptvSubscribeUa) { iptvSubscribeUa = it }
                SourceInputField("播放频道UA", iptvChannelUa) { iptvChannelUa = it }
                SourceInputField("节目单地址", epgUrl) { epgUrl = it }
                SourceInputField("节目单UA", epgUa) { epgUa = it }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismissRequest) { Text("取消") }
                    TextButton(
                        onClick = {
                            onConfirm(
                                StreamingSourceInput(
                                    iptvUrl = iptvUrl.trim(),
                                    iptvSubscribeUa = iptvSubscribeUa.trim(),
                                    iptvChannelUa = iptvChannelUa.trim(),
                                    epgUrl = epgUrl.trim(),
                                    epgUa = epgUa.trim(),
                                ),
                            )
                        }
                    ) { Text("保存") }
                }
            }
        }
    }
}

@Composable
private fun SourceInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(text = label, style = MaterialTheme.typography.labelMedium)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(10.dp),
                )
                .padding(horizontal = 10.dp, vertical = 9.dp),
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurface,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            singleLine = true,
            decorationBox = { innerTextField ->
                if (value.isBlank()) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                    )
                }
                innerTextField()
            },
        )
    }
}
