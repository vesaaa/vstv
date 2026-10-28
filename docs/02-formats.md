# 支持的格式

[返回目录](README.md)

## 直播源格式

### M3U（最常见）

以 `#EXTM3U` 开头，每个频道一行 `#EXTINF` 加一行播放地址：

```
#EXTM3U x-tvg-url="https://example.com/epg.xml"
#EXTINF:-1 tvg-id="CCTV1" tvg-name="CCTV1" tvg-logo="https://example.com/cctv1.png" group-title="央视",CCTV-1 综合
http://example.com/live/cctv1.m3u8
```

VsTV 认识这些常用写法：

| 写法 | 作用 |
|------|------|
| `x-tvg-url` / `url-tvg`（写在第一行） | 节目单地址，多个地址用逗号隔开，一个打不开会试下一个 |
| `group-title` | 分组 |
| `tvg-name`、`tvg-id` | 频道名、对应节目单里的频道 |
| `tvg-logo` | 台标 |
| `catchup`、`catchup-source`、`catchup-days` | 回看模板，见 [节目单与回看](05-epg.md) |
| `#EXTVLCOPT:http-user-agent=…`、`#EXTVLCOPT:http-referrer=…` | 播放这个频道时带上的请求头 |
| `#KODIPROP:inputstream.adaptive.license_type=clearkey` 和 `license_key=…` | ClearKey 加密频道的密钥 |

同一个频道名出现多次时，会合并成一个频道的多条线路。

### TXT（TVBox / 影视仓格式）

```
央视频道,#genre#
CCTV1,http://a.example/live1.m3u8#http://b.example/live1.m3u8
CCTV2,http://a.example/live2.m3u8$1920×1080
```

- `分组名,#genre#` 开始一个分组。
- `频道名,地址` 是一个频道；多条线路用 `#` 隔开。
- 地址后面的 `$说明`（比如分辨率）会自动忽略。

### tvgo 和 rtp2httpd

- **tvgo**：运行在小主机或 NAS 上的直播服务，可以用「自动添加」一键找到，还能提供免费的 AI 本地翻译。
- **rtp2httpd**：把运营商组播转成 http 的服务。填它的播放列表地址（如 `http://192.168.1.1:5140/playlist.m3u`），或用「自动添加」找到；列表里的相对路径会自动补全。

### 本地文件

可以直接选电视上的 m3u / txt 文件，或者用手机扫码在网页里上传，见 [添加直播源的四种方法](03-add-source.md)。

## 播放协议

VsTV 按地址自动识别；服务器实际返回的格式和猜的不一样时，会自动换一种方式再试。

| 协议 | 地址特征 | 说明 |
|------|---------|------|
| HLS | `.m3u8` | 最常见的直播格式，支持多音轨 |
| UDP / RTP 组播 | `udp://`、`rtp://` | 运营商 IPTV 组播，不需要 udpxy 转发 |
| RTSP | `rtsp://` | 包括运营商的 RTSP 回看 |
| DASH | `.mpd` | |
| Smooth Streaming | `.ism`、`Manifest` | |
| HTTP 直链 | `.mp4`、`.mkv`、`.ts`、`.flv` 等 | |

## 视频格式

| 编码 | 说明 |
|------|------|
| H.264 / AVC | 所有设备都能播 |
| H.265 / HEVC | 大多数电视和盒子能硬解；设备不支持时自动改用软解 |
| AV1、VP9 | 新设备能硬解；不支持时用软解（x86_64 版没有这两种软解） |
| MPEG-2、AVS2、AVS3 等 | 只能靠设备硬解，设备不支持就播不了 |
| HDR10、HLG、杜比视界 | 能播；要看到真正的 HDR 效果，电视需要支持 HDR，见 [常见问题](13-faq.md) |

**硬解和软解**：硬解用设备里的专用芯片，省电、流畅；软解用处理器算，兼容性好，但 4K、高帧率时可能卡顿。VsTV 默认用硬解，硬解不行时才用软解。正在用哪种，可以在快捷菜单的「解码与码流」里看。

## 音频格式

| 编码 | 说明 |
|------|------|
| AAC、MP3、Opus、FLAC、ALAC、G.711 | 都能播 |
| 杜比数字（AC-3）、杜比数字+（E-AC-3）、杜比全景声 | 能播；也可以直通给电视或功放解码 |
| DTS、杜比 TrueHD | 能播 |
| 菁彩声（Audio Vivid / AV3A） | 需要安装 arm-av3a 菁彩声版 |
| MP2 | 取决于设备 |

## 字幕

支持 WebVTT、SRT、DVB、PGS、CEA-608；暂不支持 ASS / SSA。外语节目还可以用 [AI 字幕](09-ai-subtitle.md) 实时翻译。
