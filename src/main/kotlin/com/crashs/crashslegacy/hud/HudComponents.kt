package com.crashs.crashslegacy.hud
import com.crashs.crashslegacy.config.CrashsLegacyConfig

/**
 * One editable HUD element.
 *
 * The editor works against this interface instead of hardcoding the kill-feedback HUD, so adding a
 * third HUD later only needs a new implementation. Each implementation reads and writes the normal
 * config fields, which keeps the module cards and the editor in sync by construction.
 */
interface HudComponent {

    val id: String

    val title: String

    fun isEnabled(config: CrashsLegacyConfig): Boolean

    /** Draw the HUD even while a screen (chat, inventory, container) is open. */
    fun alwaysVisible(config: CrashsLegacyConfig): Boolean

    /** On-screen rectangle in Minecraft's scaled coordinates, or null when nothing can be measured. */
    fun measure(config: CrashsLegacyConfig, screenWidth: Float, screenHeight: Float): HudPlacement.Bounds?

    fun scale(config: CrashsLegacyConfig): Float

    fun setScale(config: CrashsLegacyConfig, value: Float)

    fun position(config: CrashsLegacyConfig): Pair<Float, Float>

    /** Moves the component; percentages are clamped to `0..100` (typing may place it off-screen). */
    fun moveTo(config: CrashsLegacyConfig, percentX: Float, percentY: Float)

    /** True when the component is anchored by its bottom edge (grows upward). */
    fun bottomAnchor(config: CrashsLegacyConfig): Boolean = false

    fun right(config: CrashsLegacyConfig): Boolean

    fun setRight(config: CrashsLegacyConfig, value: Boolean)

    fun reset(config: CrashsLegacyConfig)
}

/** The kill-feedback HUD (`hud / hudScale / hudX / hudY / hudRight`). */
object CrashsLegacyHudComponent : HudComponent {

    override val id = "crashslegacy"
    override val title = "击杀反馈 HUD"

    override fun isEnabled(config: CrashsLegacyConfig) = config.enabled && config.hud

    override fun alwaysVisible(config: CrashsLegacyConfig) = config.hudAlwaysVisible

    override fun measure(config: CrashsLegacyConfig, screenWidth: Float, screenHeight: Float) =
        CrashsLegacyHud.measure(screenWidth, screenHeight, config)

    override fun scale(config: CrashsLegacyConfig) = config.hudScale.coerceIn(MIN_SCALE, MAX_SCALE)
    override fun setScale(config: CrashsLegacyConfig, value: Float) {
        config.hudScale = value.coerceIn(MIN_SCALE, MAX_SCALE)
    }

    override fun position(config: CrashsLegacyConfig) = config.hudX to config.hudY

    override fun moveTo(config: CrashsLegacyConfig, percentX: Float, percentY: Float) {
        config.hudX = percentX.coerceIn(0f, 100f)
        config.hudY = percentY.coerceIn(0f, 100f)
    }

    override fun right(config: CrashsLegacyConfig) = config.hudRight
    override fun setRight(config: CrashsLegacyConfig, value: Boolean) {
        config.hudRight = value
    }

    override fun reset(config: CrashsLegacyConfig) {
        config.hudX = 50f
        config.hudY = 50f
        config.hudScale = 1f
        config.hudRight = false
    }
}

/** The coins-statistics HUD (`coinsInfo*`). */
object CoinsInfoHudComponent : HudComponent {

    override val id = "coinsinfo"
    override val title = "金币统计 HUD"

    override fun isEnabled(config: CrashsLegacyConfig) = config.coinsInfoEnabled

    override fun alwaysVisible(config: CrashsLegacyConfig) = config.coinsInfoAlwaysVisible

    override fun measure(config: CrashsLegacyConfig, screenWidth: Float, screenHeight: Float) =
        CoinsInfoHud.measure(screenWidth, screenHeight, config)

    override fun scale(config: CrashsLegacyConfig) = config.coinsInfoScale.coerceIn(MIN_SCALE, MAX_SCALE)
    override fun setScale(config: CrashsLegacyConfig, value: Float) {
        config.coinsInfoScale = value.coerceIn(MIN_SCALE, MAX_SCALE)
    }

    override fun position(config: CrashsLegacyConfig) = config.coinsInfoX to config.coinsInfoY

    override fun moveTo(config: CrashsLegacyConfig, percentX: Float, percentY: Float) {
        config.coinsInfoX = percentX.coerceIn(0f, 100f)
        config.coinsInfoY = percentY.coerceIn(0f, 100f)
    }

    override fun right(config: CrashsLegacyConfig) = config.coinsInfoRight
    override fun setRight(config: CrashsLegacyConfig, value: Boolean) {
        config.coinsInfoRight = value
    }

    override fun reset(config: CrashsLegacyConfig) {
        config.coinsInfoX = 35f
        config.coinsInfoY = 50f
        config.coinsInfoScale = 1f
        config.coinsInfoRight = false
    }
}

/** Item Tracker HUD (`itemTracker*`). */
object ItemTrackerHudComponent : HudComponent {

    override val id = "itemtracker"
    override val title = "库存管理"

    override fun isEnabled(config: CrashsLegacyConfig) = config.itemTrackerEnabled && config.itemTrackerHud

    override fun alwaysVisible(config: CrashsLegacyConfig) = config.itemTrackerAlwaysVisible

    override fun measure(config: CrashsLegacyConfig, screenWidth: Float, screenHeight: Float) =
        ItemTrackerHud.measure(screenWidth, screenHeight, config)

    override fun scale(config: CrashsLegacyConfig) = config.itemTrackerScale.coerceIn(MIN_SCALE, MAX_SCALE)
    override fun setScale(config: CrashsLegacyConfig, value: Float) {
        config.itemTrackerScale = value.coerceIn(MIN_SCALE, MAX_SCALE)
    }

    override fun position(config: CrashsLegacyConfig) = config.itemTrackerX to config.itemTrackerY

    override fun moveTo(config: CrashsLegacyConfig, percentX: Float, percentY: Float) {
        config.itemTrackerX = percentX.coerceIn(0f, 100f)
        config.itemTrackerY = percentY.coerceIn(0f, 100f)
    }

    override fun bottomAnchor(config: CrashsLegacyConfig) = ItemTrackerHud.isUp(config)

    override fun right(config: CrashsLegacyConfig) = config.itemTrackerRight
    override fun setRight(config: CrashsLegacyConfig, value: Boolean) {
        config.itemTrackerRight = value
    }

    override fun reset(config: CrashsLegacyConfig) {
        config.itemTrackerX = 50f
        config.itemTrackerY = 30f
        config.itemTrackerScale = 1f
        config.itemTrackerRight = false
    }
}

/** Every HUD the editor can edit, in display order. */
val HUD_COMPONENTS: List<HudComponent> =
    listOf(CrashsLegacyHudComponent, CoinsInfoHudComponent, ItemTrackerHudComponent)

private const val MIN_SCALE = 0.5f
private const val MAX_SCALE = 2.5f
