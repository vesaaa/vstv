package com.vesaa.mytv.data.repositories.git.parser

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import com.vesaa.mytv.data.entities.GitRelease

class GiteeGitReleaseParser : GitReleaseParser {
    override fun isSupport(url: String): Boolean {
        return url.contains("gitee.com")
    }

    override suspend fun parse(data: String): GitRelease {
        val json = Json.parseToJsonElement(data).jsonObject

        val assets = json.getValue("assets").jsonArray
        if (assets.isEmpty()) {
            throw Exception("Release 未包含任何附件，请确认已上传 APK")
        }
        val url = assets.pickVstvDefaultApkBrowserUrl()
            ?: throw Exception(
                "Release 中没有与本机渠道/CPU 匹配的 APK（鸿蒙只升鸿蒙附件；原味按 ARM / x86_64 区分）。"
            )
        return GitRelease(
            version = json.getValue("tag_name").jsonPrimitive.content.removePrefix("v").trim(),
            downloadUrl = url,
            description = json.getValue("body").jsonPrimitive.content
        )
    }
}