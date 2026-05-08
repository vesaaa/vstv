# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# ── ijkplayer (debugly/ijkplayer k0.8.9-beta) ─────────────────────
# 闪退根因修复：ijkplayer 依赖大量 JNI native 方法，ProGuard 裁剪后
# 会导致 UnsatisfiedLinkError。以下规则保护所有 ijkplayer 类及 native 方法。

# 保留所有 ijkplayer 类（包括 JNI 桥接、内部类、匿名类）
-keep class tv.danmaku.ijk.media.player.** { *; }
-keep class tv.danmaku.ijk.media.player.IjkMediaPlayer { *; }
-keep class tv.danmaku.ijk.media.player.ffmpeg.** { *; }

# 保留所有 JNI native 方法（无论哪个类）
-keepclasseswithmembernames class * {
    native <methods>;
}

# 保留 IMediaPlayer 接口的实现类（防止接口方法被内联/移除）
-keep class * extends tv.danmaku.ijk.media.player.IMediaPlayer { *; }
-keep class * implements tv.danmaku.ijk.media.player.IMediaPlayer$OnPreparedListener { *; }
-keep class * implements tv.danmaku.ijk.media.player.IMediaPlayer$OnErrorListener { *; }
-keep class * implements tv.danmaku.ijk.media.player.IMediaPlayer$OnInfoListener { *; }
-keep class * implements tv.danmaku.ijk.media.player.IMediaPlayer$OnCompletionListener { *; }
-keep class * implements tv.danmaku.ijk.media.player.IMediaPlayer$OnVideoSizeChangedListener { *; }
-keep class * implements tv.danmaku.ijk.media.player.IMediaPlayer$OnBufferingUpdateListener { *; }

# 保留 ITrackInfo 相关（轨选择）
-keep class tv.danmaku.ijk.media.player.misc.** { *; }

# ── LeanbackIjkVideoPlayer ────────────────────────────────────────
# 确保自定义播放器实现不被裁剪（lambda 回调需要）
-keep class com.vesaa.mytv.ui.screens.leanback.video.player.LeanbackIjkVideoPlayer { *; }
