package com.crashs.crashslegacy.ui

import imgui.ImGui
import imgui.flag.ImGuiCond
import imgui.flag.ImGuiTabBarFlags
import imgui.flag.ImGuiTabItemFlags
import imgui.type.ImBoolean
import imgui.type.ImInt
import imgui.type.ImString
import com.crashs.crashslegacy.BroadcastCategorySettings
import com.crashs.crashslegacy.BroadcastPlayerSettings
import com.crashs.crashslegacy.CoinsSession
import com.crashs.crashslegacy.config.CrashsImGuiConfigSync
import com.crashs.crashslegacy.config.CrashsLegacyConfig
import com.crashs.crashslegacy.config.CrashsLegacyConfigManager
import com.crashs.crashslegacy.feature.itemtracker.ItemTrackerState
import com.crashs.crashslegacy.ui.theme.CrashsImGuiTheme
import com.crashs.crashslegacy.ui.theme.CrashsThemePalette

/**
 * Direct live binding for the Flat CrashsLegacyConfig.
 *
 * Non-text widgets update the live config immediately. Text widgets keep a local buffer and
 * require the adjacent Save button; closing the screen therefore drops uncommitted text edits.
 */
class CrashsImGuiConfigBinder {

    /** Set by the screen so the broadcast page can open the query screen. */
    var onOpenQuery: (() -> Unit)? = null

    private val config: CrashsLegacyConfig
        get() = CrashsLegacyConfigManager.config

    private val textBuffers = HashMap<String, ImString>()
    private val numericBuffers = HashMap<String, ImString>()
    private val numericErrors = HashSet<String>()
    private val colorBuffers = HashMap<String, FloatArray>()
    private var idPrefix = "root"
    private val switchProgress = HashMap<String, Float>()
    private var broadcastTabRestored = false

    private companion object {
        const val BROADCAST_TAB_KEY = "broadcast.players"
    }

    private fun key(label: String): String = "$idPrefix::$label"

    /** Drops every cached widget buffer, e.g. after the config was reset to its defaults. */
    fun resetBuffers() {
        textBuffers.clear()
        numericBuffers.clear()
        numericErrors.clear()
        colorBuffers.clear()
        switchProgress.clear()
        broadcastTabRestored = false
    }

