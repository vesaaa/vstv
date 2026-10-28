# 直播源管理

<!-- nav-top -->
<details>
<summary>帮助目录</summary>

- 入门
  - [基本介绍](01-introduction.md)
  - [添加直播源的四种方法](02-add-source.md)
  - [支持的格式](03-formats.md)
- 日常使用
  - **直播源管理**（当前）
  - [节目单与回看](05-epg.md)
  - [播放与画面](06-playback.md)
  - [老人小孩模式：精选与只看精选](07-favorites.md)
  - [多源合并：扩展频道](08-merge.md)
- 进阶
  - [AI 字幕](09-ai-subtitle.md)
  - [手机访问与网络](10-network.md)
  - [软件更新](11-update.md)
  - [扩展支持](12-extensions.md)
- 遇到问题
  - [常见问题](13-faq.md)

</details>
<!-- /nav-top -->

## 切换直播源

可以同时保存多个直播源，随时切换：

- **最快的方法**：播放时**长按确认键**（触屏长按约 0.8 秒），弹出「切换直播源」，选一个马上切换。
- 快捷菜单里的「直播源」也可以切换。
- **设置 → 直播源 → 直播源管理**：选一个切换过去；不要的**长按删除**。

在「切换直播源」列表里长按某个直播源，可以把它分享给别人（对方要先「请求协助」，见 [添加直播源的四种方法](02-add-source.md#四请求协助)）。

## 多线路

直播源里同一个分组下同名的频道，会自动合并成一个频道的多条线路。

- 播放时按**左 / 右键**切换线路，快捷菜单的「多线路」里也能选。
- 某条线路打不开时，VsTV 会自动试下一条，并记住能用的那条，下次直接用它。
- 多久打不开算失败，在 **设置 → 播放 → 播放器加载超时** 里调。

## 隐藏分组

不想看的分组可以隐藏：打开选台列表，在分组上**长按确认键**，选「隐藏分组」。

想恢复：**设置 → 直播源 → 恢复已隐藏分组**，只影响当前直播源。

## 直播源缓存

直播源下载一次后会缓存起来，打开更快。

- **直播源缓存时间**：超过这个时间才重新下载。直播源经常更新的可以调短。
- **清除缓存**：清掉直播源缓存、可播放地址和每个频道记住的线路。频道换了地址但 VsTV 还在用旧的时，清一下。

## 运营商组播（IPTV）

电信、联通、移动 IPTV 的组播地址（`udp://@239.x.x.x:端口`、`rtp://239.x.x.x:端口`）可以直接播放，**不需要 udpxy**。

1. 电视或盒子要接在 IPTV 网络上（一般接光猫的 iTV 口，或在路由器上配好 IPTV 的 VLAN）。
2. 直播源里直接写组播地址就行，其他不用设置。

如果家里已经有 rtp2httpd，也可以直接用它的播放列表，见 [支持的格式](03-formats.md#tvgo-和-rtp2httpd)。

## RTSP 直播源

运营商的 RTSP 直播和回看可以直接播放。经过路由器、防火墙时 VsTV 默认先用 TCP，打不开会自动改用 UDP。一般不用改设置，确实有问题时在 **设置 → 高级** 里调 RTSP 相关选项。

## 加密频道

支持 ClearKey 加密的频道（常见于 `.mpd` 的 DASH 直播），在 M3U 里用 `#KODIPROP` 写上密钥即可，写法见下面的 [样例](#clearkey-加密频道)。Widevine 等需要授权服务器的加密方式不支持。

## 请求头（User-Agent）

有的直播源要求特定的 User-Agent 才能打开：

- 添加直播源时在「User-Agent」里填，整个直播源都会用它。
- 也可以在 M3U 里给单个频道单独写，见下面的 [样例](#给频道加请求头)。直播源里写的请求头优先使用。

## 常见写法样例

下面的地址都是举例，换成你自己的即可。M3U 和 TXT 的基本格式见 [支持的格式](03-formats.md)。

### 直播源地址

添加直播源时填的地址：

```
http://example.com/iptv/live.m3u
https://example.com/iptv/live.txt
http://192.168.1.1:5140/playlist.m3u
```

### 需要账号密码的地址

直播源或频道需要账号密码登录（HTTP 基本认证）时，把账号密码写在地址里：

```
http://用户名:密码@example.com/iptv/live.m3u
```

下载直播源、播放频道时都会自动带上账号密码；RTSP 地址也可以这样写：

```
rtsp://用户名:密码@10.0.0.1:554/live/cctv1
```

账号或密码里有 `@`、`:`、`/` 等符号时，要写成编码后的形式（`@` 写成 `%40`，`:` 写成 `%3A`，`/` 写成 `%2F`）。

### 给频道加请求头

在 `#EXTINF` 和播放地址之间加 `#EXTVLCOPT` 行：

```
#EXTM3U
#EXTINF:-1 group-title="体育",体育频道
#EXTVLCOPT:http-user-agent=Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36
#EXTVLCOPT:http-referrer=https://example.com/
http://example.com/live/sport.m3u8
```

### ClearKey 加密频道

`license_key` 写成 `KID:KEY`，两段都是 32 位十六进制：

```
#EXTM3U
#EXTINF:-1 tvg-id="Discovery" group-title="纪录片",Discovery
#KODIPROP:inputstream.adaptive.license_type=clearkey
#KODIPROP:inputstream.adaptive.license_key=3ff02fcd80c2e3230c52ae70fe903410:c3a58ec867cc25829e1fbe8938442553
https://example.com/live/discovery/manifest.mpd
```

`license_type` 写 `clearkey` 或 `org.w3.clearkey` 都可以；需要请求头时，`#EXTVLCOPT` 也写在同一个位置。

### 一个频道多条线路

M3U 里把同名频道写几遍（要在同一个分组）：

```
#EXTINF:-1 group-title="央视",CCTV-1
http://a.example.com/cctv1.m3u8
#EXTINF:-1 group-title="央视",CCTV-1
http://b.example.com/cctv1.m3u8
```

TXT 里用 `#` 隔开：

```
CCTV-1,http://a.example.com/cctv1.m3u8#http://b.example.com/cctv1.m3u8
```

### 运营商组播

```
#EXTINF:-1 group-title="IPTV",CCTV-1
udp://@239.3.1.1:8000
#EXTINF:-1 group-title="IPTV",CCTV-2
rtp://239.3.1.2:8000
```

### 带回看的频道

`${(b)…}` 是节目开始时间，`${(e)…}` 是结束时间，括号后面是时间格式：

```
#EXTINF:-1 group-title="央视" catchup="append" catchup-source="?playseek=${(b)yyyyMMddHHmmss}-${(e)yyyyMMddHHmmss}" catchup-days="7",CCTV-1
http://example.com/live/cctv1.m3u8
```

rtp2httpd 等常用的 `{utc:YmdHMS}`、`{utcend:YmdHMS}` 写法也认识。更多说明见 [节目单与回看](05-epg.md#回看)。

## 打不开的时候

播放失败时，画面中间会显示「重试」「切换直播源」「添加直播源」「设置」几个按钮。更多排查方法见 [常见问题](13-faq.md)。

<!-- nav-bottom -->
---

上一篇：[支持的格式](03-formats.md) · [帮助目录](README.md) · 下一篇：[节目单与回看](05-epg.md)
<!-- /nav-bottom -->
