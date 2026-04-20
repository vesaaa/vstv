package com.vesaa.mytv.ui.screens.leanback.video.player

import android.content.Context

/**
 * 按 URL 持久化缓存 HLS 直播流的滑动窗口时长（毫秒）。
 *
 * 用于切台时快速给出最优 `MediaItem.LiveConfiguration`，避免每次都要先抓 m3u8
 * 才能定位起播点。第一次访问某频道时会从默认配置起步，并在后台抓取 m3u8 写入
 * 缓存；下次切回同一频道即可直接用最优配置（接近但不越过窗口末端）起播。
 *
 * 持久化使用独立的 `SharedPreferences` 文件，与应用主偏好隔离，避免污染主表。
 * 条目上限 [MAX_ENTRIES]，超过时一次性淘汰前 [EVICT_BATCH] 条（近似 FIFO，Android
 * `SharedPreferences` 内部使用 `LinkedHashMap` 保留插入顺序）。
 */
internal object HlsWindowCache {
    private const val PREF_NAME = "player_hls_window_cache"
    private const val MAX_ENTRIES = 500
    private const val EVICT_BATCH = 50

    fun get(context: Context, url: String): Long? {
        if (url.isEmpty()) return null
        val prefs = context.applicationContext
            .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val v = prefs.getLong(url, -1L)
        return if (v > 0L) v else null
    }

    fun put(context: Context, url: String, windowMs: Long) {
        if (url.isEmpty() || windowMs <= 0L) return
        val prefs = context.applicationContext
            .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val all = prefs.all
        val isNewEntry = !all.containsKey(url)
        if (isNewEntry && all.size >= MAX_ENTRIES) {
            val editor = prefs.edit()
            all.keys.asSequence().take(EVICT_BATCH).forEach { editor.remove(it) }
            editor.putLong(url, windowMs).apply()
        } else {
            prefs.edit().putLong(url, windowMs).apply()
        }
    }
}


