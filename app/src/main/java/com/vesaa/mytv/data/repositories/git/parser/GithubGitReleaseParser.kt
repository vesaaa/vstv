package com.vesaa.mytv.data.repositories.git.parser

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import com.vesaa.mytv.data.entities.GitRelease

class GithubGitReleaseParser : GitReleaseParser {
    override fun isSupport(url: String): Boolean {
        return url.contains("github.com")
    }

    override suspend fun parse(data: String): GitRelease {
        val json = Json.parseToJsonElement(data).jsonObject

        val assets = json.getValue("assets").jsonArray
        if (assets.isEmpty()) {
            throw Exception("Release 未包含任何附件，请确认已上传 APK")
        }
        val url = assets.pickVstvDefaultApkBrowserUrl()
            ?: throw Exception(
                "Release 中没有与本机渠道/CPU 匹配的 APK。" +
                    "鸿蒙（Z视介）仅升鸿蒙附件；原味 ARM 仅 arm 包；原味 x86 仅 x86_64 包，互不混用。"
            )
        // 使用 GitHub 直链：mirror.ghproxy.com 等第三方镜像易失效，会导致「下载更新失败」
        return GitRelease(
            version = json.getValue("tag_name").jsonPrimitive.content.removePrefix("v").trim(),
            downloadUrl = url,
            description = json.getValue("body").jsonPrimitive.content
        )
    }
}