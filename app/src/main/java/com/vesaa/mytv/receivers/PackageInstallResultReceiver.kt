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
import java.io.File

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

        val app = context.applicationContext
        if (status == PackageInstaller.STATUS_SUCCESS) {
            ApkInstaller.clearSessionInstallApkPath()
            return
        }

        if (legacy == LEGACY_INSTALL_FAILED_ALREADY_EXISTS ||
            status == LEGACY_INSTALL_FAILED_ALREADY_EXISTS
        ) {
            val path = ApkInstaller.takeSessionInstallApkPath()
            val file = path?.let { File(it) }?.takeIf { it.isFile }
            if (file != null) {
                Handler(Looper.getMainLooper()).post {
                    try {
                        LeanbackToastState.I.showToast(
                            "部分设备对应用内安装返回异常，已改为系统安装界面，请按提示完成更新",
                            LeanbackToastProperty.Duration.Custom(8_000),
                            id = "packageInstallFallback",
                        )
                    } catch (_: Throwable) {
                        Toast.makeText(app, "已打开系统安装界面", Toast.LENGTH_LONG).show()
                    }
                    ApkInstaller.installWithIntentView(app, file)
                }
                return
            }
        }

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
            return "安装未完成：系统返回「应用已存在」(部分电视/定制 ROM 对应用内安装误报)。" +
                "请删除缓存中的更新包后重新下载，或用文件管理器打开缓存目录下的 APK 安装；仍失败可重启设备后再试。"
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
