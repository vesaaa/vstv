package com.vesaa.mytv.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.vesaa.mytv.ui.screens.leanback.toast.LeanbackToastProperty
import com.vesaa.mytv.ui.screens.leanback.toast.LeanbackToastState
import com.vesaa.mytv.utils.ApkInstaller

// 部分 PackageInstaller 常量在 compileSdk 应用存根中不可见，与 AOSP 一致
private const val EXTRA_STATUS = "android.content.pm.extra.STATUS"
private const val EXTRA_STATUS_MESSAGE = "android.content.pm.extra.STATUS_MESSAGE"
private const val EXTRA_LEGACY_STATUS = "android.content.pm.extra.LEGACY_STATUS"

// PackageManager.INSTALL_FAILED_* 同理，数值与 AOSP 一致
private const val LEGACY_INSTALL_FAILED_UPDATE_INCOMPATIBLE = -7
private const val LEGACY_INSTALL_FAILED_SHARED_USER_INCOMPATIBLE = -8
private const val LEGACY_INSTALL_FAILED_ALREADY_EXISTS = -1

private val installConflictHint = buildString {
    append("无法覆盖安装：新安装包与当前已装应用的签名不一致。")
    append("常见于本机为 debug/旧签名包，而更新包来自 GitHub release 等不同签名。")
    append("请先卸载本应用，再安装新版本；卸载会清除本应用内数据与设置。")
}

/**
 * 接收 [PackageInstaller.Session.commit] 结果，将「签名冲突」等系统错误转成可读说明。
 */
class PackageInstallResultReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ApkInstaller.ACTION_INSTALL_RESULT) return

        val status = intent.getIntExtra(EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val legacy = intent.getIntExtra(EXTRA_LEGACY_STATUS, 0)
        val systemMsg = intent.getStringExtra(EXTRA_STATUS_MESSAGE)

        if (status == PackageInstaller.STATUS_SUCCESS) return

        val app = context.applicationContext
        val message = buildFailureMessage(status, legacy, systemMsg)

        Handler(Looper.getMainLooper()).post {
            try {
                LeanbackToastState.I.showToast(
                    message,
                    LeanbackToastProperty.Duration.Custom(14_000),
                    id = "packageInstallResult",
                )
            } catch (_: Throwable) {
                Toast.makeText(app, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun buildFailureMessage(status: Int, legacy: Int, systemMsg: String?): String {
        if (legacy == LEGACY_INSTALL_FAILED_ALREADY_EXISTS ||
            status == LEGACY_INSTALL_FAILED_ALREADY_EXISTS
        ) {
            return "安装被系统判定为「已存在相同应用」，常见于会话安装与系统安装器状态不一致。" +
                "请删除本应用缓存中的更新包后重新下载，或使用 U 盘/文件管理器覆盖安装同一 APK。"
        }
        if (legacy == LEGACY_INSTALL_FAILED_UPDATE_INCOMPATIBLE ||
            legacy == LEGACY_INSTALL_FAILED_SHARED_USER_INCOMPATIBLE
        ) {
            return installConflictHint
        }
        when (status) {
            PackageInstaller.STATUS_FAILURE_CONFLICT,
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE,
            -> return installConflictHint
            PackageInstaller.STATUS_FAILURE_BLOCKED ->
                return "安装被系统拦截，请在设置中允许本应用安装未知应用，或关闭纯净模式/应用锁"
            PackageInstaller.STATUS_FAILURE_STORAGE ->
                return "存储空间不足，无法完成安装"
            PackageInstaller.STATUS_FAILURE_INVALID ->
                return "安装包无效或已损坏，请删除缓存后重新下载更新"
            PackageInstaller.STATUS_FAILURE_ABORTED ->
                return "安装已取消"
            PackageInstaller.STATUS_FAILURE_TIMEOUT ->
                return "安装超时，请重试"
            else -> Unit
        }
        val extra = systemMsg?.trim()?.takeIf { it.isNotBlank() }
        return extra ?: "安装失败（状态码 $status）"
    }
}
