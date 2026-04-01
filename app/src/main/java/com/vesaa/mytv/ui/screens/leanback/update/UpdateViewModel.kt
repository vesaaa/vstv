package com.vesaa.mytv.ui.screens.leanback.update

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.vesaa.mytv.AppGlobal
import com.vesaa.mytv.data.entities.GitRelease
import com.vesaa.mytv.data.repositories.git.GitRepository
import com.vesaa.mytv.data.utils.Constants
import com.vesaa.mytv.ui.screens.leanback.toast.LeanbackToastProperty
import com.vesaa.mytv.ui.screens.leanback.toast.LeanbackToastState
import com.vesaa.mytv.ui.utils.SP
import com.vesaa.mytv.utils.Downloader
import com.vesaa.mytv.utils.Logger
import com.vesaa.mytv.utils.compareVersion
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext

class LeanBackUpdateViewModel : ViewModel() {
    private val log = Logger.create(javaClass.simpleName)

    private var _isChecking by mutableStateOf(false)
    val isChecking: Boolean get() = _isChecking

    private var _isUpdating = false

    private var _isUpdateAvailable by mutableStateOf(false)
    val isUpdateAvailable get() = _isUpdateAvailable

    private var _latestRelease by mutableStateOf(GitRelease())
    val latestRelease get() = _latestRelease

    /** 是否至少成功拉取过一次 GitHub Latest（用于设置页展示「上游版本」） */
    private var _hasRetrievedRemoteVersion by mutableStateOf(false)
    val hasRetrievedRemoteVersion: Boolean get() = _hasRetrievedRemoteVersion

    private var _lastCheckError by mutableStateOf<String?>(null)
    val lastCheckError: String? get() = _lastCheckError

    var showDialog by mutableStateOf(false)

    /** 是否正在下载安装包（用于设置页文案与防重复任务） */
    val isDownloadInProgress: Boolean get() = _isUpdating

    /**
     * 下载完成或命中有效缓存后发出绝对路径；界面在主线程收集并调起安装。
     * 避免在 IO 线程直接改 [mutableStateOf] 导致 Compose 漏重组、安装逻辑不触发。
     */
    private val _pendingInstallApkPath = MutableSharedFlow<String>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val pendingInstallApkPath: SharedFlow<String> = _pendingInstallApkPath.asSharedFlow()

    /**
     * 从 [Constants.GIT_RELEASE_LATEST_URL] 拉取最新 Release，与当前安装版本比较。
     * @return 是否成功完成请求与解析（网络失败等为 false，见 [lastCheckError]）
     */
    suspend fun checkUpdate(currentVersion: String): Boolean {
        if (_isChecking) return false
        _isChecking = true
        _lastCheckError = null
        return try {
            _latestRelease = GitRepository().latestRelease(Constants.GIT_RELEASE_LATEST_URL)
            val cur = currentVersion.trim().removePrefix("v")
            _isUpdateAvailable = _latestRelease.version.compareVersion(cur) > 0
            _hasRetrievedRemoteVersion = true
            invalidateStaleCachedApkIfNeeded()
            true
        } catch (e: Exception) {
            log.e("检查更新失败", e)
            _lastCheckError = e.message?.takeIf { it.isNotBlank() } ?: "检查失败，请检查网络"
            false
        } finally {
            _isChecking = false
        }
    }

    /** 远端 Release 与缓存记录不一致时删除旧包，避免误装旧版本 */
    private suspend fun invalidateStaleCachedApkIfNeeded() {
        val remoteNorm = normalizeVersion(_latestRelease.version)
        val cachedNorm = normalizeVersion(SP.updateLastDownloadedApkVersion)
        if (cachedNorm.isEmpty() || remoteNorm == cachedNorm) return
        withContext(Dispatchers.IO) {
            cachedApkFile().takeIf { it.exists() }?.delete()
        }
        SP.updateLastDownloadedApkVersion = ""
        SP.updateLastDownloadedApkUrl = ""
    }

    suspend fun downloadAndUpdate(latestFile: File) {
        if (!_isUpdateAvailable) return
        if (_isUpdating) {
            withContext(Dispatchers.Main) {
                LeanbackToastState.I.showToast("正在下载更新，请稍候")
            }
            return
        }

        val normTarget = normalizeVersion(_latestRelease.version)
        val cachedVer = normalizeVersion(SP.updateLastDownloadedApkVersion)
        val urlMatches = SP.updateLastDownloadedApkUrl == _latestRelease.downloadUrl
        if (latestFile.exists() && latestFile.length() >= MIN_APK_BYTES &&
            cachedVer.isNotEmpty() && cachedVer == normTarget && urlMatches &&
            _latestRelease.downloadUrl.isNotBlank()
        ) {
            withContext(Dispatchers.Main) {
                LeanbackToastState.I.showToast("已下载该版本，开始安装")
            }
            emitInstallRequest(latestFile.absolutePath)
            return
        }

        _isUpdating = true
        withContext(Dispatchers.Main) {
            LeanbackToastState.I.showToast(
                "开始下载更新",
                LeanbackToastProperty.Duration.Custom(10_000),
            )
        }

        try {
            withContext(Dispatchers.IO) {
                latestFile.parentFile?.mkdirs()
                if (latestFile.exists()) latestFile.delete()
            }
            SP.updateLastDownloadedApkVersion = ""
            SP.updateLastDownloadedApkUrl = ""

            Downloader.downloadTo(_latestRelease.downloadUrl, latestFile.path) {
                LeanbackToastState.I.showToast(
                    "正在下载更新: $it%",
                    LeanbackToastProperty.Duration.Custom(10_000),
                    "downloadProcess",
                )
            }

            val okSize = withContext(Dispatchers.IO) {
                latestFile.exists() && latestFile.length() >= MIN_APK_BYTES
            }
            if (!okSize) {
                throw Exception("下载不完整，请重试")
            }

            SP.updateLastDownloadedApkVersion = _latestRelease.version
            SP.updateLastDownloadedApkUrl = _latestRelease.downloadUrl

            withContext(Dispatchers.Main) {
                LeanbackToastState.I.showToast("下载更新成功")
            }
            emitInstallRequest(latestFile.absolutePath)
        } catch (ex: Exception) {
            log.e("下载更新失败", ex)
            withContext(Dispatchers.IO) {
                if (latestFile.exists()) latestFile.delete()
            }
            SP.updateLastDownloadedApkVersion = ""
            SP.updateLastDownloadedApkUrl = ""
            val hint = ex.message?.trim()?.take(160)?.takeIf { it.isNotBlank() }
                ?: "请检查网络与 GitHub 访问"
            withContext(Dispatchers.Main) {
                LeanbackToastState.I.showToast("下载失败：$hint")
            }
        } finally {
            _isUpdating = false
        }
    }

    private suspend fun emitInstallRequest(path: String) {
        withContext(Dispatchers.Main) {
            _pendingInstallApkPath.emit(path)
        }
    }

    private fun cachedApkFile() = File(AppGlobal.cacheDir, CACHED_APK_NAME)

    private fun normalizeVersion(v: String) = v.trim().removePrefix("v").lowercase()

    companion object {
        private const val CACHED_APK_NAME = "latest.apk"
        private const val MIN_APK_BYTES = 512L * 1024L
    }
}