    fun drawCrashsLegacy() {
        section("基础", "crashslegacy.base") {
            bool("拦截聊天", { config.intercept }) { config.intercept = it }
            bool("包含 ActionBar", { config.includeOverlay }) { config.includeOverlay = it }
            textRow("正则表达式", { config.pattern }, { config.pattern = it }, { value ->
                if (value.isBlank()) null else runCatching { Regex(value) }.exceptionOrNull()?.let { "正则无效" }
            })
        }
        section("声音", "crashslegacy.sound") {
            bool("启用声音", { config.sound }) { config.sound = it }
            float("音量", { config.soundVolume }, 0f, 2f, "%.2f") { config.soundVolume = it }
            float("音高", { config.soundPitch }, 0.5f, 2f, "%.2f") { config.soundPitch = it }
            choice("连杀音效组", CrashsUiLabels.comboGroups, config.comboGroup) { config.comboGroup = it }
            choice("收尾模式", CrashsUiLabels.postModes, config.postMode) { config.postMode = it }
            choice("指定音效", CrashsUiLabels.postSounds, config.specificSound) { config.specificSound = it }
        }
        section("HUD", "crashslegacy.hud") {
            bool("显示 HUD", { config.hud }) { config.hud = it }
            bool("始终显示", { config.hudAlwaysVisible }) { config.hudAlwaysVisible = it }
            float("整体缩放", { config.hudScale }, 0.3f, 2f, "%.2f") { config.hudScale = it }
            bool("右侧锚点", { config.hudRight }) { config.hudRight = it }
            float("X 位置", { config.hudX }, 0f, 100f, "%.1f") { config.hudX = it }
            float("Y 位置", { config.hudY }, 0f, 100f, "%.1f") { config.hudY = it }
            float("统计窗口", { config.statsWindowSeconds }, 1f, 600f, "%.1f") { config.statsWindowSeconds = it }
            intSlider("最大行数", { config.maxRows }, 1, 20) { config.maxRows = it }
            float("条目持续时间", { config.entryDurationSeconds }, 1f, 60f, "%.1f") { config.entryDurationSeconds = it }
            float("行间距", { config.rowSpacing }, 0f, 8f, "%.1f") { config.rowSpacing = it }
            float("待淘汰透明度", { config.pendingEvictionAlpha }, 0.1f, 1f, "%.2f") { config.pendingEvictionAlpha = it }
            float("进度条高度", { config.progressHeight }, 1f, 8f, "%.1f") { config.progressHeight = it }
        }
        section("样式", "crashslegacy.style") {
            choice("头部样式", CrashsUiLabels.headerStyles, config.headerStyle) { config.headerStyle = it }
            choice("数字格式", CrashsUiLabels.numberFormats, config.numberFormat) { config.numberFormat = it }
            choice("统计模式", CrashsUiLabels.statsModes, config.statsMode) { config.statsMode = it }
            bool("显示金币", { config.showCoins }) { config.showCoins = it }
            bool("显示击杀", { config.showKills }) { config.showKills = it }
            bool("显示来源", { config.showSource }) { config.showSource = it }
            bool("显示等级", { config.showLevel }) { config.showLevel = it }
            bool("显示金额", { config.showAmount }) { config.showAmount = it }
            bool("显示进度条", { config.showProgress }) { config.showProgress = it }
            bool("背景", { config.background }) { config.background = it }
            bool("边框", { config.hudOutline }) { config.hudOutline = it }
            bool("文字阴影", { config.hudTextShadow }) { config.hudTextShadow = it }
            bool("下移动画", { config.entryPushAnimation }) { config.entryPushAnimation = it }
            bool("逐字显示", { config.textRevealAnimation }) { config.textRevealAnimation = it }
            bool("金额滚动", { config.amountScroll }) { config.amountScroll = it }
        }
        section("颜色", "crashslegacy.colors") {
            color("金币颜色", { config.coinColor }) { config.coinColor = it }
            color("来源颜色", { config.sourceColor }) { config.sourceColor = it }
            color("等级颜色", { config.levelColor }) { config.levelColor = it }
            color("头部颜色", { config.headerColor }) { config.headerColor = it }
            color("分隔线颜色", { config.dividerColor }) { config.dividerColor = it }
            color("背景颜色", { config.backgroundColor }) { config.backgroundColor = it }
            color("边框颜色", { config.outlineColor }) { config.outlineColor = it }
            color("进度颜色", { config.progressColor }) { config.progressColor = it }
            color("进度背景", { config.progressBackground }) { config.progressBackground = it }
        }
    }

    fun drawCoinsInfo() {
        // Sits right under the module description, where the old configuration screen had it.
        actionRow("重置数据", "重置##coins-reset") { CoinsSession.reset() }
        section("HUD", "coins.hud") {
            float("缩放", { config.coinsInfoScale }, 0.5f, 2f, "%.2f") { config.coinsInfoScale = it }
            bool("右侧锚点", { config.coinsInfoRight }) { config.coinsInfoRight = it }
            float("X 位置", { config.coinsInfoX }, 0f, 100f, "%.1f") { config.coinsInfoX = it }
            float("Y 位置", { config.coinsInfoY }, 0f, 100f, "%.1f") { config.coinsInfoY = it }
            bool("始终显示", { config.coinsInfoAlwaysVisible }) { config.coinsInfoAlwaysVisible = it }
        }
        section("样式", "coins.style") {
            bool("双行显示", { config.coinsInfoTwoLines }) { config.coinsInfoTwoLines = it }
            bool("数字滚动", { config.coinsInfoScroll }) { config.coinsInfoScroll = it }
            bool("显示总金币", { config.coinsInfoShowTotal }) { config.coinsInfoShowTotal = it }
            bool("显示平均值", { config.coinsInfoShowAverage }) { config.coinsInfoShowAverage = it }
            bool("显示会话时间", { config.coinsInfoShowSessionTime }) { config.coinsInfoShowSessionTime = it }
            bool("显示总击杀", { config.coinsInfoShowKills }) { config.coinsInfoShowKills = it }
            choice("平均单位", CrashsUiLabels.averageUnits, config.coinsInfoAverageUnit) { config.coinsInfoAverageUnit = it }
            choice("数字格式", CrashsUiLabels.numberFormats, config.coinsInfoNumberFormat) { config.coinsInfoNumberFormat = it }
            bool("背景", { config.coinsInfoBackground }) { config.coinsInfoBackground = it }
            bool("边框", { config.coinsInfoOutline }) { config.coinsInfoOutline = it }
            bool("文字阴影", { config.coinsInfoTextShadow }) { config.coinsInfoTextShadow = it }
        }
        section("颜色", "coins.colors") {
            color("总金币颜色", { config.coinsInfoTotalColor }) { config.coinsInfoTotalColor = it }
            color("平均值颜色", { config.coinsInfoAverageColor }) { config.coinsInfoAverageColor = it }
            color("会话时间颜色", { config.coinsInfoSessionTimeColor }) { config.coinsInfoSessionTimeColor = it }
            color("总击杀颜色", { config.coinsInfoKillsColor }) { config.coinsInfoKillsColor = it }
            color("分隔线颜色", { config.coinsInfoDividerColor }) { config.coinsInfoDividerColor = it }
            color("分隔线背景", { config.coinsInfoDividerBackground }) { config.coinsInfoDividerBackground = it }
            color("背景颜色", { config.coinsInfoBackgroundColor }) { config.coinsInfoBackgroundColor = it }
            color("边框颜色", { config.coinsInfoOutlineColor }) { config.coinsInfoOutlineColor = it }
        }
    }

