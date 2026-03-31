package top.yogiczy.mytv.ui.utils

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** 网页推送或设置内修改直播源/节目单后，通知主界面 ViewModel 重新拉取数据。 */
object WebPushConfigNotifier {
    private val _updates = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val updates: SharedFlow<Unit> = _updates.asSharedFlow()

    fun notifyConfigMayHaveChanged() {
        _updates.tryEmit(Unit)
    }
}
