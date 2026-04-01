package com.vesaa.mytv.ui.screens.leanback.update

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.vesaa.mytv.data.entities.GitRelease
import com.vesaa.mytv.data.repositories.git.GitRepository
import com.vesaa.mytv.data.utils.Constants
import com.vesaa.mytv.ui.screens.leanback.toast.LeanbackToastProperty
import com.vesaa.mytv.ui.screens.leanback.toast.LeanbackToastState
import com.vesaa.mytv.utils.Downloader
import com.vesaa.mytv.utils.Logger
import com.vesaa.mytv.utils.compareVersion
import java.io.File

class LeanBackUpdateViewModel : ViewModel() {
    private val log = Logger.create(javaClass.simpleName)

    private var _isChecking by mutableStateOf(false)
    val isChecking: Boolean get() = _isChecking

    private var _isUpdating = false

    private var _isUpdateAvailable by mutableStateOf(false)
    val isUpdateAvailable get() = _isUpdateAvailable

    private var _updateDownloaded by mutableStateOf(false)
    val updateDownloaded get() = _updateDownloaded

    private var _latestRelease by mutableStateOf(GitRelease())
    val latestRelease get() = _latestRelease

    /** 是否至少成功拉取过一次 GitHub Latest（用于设置页展示「上游版本」） */
    private var _hasRetrievedRemoteVersion by mutableStateOf(false)
    val hasRetrievedRemoteVersion: Boolean get() = _hasRetrievedRemoteVersion

    private var _lastCheckError by mutableStateOf<String?>(null)
    val lastCheckError: String? get() = _lastCheckError

    var showDialog by mutableStateOf(false)

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
            true
        } catch (e: Exception) {
            log.e("检查更新失败", e)
            _lastCheckError = e.message?.takeIf { it.isNotBlank() } ?: "检查失败，请检查网络"
            false
        } finally {
            _isChecking = false
        }
    }

    suspend fun downloadAndUpdate(latestFile: File) {
        if (!_isUpdateAvailable) return
        if (_isUpdating) return

        _isUpdating = true
        _updateDownloaded = false
        LeanbackToastState.I.showToast(
            "开始下载更新",
            LeanbackToastProperty.Duration.Custom(10_000),
        )

        try {
            Downloader.downloadTo(_latestRelease.downloadUrl, latestFile.path) {
                LeanbackToastState.I.showToast(
                    "正在下载更新: $it%",
                    LeanbackToastProperty.Duration.Custom(10_000),
                    "downloadProcess"
                )
            }

            _updateDownloaded = true
            LeanbackToastState.I.showToast("下载更新成功")
        } catch (ex: Exception) {
            log.e("下载更新失败", ex)
            val hint = ex.message?.trim()?.take(120)?.takeIf { it.isNotBlank() } ?: "请检查网络与 GitHub 访问"
            LeanbackToastState.I.showToast("下载失败：$hint")
        } finally {
            _isUpdating = false
        }
    }
}
