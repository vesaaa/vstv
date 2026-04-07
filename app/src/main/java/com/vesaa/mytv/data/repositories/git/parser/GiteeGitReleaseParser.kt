package com.vesaa.mytv.data.repositories.git.parser

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import com.vesaa.mytv.BuildConfig
import com.vesaa.mytv.data.entities.GitRelease

class GiteeGitReleaseParser : GitReleaseParser {
    override fun isSupport(url: String): Boolean {
        return url.contains("gitee.com")
    }

    override suspend fun parse(data: String): GitRelease {
        val json = Json.parseToJsonElement(data).jsonObject

        val assets = json.getValue("assets").jsonArray
        val url = assets.pickVstvDefaultApkBrowserUrl(preferLiteApk = !BuildConfig.CHANNEL_LOGOS_ENABLED)
            ?: assets[0].jsonObject["browser_download_url"]!!.jsonPrimitive.content
        return GitRelease(
            version = json.getValue("tag_name").jsonPrimitive.content.removePrefix("v").trim(),
            downloadUrl = url,
            description = json.getValue("body").jsonPrimitive.content
        )
    }
}