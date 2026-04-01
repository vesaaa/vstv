package com.vesaa.mytv.ui.screens.leanback.update

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.vesaa.mytv.AppGlobal
import com.vesaa.mytv.ui.screens.leanback.toast.LeanbackToastState
import com.vesaa.mytv.ui.screens.leanback.update.components.LeanbackUpdateDialog
import com.vesaa.mytv.utils.ApkInstaller
import java.io.File

@Composable
fun LeanbackUpdateScreen(
    modifier: Modifier = Modifier,
    updateViewModel: LeanBackUpdateViewModel = viewModel(),
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val coroutineScope = rememberCoroutineScope()
    val latestFile = remember { File(AppGlobal.cacheDir, "latest.apk") }

    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (appContext.packageManager.canRequestPackageInstalls()) {
                    ApkInstaller.installApk(appContext, latestFile.path)
                } else {
                    LeanbackToastState.I.showToast("未授予安装权限")
                }
            }
        }

    LaunchedEffect(Unit) {
        updateViewModel.pendingInstallApkPath.collect { path ->
            val file = File(path)
            if (!file.exists()) {
                LeanbackToastState.I.showToast("安装包不存在，请重新下载")
                return@collect
            }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                ApkInstaller.installApk(appContext, path)
            } else {
                if (appContext.packageManager.canRequestPackageInstalls()) {
                    ApkInstaller.installApk(appContext, path)
                } else {
                    val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${appContext.packageName}")
                    }
                    launcher.launch(intent)
                }
            }
        }
    }

    LeanbackUpdateDialog(
        modifier = modifier,
        showDialogProvider = { updateViewModel.showDialog },
        onDismissRequest = { updateViewModel.showDialog = false },
        releaseProvider = { updateViewModel.latestRelease },
        onUpdateAndInstall = {
            updateViewModel.showDialog = false
            coroutineScope.launch(Dispatchers.IO) {
                updateViewModel.downloadAndUpdate(latestFile)
            }
        },
    )
}
