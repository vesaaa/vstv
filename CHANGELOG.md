# 更新日志（VsTV / vstv）

应用展示名 **VsTV**；仓库与构建标识仍为 **vstv**。发行线使用 **`v0.0.x`** 标签与版本号。历史 `v1.x` / `tv*` 等标签已从本 fork 移除（原为上游同步残留）。

## [0.0.19] - 2026-03-30

- 播放流 HTTP User-Agent 与直播源请求头中的 **User-Agent** 统一：`SP.playbackHttpUserAgent()`，`Media3VideoPlayer` 使用；未配置 UA 时仍为内置 `ExoPlayer`
- 移除独立「播放器自定义 UA」与网页对应项；移除 `SP.videoPlayerUserAgent` 及 `GET/POST` 中的 `videoPlayerUserAgent`

## [0.0.18] - 2026-03-30

- 订阅请求头：`normalizeIptvRequestHeadersInput`，单行无冒号视为仅填 **User-Agent 取值**；网页与 `IptvRepository` / `HttpServer` / `MainViewModel` 写入一致
- 网页：UA 表单只填取值（如 `okHttp/Mod-1.5.0.0`）；`GET /api/settings` 不再返回节目单 URL；节目单仅在有填写时推送，留空不修改；文案强调默认在电视端选择
- 设置：移除「网络」分类（`SettingsCategoryHttp`）；直播源 / 节目单对话框与列表文案突出 **选择默认**；历史直播源列表排序展示

## [0.0.17] - 2026-03-30

- 网页：移除调试区、日志 Tab、APK 上传；`GET /api/settings` 不再返回历史列表与调试日志；`POST /api/settings` 仅更新 JSON 中出现的字段，避免网页推送误改电视端调试开关
- 网页：直播源地址与请求头（含 User-Agent）**一组一次推送**；说明与 `HttpHeaderLines` 一致（`Name: Value`、冒号分隔）；设备未存请求头时表单预填 `User-Agent: okHttp/Mod-1.5.0.0`（仅完整加载时）
- 服务端：移除 `POST /api/upload/apk`

## [0.0.16] - 2026-03-30

- 网页配置：进一步修复 Vue **compiler-30**（in-DOM 模板中 `v-if`/`v-else` 之间空白文本节点；日志区改为 `v-show`）；**禁止对自定义组件使用 `/>` 自闭合**（HTML 解析下易未闭合，把后续 DOM 吞进错误节点，部分 WebView 必现）
- 未配置页：`v-if="!settings"` 与主内容 `v-else` 写在同一行，避免相邻判断断裂

## [0.0.15] - 2026-03-30

- 修复：`AllSettings` 默认参数中误用裸 `SERVER_PORT` 导致 Release 编译失败，改为 `HttpServer.SERVER_PORT`

## [0.0.14] - 2026-03-30

- 设置页 HTTP 端口改为 **1616**（`HttpServer`、README、网页文案）
- 网页配置：修复 Vue **compiler-30**（`v-if`/`v-else-if` 与 `van-tabbar` 链断裂）；用 `div`+`v-show` 替代外层 HTML `<template>`
- 网页：展示**当前默认直播源**（未配置提示）、**当前生效节目单 URL**（内置默认时说明）、历史列表**设为默认**
- **经典选台**：`SP` 默认值改为 **开启**（未写过该键的新装/首次生效）
- 启动：**加载中/失败**界面顶部提示 + 始终显示时间角标，减少黑屏「卡死」感

## [0.0.13] - 2026-03-30

- 发行：Release APK 重命名——常规版 **`vstv-<ver>-all-sdk21.apk`**（去掉 `-original`）；鸿蒙向变体 **`vstv-<ver>-all-sdk21-HarmonyOS.apk`**（原 `-disguised` 文件名）；README 已说明选型
- 更新：GitHub / Gitee Release 解析在 **多 APK** 时优先选取常规包，避免自动更新误下 HarmonyOS 变体

## [0.0.12] - 2026-03-30

- 构建：移除腾讯 **X5（TBS）** 相关 productFlavor（`originalX5Arm64` / `originalX5Armeabi`）、`tbssdk` 依赖、`src/x5`、`USE_X5` / `BuildConfig` 字段、ProGuard 保留规则、`settings` 中腾讯 Maven 镜像；Release 工作流仅上传 **original** 与 **disguised** 两个 APK（Gradle flavor 名未改，仅 CI 产物名于 0.0.13 调整）

## [0.0.11] - 2026-03-30

- 修复：`LeanbackPanelIptvInfo` 在 **`urlList` 为空**（无订阅、占位频道、解析异常）时仍读 `urlList[iptvUrlIdx]`，导致与此前相同的 `Empty list doesn't contain element at index 0`（快捷面板、节目面板底栏等均会组合该组件）
- 加固：节目单弹窗左右切换当前频道时，**空 `iptvList`** 不再做下标访问

## [0.0.10] - 2026-03-30

- 修复：经典频道面板在 **无订阅 / 分组列表为空** 时打开会 `IndexOutOfBoundsException`（误用 `iptvGroupList[0]`）；空列表时回退为 `IptvGroup()`

## [0.0.9] - 2026-03-30

- 品牌：应用展示名 **VsTV**（`app_name`、`Constants.APP_TITLE`、网页设置标题）；仓库标识 **vstv** 不变

## [0.0.8] - 2026-03-30

- 构建：移除 `androidx.media3:media3-decoder-ffmpeg`（官方未在 Google Maven 发布预构建 AAR，Gradle 无法解析）；播放仍用 Media3 + 设备 MediaCodec

## [0.0.7] - 2026-03-30

- CI：Release 工作流 YAML 修正；`setup-java` 内置 Gradle 缓存；显式 `versionCode`；Gradle 重试；TBS 双镜像；APK 递归查找

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
