package com.crashs.crashslegacy.command

import com.crashs.crashslegacy.feature.lightning.LightningDebug
import com.crashs.crashslegacy.feature.quickactions.QuickActionDebug
import com.crashs.crashslegacy.serverdamage.ServerDamageDebug
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import com.crashs.crashslegacy.CrashsLegacyMod
import com.crashs.crashslegacy.chat.ChatDebug
import com.crashs.crashslegacy.feature.broadcast.BroadcastManager
import com.crashs.crashslegacy.feature.itemtracker.ItemTrackerDebug
import com.crashs.crashslegacy.ui.CrashsHudEditorScreen
import com.crashs.crashslegacy.ui.CrashsImGuiScreen
import com.crashs.crashslegacy.ui.CrashsUiDebug

/**
 * Client commands. Every configuration surface is the single ImGui screen now; `moduleId` only
 * decides which card is expanded when it opens.
 *
 * Diagnostics live under one namespace, `/crashs debug …`, so the top level stays focused on
 * user-facing actions (`ui`, `hud`).
 */
object CrashsLegacyCommands {

    fun register(dispatcher: CommandDispatcher<FabricClientCommandSource>) {
        dispatcher.register(
            ClientCommandManager.literal("crashs")
                .then(
                    ClientCommandManager.literal("ui").executes {
                        openUi(null)
                        1
                    },
                )
                .then(
                    // Kept as an alias so existing muscle memory still works.
                    ClientCommandManager.literal("imgui").executes {
                        openUi(null)
                        1
                    },
                )
                .then(
                    ClientCommandManager.literal("reload").executes { context ->
                        val screen = Minecraft.getInstance().screen as? CrashsImGuiScreen
                        if (screen != null) screen.reloadFromDisk() else CrashsImGuiScreen.reloadAll()
                        context.source.sendFeedback(Component.literal("[Crashs] 配置已重新加载"))
                        1
                    },
                )
                .then(
                    ClientCommandManager.literal("hud").executes { context ->
                        openHudEditor()
                        context.source.sendFeedback(Component.literal("[HUD] 已打开 HUD 编辑器"))
                        1
                    },
                )
                .then(debugNode()),
        )
        registerUi(dispatcher, "crashslegacy", "crashslegacy")
        registerUi(dispatcher, "damagetext", "serverdamage")
    }

    /** `/crashs debug` and its subcommands. */
    private fun debugNode() =
        ClientCommandManager.literal("debug")
            .executes { context ->
                showDebugHelp(context.source)
                1
            }
            .then(
                ClientCommandManager.literal("chat").executes { context ->
                    toggleChat(context.source)
                    1
                },
            )
            .then(
                ClientCommandManager.literal("item").executes { context ->
                    toggleItem(context.source)
                    1
                },
            )
            .then(
                ClientCommandManager.literal("ui").executes { context ->
                    toggleUi(context.source)
                    1
                },
            )
            .then(
                ClientCommandManager.literal("damage").executes { context ->
                    damage(context.source)
                    1
                },
            )
            .then(
                ClientCommandManager.literal("quick").executes { context ->
                    val on = QuickActionDebug.toggle()
                    context.source.sendFeedback(
                        Component.literal(
                            if (on) {
                                "[QuickActionDebug] 已开启：快捷操作执行过程写入日志（/crashs debug quick 关闭）"
                            } else {
                                "[QuickActionDebug] 已关闭"
                            },
                        ),
                    )
                    1
                },
            )
            .then(
                ClientCommandManager.literal("lightning").executes { context ->
                    val on = LightningDebug.toggle()
                    context.source.sendFeedback(
                        Component.literal(
                            if (on) {
                                "[LightningDebug] 已开启：闪电实体/音效/颜色事件写入日志（/crashs debug lightning 关闭）"
                            } else {
                                "[LightningDebug] 已关闭"
                            },
                        ),
                    )
                    1
                },
            )
            .then(
                ClientCommandManager.literal("loot").then(
                    ClientCommandManager.argument("message", StringArgumentType.greedyString())
                        .executes { context ->
                            loot(context)
                            1
                        },
                ),
            )