    fun drawServerDamage() {
        bool("透明背景", { config.serverDamageBackground }) { config.serverDamageBackground = it }
        bool("移除伤害显示", { config.serverDamageRemove }) { config.serverDamageRemove = it }
        bool("文字阴影", { config.serverDamageShadow }) { config.serverDamageShadow = it }
        bool("玩家受伤文本（-nn.nn）", { config.serverDamagePlayerHurt }) { config.serverDamagePlayerHurt = it }
        bool("固定住伤害显示文本", { config.serverDamageFreeze }) { config.serverDamageFreeze = it }
    }

    fun drawLightning() {
        section("基础", "lightning.base") {
            bool("隐藏天空闪烁", { config.lightningHideFlash }) { config.lightningHideFlash = it }
            bool("隐藏闪电实体", { config.lightningHideEntity }) { config.lightningHideEntity = it }
        }
        section("颜色", "lightning.color") {
            bool("修改闪电颜色", { config.lightningRecolor }) { config.lightningRecolor = it }
            ImGui.beginDisabled(!config.lightningRecolor)
            color("闪电颜色", { config.lightningColor }) { config.lightningColor = it }
            ImGui.endDisabled()
        }
        section("音效", "lightning.sound") {
            choice("音效模式", CrashsUiLabels.lightningSoundModes, config.lightningSoundMode) {
                config.lightningSoundMode = it
            }
            ImGui.beginDisabled(config.lightningSoundMode != "SPECIFIC")
            choice("特定音效", CrashsUiLabels.lightningSounds, config.lightningSound) {
                config.lightningSound = it
            }
            ImGui.endDisabled()
        }
    }

    fun drawItemTracker() {
        actionRow("重置数据", "重置##itemtracker-reset") { ItemTrackerState.reset() }
        section("基础", "itemtracker.base") {
            textRow("库存正则", { config.itemTrackerPattern }, { config.itemTrackerPattern = it }, { value ->
                if (value.isBlank()) null else runCatching { Regex(value) }.exceptionOrNull()?.let { "正则无效" }
            })
            bool("拦截聊天", { config.itemTrackerIntercept }) { config.itemTrackerIntercept = it }
            bool("管理背包", { config.itemTrackerInventoryEnabled }) { config.itemTrackerInventoryEnabled = it }
            bool("显示仓库名", { config.itemTrackerShowWarehouse }) { config.itemTrackerShowWarehouse = it }
        }
        section("HUD", "itemtracker.hud") {
            bool("显示 HUD", { config.itemTrackerHud }) { config.itemTrackerHud = it }
            bool("始终显示", { config.itemTrackerAlwaysVisible }) { config.itemTrackerAlwaysVisible = it }
            choice("HUD 方向", CrashsUiLabels.itemTrackerDirections, config.itemTrackerHudDirection) {
                config.itemTrackerHudDirection = it
            }
            float("整体缩放", { config.itemTrackerScale }, 0.5f, 2.5f, "%.2f") { config.itemTrackerScale = it }
            bool("右侧锚点", { config.itemTrackerRight }) { config.itemTrackerRight = it }
            float("X 位置", { config.itemTrackerX }, 0f, 100f, "%.1f") { config.itemTrackerX = it }
            float("Y 位置", { config.itemTrackerY }, 0f, 100f, "%.1f") { config.itemTrackerY = it }
            intSlider("最大行数", { config.itemTrackerMaxRows }, 1, 20) { config.itemTrackerMaxRows = it }
            float("窗口秒数", { config.itemTrackerWindowSeconds }, 1f, 60f, "%.1f") {
                config.itemTrackerWindowSeconds = it
            }
        }
        section("样式", "itemtracker.style") {
            bool("背景", { config.itemTrackerBackground }) { config.itemTrackerBackground = it }
            bool("描边", { config.itemTrackerOutline }) { config.itemTrackerOutline = it }
            bool("文字阴影", { config.itemTrackerTextShadow }) { config.itemTrackerTextShadow = it }
        }
        section("颜色", "itemtracker.colors") {
            color("背景颜色", { config.itemTrackerBackgroundColor }) { config.itemTrackerBackgroundColor = it }
            color("边框颜色", { config.itemTrackerOutlineColor }) { config.itemTrackerOutlineColor = it }
        }
    }

