package com.vesaa.mytv.utils

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
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
    /**
     * @param context 尽量传入当前 [android.app.Activity]，部分电视（如海信 Vidda）对仅 Application Context
     * 触发的安装界面支持不完整；从 Compose 侧请传 [LocalContext.current]。
     */
    fun installApk(context: Context, filePath: String) {
        val file = File(filePath)
        if (!file.exists()) {
            Log.w(TAG, "APK 不存在: $filePath")
            toastOnMain(context, "安装包不存在，请重新下载")
            return
        }
        val appCtx = context.applicationContext
        val launchContext = context
        installExecutor.execute {
            if (preferSystemInstallerUiFirst(appCtx)) {
                try {
                    val uri = prepareUriForInstall(appCtx, file)
                    Handler(Looper.getMainLooper()).post {
                        val ok = tryLaunchSystemPackageInstallers(launchContext, uri)
                        if (!ok) {
                            Log.w(TAG, "电视端未能解析系统安装界面，回退 PackageInstaller 会话")
                            installExecutor.execute {
                                try {
                                    installWithPackageInstallerSession(appCtx, file)
                                } catch (e: Exception) {
                                    Log.e(TAG, "会话安装仍失败", e)
                                    toastOnMain(appCtx, "安装失败：${e.message}")
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "准备系统安装失败，尝试 PackageInstaller 会话", e)
                    try {
                        installWithPackageInstallerSession(appCtx, file)
                    } catch (e2: Exception) {
                        Log.e(TAG, "PackageInstaller 安装失败，再试系统安装界面", e2)
                        try {
                            installWithIntentView(launchContext, file)
                        } catch (e3: Exception) {
                            Log.e(TAG, "系统安装界面仍失败", e3)
                            toastOnMain(appCtx, "安装失败：${e3.message}")
                        }
                    }
                }
                return@execute
            }
            try {
                installWithPackageInstallerSession(appCtx, file)
            } catch (e: Exception) {
                Log.e(TAG, "PackageInstaller 安装失败，回退系统安装界面", e)
                try {
                    installWithIntentView(launchContext, file)
                } catch (e2: Exception) {
                    Log.e(TAG, "回退安装也失败", e2)
                    toastOnMain(appCtx, "安装失败：${e2.message}")
                }
            }
        }
    }
    /**
     * 海信 Vidda 等电视对 [PackageInstaller.Session] 常出现「弹出确认后无进度、再次更新无反应」；
     * 优先走系统「打开 APK」安装流程更稳定。
     */
    private fun preferSystemInstallerUiFirst(context: Context): Boolean {
        val pm = context.packageManager
        return pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
            pm.hasSystemFeature(PackageManager.FEATURE_TELEVISION)
    }
    private fun prepareUriForInstall(appCtx: Context, sourceFile: File): Uri {
        val apk = prepareApkFileForSharing(appCtx, sourceFile)
        return apkContentUri(appCtx, apk)
    }
    /**
     * 已在应用缓存目录下的 APK 直接共用；否则复制到 cache 再交给 FileProvider（避免外部分区无法暴露）。
     */
    private fun prepareApkFileForSharing(appCtx: Context, sourceFile: File): File {
        if (!sourceFile.isFile) {
            throw IllegalStateException("安装包不是有效文件")
        }
        return try {
            val cacheRoot = appCtx.cacheDir.canonicalFile
            val src = sourceFile.canonicalFile
            if (src.path.startsWith(cacheRoot.path)) {
                src
            } else {
                copyApkIntoCache(appCtx, sourceFile)
            }
        } catch (_: Exception) {
            copyApkIntoCache(appCtx, sourceFile)
        }
    }
    @SuppressLint("SetWorldReadable")
    private fun copyApkIntoCache(appCtx: Context, sourceFile: File): File {
        val dest = File(appCtx.cacheDir, sourceFile.name)
        sourceFile.inputStream().use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            dest.setReadable(true, false)
        }
        return dest
    }
    private fun apkContentUri(appCtx: Context, apkFile: File): Uri =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            FileProvider.getUriForFile(
                appCtx,
                appCtx.packageName + ".FileProvider",
                apkFile,
            )
        } else {
            @Suppress("DEPRECATION")
            Uri.fromFile(apkFile)
        }
    /**
     * @return 是否已成功调用 [Context.startActivity]（部分 ROM 仍可能不展示界面，此处无法检测）
     */
    private fun tryLaunchSystemPackageInstallers(launchContext: Context, uri: Uri): Boolean {
        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            clipData = ClipData.newRawUri("", uri)
        }
        try {
            launchContext.startActivity(viewIntent)
            return true
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "ACTION_VIEW 无安装器处理", e)
        } catch (e: Exception) {
            Log.w(TAG, "ACTION_VIEW 启动失败", e)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            @Suppress("DEPRECATION")
            val installPkg = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                data = uri
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                clipData = ClipData.newRawUri("", uri)
            }
            try {
                launchContext.startActivity(installPkg)
                return true
            } catch (e: ActivityNotFoundException) {
                Log.w(TAG, "ACTION_INSTALL_PACKAGE 无处理方", e)
            } catch (e: Exception) {
                Log.w(TAG, "ACTION_INSTALL_PACKAGE 启动失败", e)
            }
        }
        return false
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
    internal fun installWithIntentView(context: Context, sourceFile: File) {
        val appCtx = context.applicationContext
        val launchContext = context
        try {
            val uri = prepareUriForInstall(appCtx, sourceFile)
            Handler(Looper.getMainLooper()).post {
                if (!tryLaunchSystemPackageInstallers(launchContext, uri)) {
                    Log.e(TAG, "未找到可处理 APK 的安装界面")
                    Toast.makeText(
                        appCtx,
                        "无法打开安装界面，请在系统设置中允许本应用安装未知应用",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "准备系统安装失败", e)
            toastOnMain(appCtx, "安装失败：${e.message}")
        }
    }
    private fun toastOnMain(context: Context, message: String) {
        val app = context.applicationContext
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(app, message, Toast.LENGTH_LONG).show()
        }
    }
}
