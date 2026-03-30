# 更新日志（vstv）

本仓库为 fork，发行线使用 **`v0.0.x`** 标签与版本号。历史 `v1.x` / `tv*` 等标签已从本 fork 移除（原为上游同步残留）。

## [0.0.6] - 2026-03-30

- 默认节目单地址改为 `http://epg.51zmt.top:8000/e.xml`（仍支持同站 `e.xml.gz`）

## [0.0.5] - 2026-03-30

- 国产电视 / 无 GMS：README 说明目标机型与侧载；`AndroidManifest` 增加安装 APK 的 **`<queries>`**（Android 11+ 包可见性）
- 网页 / 远程触发安装：`ApkInstaller` 在主线程启动安装界面，补充异常与 Toast；`versionCode` 按 **semver x.y.z** 自动生成，便于覆盖安装

## [0.0.4] - 2026-03-30

- Media3：DASH、Smooth Streaming、FFmpeg 扩展；解析失败时 HLS / DASH / SS / 渐进式回退
- 调试日志：默认关闭；开启后网页「日志」可见，OkHttp 记录 URL、请求头、状态码与响应体长度（不记录正文）
- CI：Release 工作流动态解析 APK 路径、提高 Gradle 堆；ProGuard 保留 FFmpeg 包
- 默认 `versionName` 与标签线统一为 `0.0.x`

## [0.0.3] 及更早

见对应 GitHub Release 与提交记录。