    fun drawBroadcast() {
        val settings = config.broadcast
        section("全局", "broadcast.global") {
            bool("记录到文件", { settings.writeFile }) { settings.writeFile = it }
            choice("文件格式", CrashsUiLabels.fileFormats, settings.fileFormat) { settings.fileFormat = it }
            onOpenQuery?.let { open ->
                actionRow("播报查询", "打开##broadcast-query") { open() }
            }
        }
        // Restore the tab that was open last time. ImGui auto-selects the first tab, so the stored
        // one has to be forced once, then the user's clicks take over from there.
        val storedTab = CrashsImGuiUiState.selectedTab(BROADCAST_TAB_KEY, 0)
        if (ImGui.beginTabBar("##broadcastPlayers", ImGuiTabBarFlags.AutoSelectNewTabs)) {
            var ownVisible = false
            var otherVisible = false
            val ownFlags = if (!broadcastTabRestored && storedTab == 0) ImGuiTabItemFlags.SetSelected else 0
            if (ImGui.beginTabItem("本玩家", null, ownFlags)) {
                ownVisible = true
                drawBroadcastPlayer("broadcast.own", settings.own)
                ImGui.endTabItem()
            }
            val otherFlags = if (!broadcastTabRestored && storedTab == 1) ImGuiTabItemFlags.SetSelected else 0
            if (ImGui.beginTabItem("其他玩家", null, otherFlags)) {
                otherVisible = true
                drawBroadcastPlayer("broadcast.other", settings.other)
                ImGui.endTabItem()
            }
            ImGui.endTabBar()
            if (ownVisible || otherVisible) {
                broadcastTabRestored = true
                val current = if (otherVisible) 1 else 0
                if (current != storedTab) CrashsImGuiUiState.setSelectedTab(BROADCAST_TAB_KEY, current)
            }
        }
    }

    /**
     * The tab bar already labels the group, so the settings start directly with the keyword
     * category instead of nesting the whole group in a second collapsing header with the same
     * title. The keyword itself lives inside that section (two-line layout), which removes an extra
     * expand node.
     */
    private fun drawBroadcastPlayer(id: String, group: BroadcastPlayerSettings) {
        section("关键词装备", "$id.keywordCategory") {
            textRow("关键词", { group.keyword }, { group.keyword = it }, { value ->
                if (value.isBlank()) "关键词不能为空" else null
            })
            ImGui.separator()
            broadcastCategory(group.keywordCategory)
        }
        section("红色装备", "$id.redCategory") {
            broadcastCategory(group.redCategory)
        }
        section("其他装备", "$id.otherCategory") {
            broadcastCategory(group.otherCategory)
        }
    }

    private fun broadcastCategory(category: BroadcastCategorySettings) {
        bool("显示聊天", { category.showChat }) { category.showChat = it }
        bool("显示 BossBar", { category.showBossBar }) { category.showBossBar = it }
        intSlider("BossBar 秒数", { category.bossBarSeconds }, 3, 60) { category.bossBarSeconds = it }
        choice("音效", CrashsUiLabels.broadcastSounds, category.sound) { category.sound = it }
    }

    private fun section(title: String, id: String, block: () -> Unit) {
        val open = CrashsImGuiUiState.isOpen(id)
        ImGui.setNextItemOpen(open, ImGuiCond.Always)
        val expanded = ImGui.collapsingHeader("$title##$id")
        if (expanded != open) {
            CrashsImGuiUiState.setOpen(id, expanded)
        }
        if (expanded) {
            val previousPrefix = idPrefix
            idPrefix = id
            ImGui.pushID(id)
            ImGui.indent(12f)
            block()
            ImGui.unindent(12f)
            ImGui.popID()
            idPrefix = previousPrefix
        }
    }

