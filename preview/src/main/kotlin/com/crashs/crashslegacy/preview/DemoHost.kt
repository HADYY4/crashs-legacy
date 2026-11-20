package com.crashs.crashslegacy.preview

import imgui.ImGui
import imgui.flag.ImGuiTabBarFlags
import imgui.type.ImBoolean
import imgui.type.ImInt
import imgui.type.ImString
import com.crashs.crashslegacy.ui.CrashsImGuiSurface
import com.crashs.crashslegacy.ui.CrashsUiHost
import com.crashs.crashslegacy.ui.CrashsUiModule

/**
 * Demo implementation of [CrashsUiHost] shared by the desktop preview and the headless performance
 * benchmark, so both drive [com.crashs.crashslegacy.CrashsImGuiSurface] with the same module
 * bodies and the benchmark measures what the preview shows.
 */
internal class DemoHost(private val verbose: Boolean = true) : CrashsUiHost {

    var shouldClose = false

    private var enabled = true
    private var sound = true
    private val volume = floatArrayOf(0.8f)
    private val combo = ImInt(0)
    private val format = ImInt(0)
    private val text = ImString("", 128)

    override val modules: List<CrashsUiModule> = listOf(
        CrashsUiModule(
            "crashslegacy",
            "击杀反馈",
            "击杀金币提示、HUD、声音与动画设置。",
            "MYSTERIOUS",
            { crashsLegacyDemo() },
            { enabled },
            { enabled = it },
        ),
        CrashsUiModule(
            "coinsinfo",
            "金币统计",
            "会话时长、总击杀、金币与平均收益 HUD。",
            "MYSTERIOUS",
            { coinsDemo() },
            { sound },
            { sound = it },
        ),
        CrashsUiModule(
            "serverdamage",
            "服务器伤害",
            "服务器伤害文本的显示、背景和玩家受伤适配。",
            "MYSTERIOUS",
            { damageDemo() },
            { true },
            { },
        ),
        CrashsUiModule(
            "broadcast",
            "播报管理",
            "本玩家与其他玩家的聊天、BossBar 和音效播报。",
            "MYSTERIOUS",
            { broadcastDemo() },
            { true },
            { },
        ),
    )

    override fun frameTick() = Unit

    override fun configChanged() = Unit

    override fun closeScreen() {
        shouldClose = true
    }

    override fun openExternal(moduleId: String) = note("openExternal($moduleId)")

    override fun openHudEditor() = note("openHudEditor() - 游戏内会打开 HUD 编辑器")

    override fun resetConfig() {
        enabled = true
        sound = true
        volume[0] = 0.8f
        combo.set(0)
        format.set(0)
        note("resetConfig")
    }

    override fun log(message: String) = note(message)

    private fun note(message: String) {
        if (verbose) println(message)
    }

    private fun section(title: String, id: String, body: () -> Unit) {
        if (ImGui.collapsingHeader("$title##$id")) {
            ImGui.indent(12f)
            body()
            ImGui.unindent(12f)
        }
    }

    private fun crashsLegacyDemo() {
        section("基础", "base") {
            ImGui.text("拦截聊天")
            ImGui.sameLine()
            ImGui.checkbox("##intercept", ImBoolean(enabled))
            ImGui.text("正则表达式")
            ImGui.sameLine()
            ImGui.smallButton("保存##pattern")
            ImGui.setNextItemWidth(-1f)
            ImGui.inputText("##pattern", text)
        }
        section("声音", "sound") {
            ImGui.text("音量")
            ImGui.sameLine()
            ImGui.setNextItemWidth(-1f)
            ImGui.sliderFloat("##volume", volume, 0f, 2f, "%.2f")
            ImGui.setNextItemWidth(200f)
            ImGui.combo("连杀音效组", combo, arrayOf("NONE", "COMBO", "VALORANT"))
        }
        section("HUD", "hud") {
            ImGui.checkbox("显示 HUD", ImBoolean(true))
            ImGui.setNextItemWidth(-1f)
            ImGui.sliderFloat("##hudScale", volume, 0.3f, 2f, "%.2f")
        }
    }

    private fun coinsDemo() {
        ImGui.text("缩放")
        ImGui.sameLine()
        ImGui.setNextItemWidth(-1f)
        ImGui.sliderFloat("##coinsScale", volume, 0.5f, 2f, "%.2f")
        ImGui.checkbox("显示总金币", ImBoolean(true))
        ImGui.checkbox("数字滚动", ImBoolean(true))
    }

    private fun damageDemo() {
        ImGui.checkbox("透明背景", ImBoolean(true))
        ImGui.checkbox("移除伤害显示", ImBoolean(false))
        ImGui.checkbox("文字阴影", ImBoolean(true))
        ImGui.checkbox("禁止实体移动", ImBoolean(false))
    }

    private fun broadcastDemo() {
        section("全局", "global") {
            ImGui.checkbox("记录到文件", ImBoolean(true))
            ImGui.setNextItemWidth(200f)
            ImGui.combo("文件格式", format, arrayOf("CSV", "SQLITE"))
        }
        if (ImGui.beginTabBar("##players", ImGuiTabBarFlags.AutoSelectNewTabs)) {
            if (ImGui.beginTabItem("本玩家")) {
                section("关键词装备", "own.keywordCategory") {
                    ImGui.text("关键词")
                    ImGui.sameLine()
                    ImGui.smallButton("保存##ownKeyword")
                    ImGui.setNextItemWidth(-1f)
                    ImGui.inputText("##ownKeyword", text)
                    ImGui.checkbox("显示聊天", ImBoolean(true))
                }
                section("红色装备", "own.redCategory") { ImGui.checkbox("显示 BossBar", ImBoolean(true)) }
                ImGui.endTabItem()
            }
            if (ImGui.beginTabItem("其他玩家")) {
                section("关键词装备", "other.keywordCategory") {
                    ImGui.text("关键词")
                    ImGui.sameLine()
                    ImGui.smallButton("保存##otherKeyword")
                    ImGui.setNextItemWidth(-1f)
                    ImGui.inputText("##otherKeyword", text)
                }
                ImGui.endTabItem()
            }
            ImGui.endTabBar()
        }
    }
}
