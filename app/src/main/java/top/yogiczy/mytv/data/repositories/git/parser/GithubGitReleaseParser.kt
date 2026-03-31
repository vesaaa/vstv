package top.yogiczy.mytv.data.repositories.git.parser

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import top.yogiczy.mytv.data.entities.GitRelease

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
            ?: assets[0].jsonObject["browser_download_url"]!!.jsonPrimitive.content
        // 使用 GitHub 直链：mirror.ghproxy.com 等第三方镜像易失效，会导致「下载更新失败」
        return GitRelease(
            version = json.getValue("tag_name").jsonPrimitive.content.removePrefix("v").trim(),
            downloadUrl = url,
            description = json.getValue("body").jsonPrimitive.content
        )
    }
}