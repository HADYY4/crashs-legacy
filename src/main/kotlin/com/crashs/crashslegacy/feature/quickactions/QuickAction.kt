package com.crashs.crashslegacy.feature.quickactions

/** One user-defined macro shown as a pill on the Quick Actions page. */
data class QuickAction(
    var id: String,
    var name: String,
    var icon: String,
    var operations: MutableList<QuickOperation> = mutableListOf(),
)

/** One step of a macro. `value` holds chat text / command; the other fields depend on [type]. */
data class QuickOperation(
    var type: String = QuickOperationTypes.COMMAND,
    var value: String = "",
    var ticks: Int = 0,
    var ms: Int = 0,
    var slot: Int = 0,
    var button: Int = 0,
)

object QuickOperationTypes {
    const val CHAT = "chat"
    const val COMMAND = "command"
    const val WAIT_TICKS = "wait_ticks"
    const val WAIT_MS = "wait_ms"
    const val WAIT_CONTAINER = "wait_container"
    const val CLICK_SLOT = "click_slot"

    val ALL = listOf(CHAT, COMMAND, WAIT_TICKS, WAIT_MS, WAIT_CONTAINER, CLICK_SLOT)

    fun label(type: String): String = when (type) {
        CHAT -> "发送聊天信息"
        COMMAND -> "发送命令"
        WAIT_TICKS -> "等待 tick"
        WAIT_MS -> "等待毫秒"
        WAIT_CONTAINER -> "等待容器标题"
        CLICK_SLOT -> "点击容器槽位"
        else -> type
    }
}