    /** Label on the left, action button on the right; used for resets and screen jumps. */
    private fun actionRow(label: String, button: String, onClick: () -> Unit) {
        ImGui.text(label)
        ImGui.sameLine()
        val style = ImGui.getStyle()
        val width = ImGui.calcTextSize(button.substringBefore("##")).x + style.getFramePaddingX() * 2f
        val available = ImGui.getContentRegionAvailX()
        if (available > width) {
            ImGui.setCursorPosX(ImGui.getCursorPosX() + available - width)
        }
        if (ImGui.button(button)) {
            onClick()
        }
    }

    private fun bool(label: String, get: () -> Boolean, set: (Boolean) -> Unit) {
        val key = key(label)
        val value = get()
        val dt = ImGui.getIO().deltaTime.coerceIn(0f, 0.1f)
        val target = if (value) 1f else 0f
        val current = switchProgress.getOrDefault(key, target)
        val progress = current + (target - current) * (1f - kotlin.math.exp(-dt * 18f))
        switchProgress[key] = progress

        ImGui.text(label)
        ImGui.sameLine()
        val remaining = ImGui.getContentRegionAvailX()
        if (remaining > 44f) {
            ImGui.setCursorPosX(ImGui.getCursorPosX() + remaining - 44f)
        }
        val x = ImGui.getCursorScreenPosX()
        val y = ImGui.getCursorScreenPosY() + 2f
        val clicked = ImGui.invisibleButton("##switch$key", 42f, 20f)
        val drawList = ImGui.getWindowDrawList()
        val track = CrashsThemePalette.imU32(if (progress > 0.5f) CrashsImGuiTheme.PRIMARY else CrashsImGuiTheme.SWITCH_TRACK_OFF)
        drawList.addRectFilled(x, y, x + 42f, y + 20f, track, 10f)
        val knobX = x + 10f + 22f * progress
        drawList.addCircleFilled(
            knobX,
            y + 10f,
            8f,
            CrashsThemePalette.imU32(if (progress > 0.5f) CrashsImGuiTheme.SWITCH_KNOB_ON else CrashsImGuiTheme.SWITCH_KNOB_OFF),
        )
        if (clicked) {
            set(!value)
            CrashsImGuiConfigSync.touch()
        }
    }

    private fun float(
        label: String,
        get: () -> Float,
        min: Float,
        max: Float,
        format: String,
        set: (Float) -> Unit,
    ) {
        val key = key(label)
        val values = floatArrayOf(get())
        val buffer = numericBuffers.getOrPut(key) { ImString(formatValue(get(), format), 24) }

        numericRow(label, key)
        val entered = ImGui.inputText("##input$key", buffer)
        if (entered || ImGui.isItemDeactivatedAfterEdit()) {
            val parsed = buffer.get().replace(',', '.').toFloatOrNull()
            if (parsed == null) {
                numericErrors += key
            } else {
                numericErrors -= key
                val clamped = parsed.coerceIn(min, max)
                set(clamped)
                CrashsImGuiConfigSync.touch()
                buffer.set(formatValue(clamped, format))
                values[0] = clamped
            }
        }

        ImGui.setNextItemWidth(-1f)
        if (ImGui.sliderFloat("##slider$key", values, min, max, format)) {
            set(values[0])
            CrashsImGuiConfigSync.touch()
            buffer.set(formatValue(values[0], format))
            numericErrors -= key
        }
    }

    private fun intSlider(label: String, get: () -> Int, min: Int, max: Int, set: (Int) -> Unit) {
        val key = key(label)
        val values = intArrayOf(get())
        val buffer = numericBuffers.getOrPut(key) { ImString(get().toString(), 16) }

        numericRow(label, key)
        val entered = ImGui.inputText("##input$key", buffer)
        if (entered || ImGui.isItemDeactivatedAfterEdit()) {
            val parsed = buffer.get().toIntOrNull()
            if (parsed == null) {
                numericErrors += key
            } else {
                numericErrors -= key
                val clamped = parsed.coerceIn(min, max)
                set(clamped)
                CrashsImGuiConfigSync.touch()
                buffer.set(clamped.toString())
                values[0] = clamped
            }
        }

        ImGui.setNextItemWidth(-1f)
        if (ImGui.sliderInt("##slider$key", values, min, max)) {
            set(values[0])
            CrashsImGuiConfigSync.touch()
            buffer.set(values[0].toString())
            numericErrors -= key
        }
    }

