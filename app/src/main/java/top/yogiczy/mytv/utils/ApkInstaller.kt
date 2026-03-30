package top.yogiczy.mytv.utils

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

object ApkInstaller {
    private const val TAG = "ApkInstaller"

    @SuppressLint("SetWorldReadable")
    fun installApk(context: Context, filePath: String) {
        val file = File(filePath)
        if (!file.exists()) {
            Log.w(TAG, "APK 不存在: $filePath")
            return
        }
        try {
            val cacheDir = context.cacheDir
            val cachedApkFile = File(cacheDir, file.name).apply {
                writeBytes(file.readBytes())
                // 解决 Android 6 无法解析安装包
                setReadable(true, false)
            }

            val uri =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    FileProvider.getUriForFile(
                        context,
                        context.packageName + ".FileProvider",
                        cachedApkFile,
                    )
                } else {
                    Uri.fromFile(cachedApkFile)
                }

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                // NEW_TASK：非 Activity 上下文或后台回调（如网页上传完成）启动安装页所必需
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                setDataAndType(uri, "application/vnd.android.package-archive")
            }

            val appCtx = context.applicationContext
            Handler(Looper.getMainLooper()).post {
                try {
                    appCtx.startActivity(installIntent)
                } catch (e: ActivityNotFoundException) {
                    Log.e(TAG, "未找到可处理 APK 的安装界面（请检查系统是否限制侧载或包可见性）", e)
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
        } catch (e: Exception) {
            Log.e(TAG, "准备安装包失败", e)
            toastOnMain(context, "安装失败：${e.message}")
        }
    }

    private fun toastOnMain(context: Context, message: String) {
        val app = context.applicationContext
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(app, message, Toast.LENGTH_LONG).show()
        }
    }
}