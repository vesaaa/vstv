package com.vesaa.mytv.proprietary

/**
 * GitHub Releases 检查地址。
 * 官方发版 CI 会从 `proprietary/bundle.tar.gz.enc` 解密并覆盖本文件；若 bundle 内无 .kt，则沿用此处提交内容。
 */
object ProprietaryUpdate {
    const val GIT_RELEASE_LATEST_URL =
        "https://api.github.com/repos/vesaaa/vstv/releases/latest"
}