    private fun choice(label: String, options: List<ChoiceOption>, current: String, set: (String) -> Unit) {
        val index = options.indexOfFirst { it.value.equals(current, ignoreCase = true) }.coerceAtLeast(0)
        val selected = ImInt(index)
        ImGui.setNextItemWidth(-1f)
        if (ImGui.combo(label, selected, options.map { it.label }.toTypedArray())) {
            set(options[selected.get().coerceIn(options.indices)].value)
            CrashsImGuiConfigSync.touch()
        }
    }

    /**
     * Two-line text field — the layout every text setting uses from now on:
     *
     * ```
     * 名称                        [保存]
     * [文本框本体                      ]
     * ```
     *
     * The save button is right-aligned on the label line, and its width is derived from the current
     * font. Previously the row reserved a hardcoded 70px for it, which the button outgrew once the
     * UI font reached 24px (36px of text + 50px of padding = 86px), so it was clipped by the card.
     */
    private fun textRow(
        label: String,
        get: () -> String,
        set: (String) -> Unit,
        validate: (String) -> String? = { null },
    ) {
        val key = key(label)
        val buffer = textBuffers.getOrPut(key) { ImString(get(), 2048) }
        val error = validate(buffer.get())
        val save = labelRow(label, "保存##$key")
        ImGui.setNextItemWidth(-1f)
        ImGui.inputText("##text$key", buffer)
        if (save && error == null) {
            set(buffer.get())
            CrashsImGuiConfigSync.touch()
        }
        if (error != null) {
            textColoredArgb(CrashsImGuiTheme.ERROR, error)
        }
    }

    /** Draws [label] on the left and [button] right-aligned on the same line; returns the click. */
    private fun labelRow(label: String, button: String): Boolean {
        ImGui.text(label)
        ImGui.sameLine()
        val style = ImGui.getStyle()
        val width = ImGui.calcTextSize(button.substringBefore("##")).x + style.getFramePaddingX() * 2f
        val available = ImGui.getContentRegionAvailX()
        if (available > width) {
            ImGui.setCursorPosX(ImGui.getCursorPosX() + available - width)
        }
        return ImGui.button(button)
    }

    /**
     * Width of a numeric input that fits the current font: four digits, both paddings, and a little
     * breathing room on the right. The old hardcoded 64px left only ~14px of text space at 24px, so
     * the value scrolled and looked pushed against the right edge.
     */
    private fun numericWidth(): Float = CrashsImGuiFields.numericWidth()

    /** Label on the left, numeric input right-aligned with [numericWidth]; error marker in front. */
    /** Label on the left, numeric input right-aligned with [numericWidth]; error marker in front. */
    private fun numericRow(label: String, key: String) {
        val width = numericWidth()
        if (key in numericErrors) {
            textColoredArgb(CrashsImGuiTheme.ERROR, "!")
            ImGui.sameLine()
        }
        ImGui.text(label)
        ImGui.sameLine()
        val remaining = ImGui.getContentRegionAvailX()
        if (remaining > width) {
            ImGui.setCursorPosX(ImGui.getCursorPosX() + remaining - width)
        }
        ImGui.setNextItemWidth(width)
    }

    /** Draws theme-colored text without hardcoding float components at the call site. */
    private fun textColoredArgb(argb: Int, message: String) = CrashsImGuiFields.colorText(argb, message)

    private fun color(label: String, get: () -> Int, set: (Int) -> Unit) {
        val key = key(label)
        val rgba = colorBuffers.getOrPut(key) { argbToRgba(get()) }
        if (ImGui.colorEdit4(label, rgba)) {
            set(rgbaToArgb(rgba))
            CrashsImGuiConfigSync.touch()
        }
    }

    private fun formatValue(value: Float, format: String): String =
        if (format.contains("f")) String.format(java.util.Locale.ROOT, format, value) else value.toString()

    private fun argbToRgba(argb: Int): FloatArray = floatArrayOf(
        ((argb ushr 16) and 0xFF) / 255f,
        ((argb ushr 8) and 0xFF) / 255f,
        (argb and 0xFF) / 255f,
        ((argb ushr 24) and 0xFF) / 255f,
    )

    private fun rgbaToArgb(rgba: FloatArray): Int {
        val a = (rgba[3].coerceIn(0f, 1f) * 255f).toInt()
        val r = (rgba[0].coerceIn(0f, 1f) * 255f).toInt()
        val g = (rgba[1].coerceIn(0f, 1f) * 255f).toInt()
        val b = (rgba[2].coerceIn(0f, 1f) * 255f).toInt()
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }
}