    private fun showDebugHelp(source: FabricClientCommandSource) {
        val lines = listOf(
            "[Crashs] 调试命令：",
            "  /crashs debug chat    聊天日志（on/off）",
            "  /crashs debug item    背包日志（on/off）",
            "  /crashs debug ui      界面日志（on/off）",
            "  /crashs debug damage  伤害来源统计（再执行一次输出结果）",
            "  /crashs debug lightning  闪电管理调试日志（on/off）",
            "  /crashs debug quick      快捷操作调试日志（on/off）",
            "  /crashs debug loot <message>  播报解析调试",
            "  当前：chat=${onOff(ChatDebug.enabled)} item=${onOff(ItemTrackerDebug.enabled)} " +
                "ui=${onOff(CrashsUiDebug.enabled)} lightning=${onOff(LightningDebug.enabled)} " +
                "quick=${onOff(QuickActionDebug.enabled)} " +
                "damage=${if (ServerDamageDebug.recording) "记录中" else "空闲"}",
        )
        lines.forEach { source.sendFeedback(Component.literal(it)) }
    }

    private fun toggleChat(source: FabricClientCommandSource) {
        val on = ChatDebug.toggle()
        source.sendFeedback(
            Component.literal(
                if (on) {
                    "[ChatDebug] 已开启：收到聊天/ActionBar 会把文本与样式打印到日志（/crashs debug chat 关闭）"
                } else {
                    "[ChatDebug] 已关闭"
                },
            ),
        )
    }

    private fun toggleItem(source: FabricClientCommandSource) {
        val on = ItemTrackerDebug.toggle()
        source.sendFeedback(
            Component.literal(
                if (on) {
                    "[ItemDebug] 已开启：背包同步包、容器抑制与每次增减都会写入日志（/crashs debug item 关闭）"
                } else {
                    "[ItemDebug] 已关闭"
                },
            ),
        )
    }

    private fun toggleUi(source: FabricClientCommandSource) {
        val on = CrashsUiDebug.toggle()
        source.sendFeedback(
            Component.literal(
                if (on) {
                    "[UiDebug] 已开启：复现问题后把带 [UiDebug] 的日志发我（/crashs debug ui 关闭）"
                } else {
                    "[UiDebug] 已关闭"
                },
            ),
        )
    }

    private fun damage(source: FabricClientCommandSource) {
        // First run arms the counters, second run prints the summary and disarms.
        val lines = if (ServerDamageDebug.recording) ServerDamageDebug.end() else ServerDamageDebug.begin()
        lines.forEach { source.sendFeedback(Component.literal(it)) }
    }

    private fun loot(context: CommandContext<FabricClientCommandSource>) {
        val input = StringArgumentType.getString(context, "message")
        val component = BroadcastManager.parseDebugComponent(input)
        val lines = if (component == null) {
            listOf("[DebugLoot] 文本组件解析失败")
        } else {
            BroadcastManager.debugMessage(component)
        }
        lines.forEach { line -> context.source.sendFeedback(Component.literal(line)) }
    }

    private fun onOff(value: Boolean): String = if (value) "on" else "off"

    private fun registerUi(
        dispatcher: CommandDispatcher<FabricClientCommandSource>,
        name: String,
        moduleId: String,
    ) {
        dispatcher.register(
            ClientCommandManager.literal(name)
                .then(
                    ClientCommandManager.literal("ui").executes {
                        openUi(moduleId)
                        1
                    },
                ),
        )
    }

    private fun openUi(moduleId: String?) {
        CrashsLegacyMod.requestScreen { parent -> CrashsImGuiScreen(parent, moduleId) }
    }

    private fun openHudEditor() {
        CrashsLegacyMod.requestScreen { parent -> CrashsHudEditorScreen(parent) }
    }
}
