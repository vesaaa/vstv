<div align="center">
    <h1>VsTV</h1>
    <p><strong>vstv</strong> — 仓库、CI 产物前缀与标识沿用此名</p>
<div align="center">

![GitHub repo size](https://img.shields.io/github/repo-size/vesaaa/vstv)
![GitHub Repo stars](https://img.shields.io/github/stars/vesaaa/vstv)
![GitHub all releases](https://img.shields.io/github/downloads/vesaaa/vstv/total)

</div>
    <p>基于 Android 原生（Kotlin / Compose / Media3）的电视直播客户端（<strong>VsTV</strong>），默认不内置订阅源，需自行配置。</p>
</div>

## 与上游

- 上游：<https://github.com/sakana164/mytv-android>（及更早 lineage）
- 本仓库：<https://github.com/vesaaa/vstv>（应用展示名 <strong>VsTV</strong>），**发行标签仅使用 `v0.0.x`**

## 目标设备与运行环境（国产电视等）

主要面向 **智能电视 / 盒子**：如 TCL、海信、酷开、华为智慧屏、荣耀、小米电视等，系统多为 **厂商定制 Android**（含鸿蒙设备上的 **Android 兼容层**）。此类环境通常 **没有谷歌移动服务（GMS）**、**没有 Play 商店**，依赖 **U 盘 / 远程安装 / 网页上传 APK** 等方式侧载。

本应用 **不依赖** Google Play 服务或谷歌账号；网络播放使用 **OkHttp + Media3**，设置页走本机 **HTTP 服务（10481 端口）**。已做或请注意：

| 项 | 说明 |
|----|------|
| 桌面入口 | `LeanbackActivity` 同时声明 **LEANBACK_LAUNCHER** 与 **LAUNCHER**，便于在电视桌面出现 |
| 明文 HTTP | 已允许 **明文流量**（`usesCleartextTraffic` + `networkSecurityConfig`），兼容常见 IPTV/EPG **http** 源 |
| 覆盖安装 | `versionCode` 由 **x.y.z** 自动推导（与 `-PreleaseVersion` / 默认 `versionName` 一致），避免长期固定为 `1` 导致无法升级 |
| 网页装 APK | Android 11+ 已声明 **`<queries>`** 指向 APK 安装 `Intent`，减少「点了安装没反应」 |
| 安装权限 | 首次侧载或网页更新需在系统设置中允许 **安装未知应用**；部分品牌路径名称不同 |
| 开机自启 | **BootReceiver** 依赖 `BOOT_COMPLETED`，华为 / 小米等可能需在系统里为本应用 **允许自启动 / 后台活动**，否则无效 |

## 使用

### 操作方式

> 遥控器操作方式与主流视频播放软件类似；

- 频道切换：使用上下方向键，或者数字键切换频道；屏幕上下滑动；
- 频道选择：OK键；单击屏幕；
- 设置页面：按下菜单、帮助键，长按OK键；双击、长按屏幕；

### 触摸键位对应

- 方向键：屏幕上下左右滑动
- OK键：点击屏幕
- 长按OK键：长按屏幕
- 菜单、帮助键：双击屏幕

### 自定义设置

- 访问以下网址：`http://<设备IP>:10481`
- 打开应用设置界面，移到最后一项
- 支持自定义订阅源、自定义节目单、缓存时间等等
- 须知：网页中引用了 `jsdelivr` 的 CDN，请确保能够正常访问

### 自定义订阅源

- 设置入口：自定义设置网址
- 格式支持：m3u 格式、tvbox 格式

### 多订阅源

- 设置入口：打开应用设置界面，选中`自定义订阅源`项，点击后将弹出历史订阅源列表
- 历史订阅源列表：短按可切换当前订阅源（需重启），长按将清除历史记录；该功能类似于`多仓`，主要用于简化订阅源切换流程
- 须知：
    1. 当订阅源数据获取成功时，会将该订阅源保存到历史订阅源列表中
    2. 当订阅源数据获取失败时，会将该订阅源移出历史订阅源列表

### 多线路

- 功能描述：同一频道拥有多个播放地址，相关标识位于频道名称后面
- 切换线路：左右方向键；屏幕左右滑动
- 自动切换：当当前线路播放失败后，将自动播放下一个线路，直至最后
- 须知：
    1. 当某一线路播放成功后，会将该线路的`域名`保存到`可播放域名列表`中
    2. 当某一线路播放失败后，会将该线路的`域名`移出`可播放域名列表`
    3. 当播放某一频道时，将优先选择匹配`可播放域名列表`的线路

### 自定义节目单

- 设置入口：自定义设置网址
- 格式支持：.xml、.xml.gz格式

### 多节目单

- 设置入口：打开应用设置界面，选中`自定义节目单`项，点击后将弹出历史节目单列表
- 具体功能请参照`多订阅源`

### 当天节目单

- 功能入口：打开应用选台界面，选中某一频道，按下菜单、帮助键、双击屏幕，将打开当天节目单
- 须知：由于该应用不支持回放功能，所以更早的节目单没必要展示

### 频道收藏

- 功能入口：打开应用选台界面，选中某一频道，长按OK键、长按屏幕，将收藏/取消收藏该频道
- 切换显示收藏列表：首先移动到频道列表顶部，然后再次按下方向键上，将切换显示收藏列表；手机长按频道信息切换

## 下载

在 [Releases](https://github.com/vesaaa/vstv/releases) 下载预编译 APK，或本地自行编译。

每个版本会提供 **两个** APK，请按设备与需求选择（文件名中的 `<ver>` 为版本号，如 `0.0.13`）：

| 文件 | 适用场景 |
|------|----------|
| **`vstv-<ver>-all-sdk21.apk`** | **默认下载**：常规 VsTV，包名 `top.yogiczy.mytv`，桌面显示 **VsTV**。适用于大多数 Android 电视 / 盒子 / 手机。 |
| **`vstv-<ver>-all-sdk21-HarmonyOS.apk`** | **华为智慧屏 / 鸿蒙生态**等需要「非 mytv 系包名」侧载时使用：包名 `com.chinablue.tv`，桌面名 **Z视介**，与常规版 **互不相同包**，数据与更新通道彼此独立。 |

应用内 **检查更新** 会拉取与 **常规包** 对应的 APK（`…-all-sdk21.apk`）；若你安装的是 HarmonyOS 变体，请自行在 Release 页下载带 **HarmonyOS** 后缀的安装包升级。

## 说明

- 目标环境：Android 5+，电视 / 盒子 / 平板 / 手机（含 Leanback）
- 播放能力依赖设备 **MediaCodec** 硬解与订阅源质量；已集成 Media3（HLS / DASH / SS / RTSP 等）。FFmpeg 扩展需自行按 [androidx/media 文档](https://github.com/androidx/media/tree/release/libraries/decoder_ffmpeg) 编译 AAR，官方 Maven 无预构建产物。
- 自动更新检查指向 **本仓库** 的最新 Release

## 功能

- [x] 换台反转
- [x] 数字选台
- [x] 节目单
- [x] 开机自启
- [x] 自动更新
- [x] 多订阅源
- [x] 多线路
- [x] 自定义订阅源
- [x] 多节目单
- [x] 自定义节目单
- [x] 频道收藏
- [x] 应用自定义设置

## 更新日志

[CHANGELOG.md](./CHANGELOG.md)

## 声明

**VsTV**（仓库标识 vstv）仅供学习与交流。所用接口与数据来自公开网络与用户自行配置的订阅，不提供任何破解内容。

## 致谢

- [mytv-android](https://github.com/sakana164/mytv-android) 及上游贡献者
- [my-tv](https://github.com/lizongying/my-tv)
- [参考设计稿](https://github.com/lizongying/my-tv/issues/594)
- [IPV6直播源](https://github.com/zhumeng11/IPTV)
- [live](https://github.com/fanmingming/live)
