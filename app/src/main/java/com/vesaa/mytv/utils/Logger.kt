package com.vesaa.mytv.utils

import android.util.Log
import kotlinx.serialization.Serializable
import com.vesaa.mytv.data.utils.Constants
import com.vesaa.mytv.ui.utils.SP

/**
 * 日志工具类
 *
 * 写入 [history] 仅在 [SP.debugAppLog] 为 true 时生效（默认关闭），供电视端调试开关与调测使用；Logcat 始终输出。
 */
class Logger private constructor(
    private val tag: String
) {
    fun d(message: String, throwable: Throwable? = null) {
        Log.d(tag, message, throwable)
        addHistoryItem(HistoryItem(LevelType.DEBUG, tag, message, throwable?.message))
    }

    fun i(message: String, throwable: Throwable? = null) {
        Log.i(tag, message, throwable)
        addHistoryItem(HistoryItem(LevelType.INFO, tag, message, throwable?.message))
    }

    fun w(message: String, throwable: Throwable? = null) {
        Log.w(tag, message, throwable)
        addHistoryItem(HistoryItem(LevelType.WARN, tag, message, throwable?.message))
    }

    fun e(message: String, throwable: Throwable? = null) {
        Log.e(tag, message, throwable)
        addHistoryItem(HistoryItem(LevelType.ERROR, tag, message, throwable?.message))
    }

    fun wtf(message: String, throwable: Throwable? = null) {
        Log.wtf(tag, message, throwable)
        addHistoryItem(HistoryItem(LevelType.ERROR, tag, message, throwable?.message))
    }

    companion object {
        fun create(tag: String) = Logger(tag)

        private val _history = mutableListOf<HistoryItem>()
        val history: List<HistoryItem>
            get() = _history

        fun addHistoryItem(item: HistoryItem) {
            val enabled = runCatching { SP.debugAppLog }.getOrDefault(false)
            if (!enabled) return
            synchronized(_history) {
                _history.add(item)
                while (_history.size > Constants.LOG_HISTORY_MAX_SIZE) _history.removeAt(0)
            }
        }

        fun clearHistory() {
            synchronized(_history) {
                _history.clear()
            }
        }
    }

    @Serializable
    enum class LevelType {
        DEBUG, INFO, WARN, ERROR
    }

    @Serializable
    data class HistoryItem(
        val level: LevelType,
        val tag: String,
        val message: String,
        val cause: String? = null,
        val time: Long = System.currentTimeMillis(),
    )
}

/**
 * 注入日志
 */
abstract class Loggable(private val tag: String? = null) {
    protected val log: Logger
        get() = Logger.create(tag ?: javaClass.simpleName)
}