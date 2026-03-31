package top.yogiczy.mytv.ui.screens.leanback.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import top.yogiczy.mytv.ui.rememberLeanbackChildPadding
import top.yogiczy.mytv.ui.screens.leanback.settings.components.LeanbackSettingsCategoryDetail
import top.yogiczy.mytv.ui.screens.leanback.settings.components.LeanbackSettingsCategoryList
import top.yogiczy.mytv.ui.theme.LeanbackTheme

@Composable
fun LeanbackSettingsScreen(
    modifier: Modifier = Modifier,
    /** 首次进入设置时自动打开的详情分类（如引导进入直播源/节目单） */
    initialOpenCategory: LeanbackSettingsCategories? = null,
    onRequestClose: () -> Unit = {},
) {
    val childPadding = rememberLeanbackChildPadding()
    var openCategory by remember { mutableStateOf<LeanbackSettingsCategories?>(null) }
    var appliedInitialCategory by remember { mutableStateOf(false) }

    LaunchedEffect(initialOpenCategory) {
        if (!appliedInitialCategory && initialOpenCategory != null) {
            openCategory = initialOpenCategory
            appliedInitialCategory = true
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(
                top = childPadding.top + 20.dp,
                bottom = childPadding.bottom,
                start = childPadding.start,
                end = childPadding.end,
            )
            .pointerInput(Unit) { detectTapGestures(onTap = { }) },
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                text = "设置",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "选择一项打开详情；「返回直播」关闭设置。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LeanbackSettingsCategoryList(
                modifier = Modifier.fillMaxWidth(),
                onReturnLive = onRequestClose,
                onCategoryOpen = { openCategory = it },
            )
        }
    }

    openCategory?.let { category ->
        Dialog(
            onDismissRequest = { openCategory = null },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
            ),
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .fillMaxHeight(0.88f),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = category.title,
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { openCategory = null }) {
                            Text("返回")
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        LeanbackSettingsCategoryDetail(
                            category = category,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }
}

@Preview(device = "id:Android TV (720p)")
@Composable
private fun LeanbackSettingsScreenPreview() {
    LeanbackTheme {
        LeanbackSettingsScreen()
    }
}
