package com.vesaa.mytv.ui.utils

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 会话安装异常时，通知前台 UI 使用 Activity context 回退拉起系统安装器。
 */
object InstallFallbackNotifier {
    private val _apkPathUpdates = MutableSharedFlow<String>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val apkPathUpdates: SharedFlow<String> = _apkPathUpdates.asSharedFlow()

    fun notifyFallbackInstall(apkAbsolutePath: String) {
        _apkPathUpdates.tryEmit(apkAbsolutePath)
    }
}
