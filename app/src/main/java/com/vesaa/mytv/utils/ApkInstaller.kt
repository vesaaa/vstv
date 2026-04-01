package com.vesaa.mytv.utils

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import com.vesaa.mytv.receivers.PackageInstallResultReceiver
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.Executors

object ApkInstaller {
    private const val TAG = "ApkInstaller"

    /** 与 [AndroidManifest] 中 [PackageInstallResultReceiver] 的 intent-filter 一致 */
    const val ACTION_INSTALL_RESULT = "com.vesaa.mytv.action.PACKAGE_INSTALL_RESULT"

    private val installExecutor = Executors.newSingleThreadExecutor()

    private val sessionApkPathLock = Any()
    private var lastSessionApkAbsolutePath: String? = null

    /** [PackageInstallResultReceiver] 在会话成功时调用，避免后续误触发回退安装 */
    fun clearSessionInstallApkPath() {
        synchronized(sessionApkPathLock) { lastSessionApkAbsolutePath = null }
    }

    /**
     * 取出并清空最近一次 [installWithPackageInstallerSession] 提交的 APK 路径。
     * 用于系统在覆盖安装时误报 INSTALL_FAILED_ALREADY_EXISTS 时改走系统安装界面。
     */
    fun takeSessionInstallApkPath(): String? {
        synchronized(sessionApkPathLock) {
            val p = lastSessionApkAbsolutePath
            lastSessionApkAbsolutePath = null
            return p
        }
    }

    fun installApk(context: Context, filePath: String) {
        val file = File(filePath)
        if (!file.exists()) {
            Log.w(TAG, "APK 不存在: $filePath")
            toastOnMain(context, "安装包不存在，请重新下载")
            return
        }
        val appCtx = context.applicationContext
        installExecutor.execute {
            try {
                installWithPackageInstallerSession(appCtx, file)
            } catch (e: Exception) {
                Log.e(TAG, "PackageInstaller 安装失败，回退系统安装界面", e)
                try {
                    installWithIntentView(appCtx, file)
                } catch (e2: Exception) {
                    Log.e(TAG, "回退安装也失败", e2)
                    toastOnMain(appCtx, "安装失败：${e2.message}")
                }
            }
        }
    }

    /**
     * 使用 [PackageInstaller.Session] 提交安装，失败时由 [PackageInstallResultReceiver] 解析原因。
     */
    private fun installWithPackageInstallerSession(appCtx: Context, apkFile: File) {
        val packageInstaller = appCtx.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setAppPackageName(appCtx.packageName)
            }
        }
        val sessionId = packageInstaller.createSession(params)
        val session = packageInstaller.openSession(sessionId)
        try {
            session.openWrite("base.apk", 0, -1).use { out ->
                FileInputStream(apkFile).use { input -> input.copyTo(out) }
                session.fsync(out)
            }
            synchronized(sessionApkPathLock) {
                lastSessionApkAbsolutePath = apkFile.absolutePath
            }
            // 必须显式指定组件：隐式广播在 Android 12+ 上可能无法投递到 exported=false 的 manifest 接收器，
            // 导致收不到结果或 extras 异常（用户侧表现为安装失败、状态码错乱）。
            val callback = Intent(appCtx, PackageInstallResultReceiver::class.java).apply {
                action = ACTION_INSTALL_RESULT
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or pendingIntentMutableFlag()
            val pendingIntent = PendingIntent.getBroadcast(
                appCtx,
                sessionId,
                callback,
                flags,
            )
            session.commit(pendingIntent.intentSender)
        } catch (e: Exception) {
            session.abandon()
            synchronized(sessionApkPathLock) { lastSessionApkAbsolutePath = null }
            throw e
        }
    }

    private fun pendingIntentMutableFlag(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE
        } else {
            0
        }

    /** 供 [PackageInstallResultReceiver] 在会话安装误报失败时调用（与 [installApk] 内回退逻辑一致） */
    @SuppressLint("SetWorldReadable")
    internal fun installWithIntentView(appCtx: Context, sourceFile: File) {
        val cacheDir = appCtx.cacheDir
        val cachedApkFile = File(cacheDir, sourceFile.name).apply {
            writeBytes(sourceFile.readBytes())
            setReadable(true, false)
        }

        val uri =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                FileProvider.getUriForFile(
                    appCtx,
                    appCtx.packageName + ".FileProvider",
                    cachedApkFile,
                )
            } else {
                Uri.fromFile(cachedApkFile)
            }

        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            setDataAndType(uri, "application/vnd.android.package-archive")
        }

        Handler(Looper.getMainLooper()).post {
            try {
                appCtx.startActivity(installIntent)
            } catch (e: ActivityNotFoundException) {
                Log.e(TAG, "未找到可处理 APK 的安装界面", e)
                Toast.makeText(
                    appCtx,
                    "无法打开安装界面，请在系统设置中允许本应用安装未知应用",
                    Toast.LENGTH_LONG,
                ).show()
            } catch (e: Exception) {
                Log.e(TAG, "启动安装失败", e)
                Toast.makeText(appCtx, "安装失败：${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun toastOnMain(context: Context, message: String) {
        val app = context.applicationContext
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(app, message, Toast.LENGTH_LONG).show()
        }
    }
}
