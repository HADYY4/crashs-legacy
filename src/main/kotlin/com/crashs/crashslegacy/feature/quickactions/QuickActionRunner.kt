package com.crashs.crashslegacy.feature.quickactions

import com.crashs.crashslegacy.CrashsLegacyMod
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.inventory.ClickType
import net.minecraft.network.chat.Component
import net.minecraft.ChatFormatting

/**
 * Runs one Quick Action at a time on the client tick.
 *
 * The screen is closed before the first step, so a macro started from a container GUI returns to
 * that container and `click_slot` can use its screen handler.
 */
object QuickActionRunner {

    private var action: QuickAction? = null
    private var index = 0
    private var startDelay = 0
    private var waitTicks = 0
    private var waitUntilMs = 0L
    private var waitContainerTitle: String? = null
    private var waitContainerExact = false
    private var waitContainerDeadline = 0L
    private var waitContainerLogged = false
    private var running = false

    fun isRunning(): Boolean = running

    fun start(action: QuickAction): Boolean {
        if (running) {
            notify("快捷操作不支持同时使用")
            return false
        }
        if (action.operations.isEmpty()) {
            notify("快捷操作没有可执行步骤")
            return false
        }
        this.action = action
        index = 0
        startDelay = 1
        waitTicks = 0
        waitUntilMs = 0L
        waitContainerTitle = null
        waitContainerExact = false
        waitContainerDeadline = 0L
        waitContainerLogged = false
        running = true
        QuickActionDebug.log("start id=${action.id} name=${action.name} steps=${action.operations.size}")
        return true
    }

    fun tick(client: Minecraft) {
        if (!running) return
        val player = client.player
        if (player == null || client.level == null) {
            finish("world gone")
            return
        }
        if (player.isRemoved) {
            finish("player dead")
            return
        }
        if (startDelay > 0) {
            startDelay--
            return
        }
        if (waitTicks > 0) {
            waitTicks--
            return
        }
        if (waitUntilMs > System.currentTimeMillis()) return

        if (waitContainerTitle != null) {
            if (!pollContainerWait(client)) return
            index++
            if (index >= (action?.operations?.size ?: 0)) {
                finish("completed")
                return
            }
        }

        val current = action ?: run { finish("no action"); return }
        if (index >= current.operations.size) {
            finish("completed")
            return
        }

        val operation = current.operations[index]
        if (!execute(client, operation)) return
        index++
        if (index >= current.operations.size) finish("completed")
    }

    private fun execute(client: Minecraft, operation: QuickOperation): Boolean {
        if (operation.type != QuickOperationTypes.WAIT_CONTAINER) {
            QuickActionDebug.log("step type=${operation.type} value=${operation.value}")
        }
        return when (operation.type) {
            QuickOperationTypes.CHAT -> {
                client.player?.connection?.sendChat(operation.value)
                true
            }

            QuickOperationTypes.COMMAND -> {
                client.player?.connection?.sendCommand(operation.value.removePrefix("/"))
                true
            }

            QuickOperationTypes.WAIT_TICKS -> {
                waitTicks = operation.ticks.coerceAtLeast(0)
                true
            }

            QuickOperationTypes.WAIT_MS -> {
                waitUntilMs = System.currentTimeMillis() + operation.ms.coerceAtLeast(0)
                true
            }

            QuickOperationTypes.WAIT_CONTAINER -> {
                val title = operation.value.trim()
                if (title.isEmpty()) {
                    abort("等待容器标题不能为空")
                    return false
                }
                waitContainerTitle = title
                waitContainerExact = operation.button == 1
                waitContainerDeadline = System.currentTimeMillis() +
                    (operation.ms.takeIf { it > 0 } ?: 10000).coerceAtLeast(1000)
                waitContainerLogged = false
                QuickActionDebug.log(
                    "wait-container start title=$title exact=$waitContainerExact " +
                        "timeout=${waitContainerDeadline - System.currentTimeMillis()}ms",
                )
                if (pollContainerWait(client)) return true
                false
            }

            QuickOperationTypes.CLICK_SLOT -> clickSlot(client, operation)

            else -> {
                QuickActionDebug.log("unknown type=${operation.type}")
                false
            }
        }
    }

    private fun clickSlot(client: Minecraft, operation: QuickOperation): Boolean {
        val screen = client.screen as? AbstractContainerScreen<*> ?: run {
            abort("当前没有打开容器，已停止")
            return false
        }
        val handler = screen.menu
        val slot = handler.slots.getOrNull(operation.slot) ?: run {
            abort("槽位 ${operation.slot} 不存在，已停止")
            return false
        }
        val manager = client.gameMode ?: run {
            abort("无法点击槽位，已停止")
            return false
        }
        QuickActionDebug.log(
            "click syncId=${handler.containerId} slot=${operation.slot} button=${operation.button}",
        )
        val player = client.player ?: return false
        manager.handleInventoryMouseClick(
            handler.containerId,
            slot.index,
            operation.button.coerceIn(0, 1),
            ClickType.PICKUP,
            player,
        )
        return true
    }


    private fun pollContainerWait(client: Minecraft): Boolean {
        val screen = client.screen as? AbstractContainerScreen<*>
        val title = screen?.title?.string?.trim().orEmpty()
        val expected = waitContainerTitle ?: return true
        val matches = if (waitContainerExact) {
            title.equals(expected, ignoreCase = true)
        } else {
            title.contains(expected, ignoreCase = true)
        }
        if (matches) {
            QuickActionDebug.log("wait-container matched title=$title expected=$expected")
            waitContainerTitle = null
            waitContainerExact = false
            waitContainerDeadline = 0L
            waitContainerLogged = false
            return true
        }
        if (!waitContainerLogged) {
            waitContainerLogged = true
            QuickActionDebug.log("wait-container waiting title=$title expected=$expected")
        }
        if (System.currentTimeMillis() >= waitContainerDeadline) {
            abort("等待容器标题超时：$expected")
            return false
        }
        return false
    }

    fun reset() {
        action = null
        index = 0
        startDelay = 0
        waitTicks = 0
        waitUntilMs = 0L
        waitContainerTitle = null
        waitContainerExact = false
        waitContainerDeadline = 0L
        waitContainerLogged = false
        running = false
    }

    private fun finish(reason: String) {
        QuickActionDebug.log("finish reason=$reason")
        reset()
    }

    private fun abort(message: String) {
        QuickActionDebug.log("abort message=$message")
        notify(message)
        reset()
    }

    /** `[crashs]` prefix: gray brackets, red "crashs", white body. */
    fun notify(message: String) {
        val text = Component.empty()
            .append(Component.literal("[").withStyle(ChatFormatting.GRAY))
            .append(Component.literal("crashs").withStyle(ChatFormatting.RED))
            .append(Component.literal("]").withStyle(ChatFormatting.GRAY))
            .append(Component.literal(message).withStyle(ChatFormatting.WHITE))
        runCatching {
            Minecraft.getInstance().gui.chat.addMessage(text)
        }.onFailure {
            CrashsLegacyMod.LOGGER.info("[crashs]$message")
        }
    }
}
