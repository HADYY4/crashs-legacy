package com.crashs.crashslegacy.config

import com.crashs.crashslegacy.BroadcastConfigMigrator
import com.crashs.crashslegacy.BroadcastPlayerSettings
import com.crashs.crashslegacy.BroadcastSettings
import com.crashs.crashslegacy.deepCopy
import com.crashs.crashslegacy.feature.itemtracker.ItemTrackerParser
import com.crashs.crashslegacy.feature.killfeed.ComboGroup
import com.crashs.crashslegacy.feature.killfeed.KillFeedState
import com.crashs.crashslegacy.feature.killfeed.KillRewardParser
import com.crashs.crashslegacy.feature.killfeed.PostComboMode
import com.crashs.crashslegacy.feature.killfeed.PostComboSound

/**
 * Flat JSON config so the mod has no dependency on a config-screen library.
 */
data class CrashsLegacyConfig(
    var enabled: Boolean = true,
    var intercept: Boolean = true,
    var includeOverlay: Boolean = false,
    var pattern: String = KillRewardParser.DEFAULT_PATTERN,

    var sound: Boolean = true,
    var soundVolume: Float = 1f,
    var soundPitch: Float = 1f,
    var comboGroup: String = ComboGroup.NONE.name,
    var postMode: String = PostComboMode.SPECIFIC.name,
    var specificSound: String = PostComboSound.KILL_DF.name,

    var hud: Boolean = true,
    var hudAlwaysVisible: Boolean = false,
    var hudScale: Float = 1f,
    var hudRight: Boolean = false,
    // Percent of the scaled screen: 0 = anchored edge, 100 = opposite edge.
    var hudX: Float = 50f,
    var hudY: Float = 50f,
    var statsWindowSeconds: Float = 8f,
    var maxRows: Int = 5,
    var entryDurationSeconds: Float = 5f,
    var headerStyle: String = "FULL",
    var background: Boolean = false,
    var hudOutline: Boolean = false,
    var hudTextShadow: Boolean = true,
    var entryPushAnimation: Boolean = true,
    var textRevealAnimation: Boolean = true,
    var showProgress: Boolean = true,
    var showCoins: Boolean = true,
    var showKills: Boolean = true,
    var showSource: Boolean = true,
    var showLevel: Boolean = true,
    var showAmount: Boolean = true,
    var numberFormat: String = "RAW",
    var statsMode: String = "TIMED",
    var rowSpacing: Float = 1f,
    var pendingEvictionAlpha: Float = 0.55f,
    var progressHeight: Float = 2f,
    var amountScroll: Boolean = true,

    var coinColor: Int = 0xFFFFD23C.toInt(),
    var sourceColor: Int = 0xFFFFFFFF.toInt(),
    var levelColor: Int = 0xFFFFDC50.toInt(),
    var headerColor: Int = 0xFFFFFFFF.toInt(),
    var dividerColor: Int = 0x40FFFFFF,
    var backgroundColor: Int = 0xAA08080C.toInt(),
    var outlineColor: Int = 0x40FFFFFF,
    var progressColor: Int = 0xFFFFD23C.toInt(),
    var progressBackground: Int = 0x40FFFFFF,

    var serverDamageEnabled: Boolean = true,
    var serverDamageBackground: Boolean = true,
    var serverDamageRemove: Boolean = false,
    var serverDamageShadow: Boolean = true,
    var serverDamagePlayerHurt: Boolean = true,
    var serverDamageFreeze: Boolean = false,

    var lightningEnabled: Boolean = false,
    var lightningHideFlash: Boolean = false,
    var lightningHideEntity: Boolean = false,
    var lightningRecolor: Boolean = false,
    var lightningColor: Int = 0xFF7DF9FF.toInt(),
    var lightningSoundMode: String = "VANILLA",
    var lightningSound: String = "boykisser",

    var itemTrackerEnabled: Boolean = false,
    var itemTrackerInventoryEnabled: Boolean = false,
    var itemTrackerIntercept: Boolean = false,
    var itemTrackerPattern: String = ItemTrackerParser.DEFAULT_PATTERN,
    var itemTrackerHud: Boolean = true,
    var itemTrackerAlwaysVisible: Boolean = false,
    var itemTrackerHudDirection: String = "UP",
    var itemTrackerShowWarehouse: Boolean = true,
    var itemTrackerScale: Float = 1f,
    var itemTrackerRight: Boolean = false,
    var itemTrackerX: Float = 50f,
    var itemTrackerY: Float = 30f,
    var itemTrackerMaxRows: Int = 8,
    var itemTrackerWindowSeconds: Float = 6f,
    var itemTrackerBackground: Boolean = true,
    var itemTrackerOutline: Boolean = false,
    var itemTrackerTextShadow: Boolean = true,
    var itemTrackerBackgroundColor: Int = 0xAA08080C.toInt(),
    var itemTrackerOutlineColor: Int = 0x40FFFFFF,

    var coinsInfoEnabled: Boolean = false,
    var coinsInfoAlwaysVisible: Boolean = false,
    var coinsInfoScale: Float = 1f,
    var coinsInfoRight: Boolean = false,
    var coinsInfoX: Float = 35f,
    var coinsInfoY: Float = 50f,
    var coinsInfoTwoLines: Boolean = true,
    var coinsInfoScroll: Boolean = true,
    var coinsInfoShowTotal: Boolean = true,
    var coinsInfoShowAverage: Boolean = true,
    var coinsInfoAverageUnit: String = "MINUTE",
    var coinsInfoNumberFormat: String = "RAW",
    var coinsInfoTotalColor: Int = 0xFFFFFFFF.toInt(),
    var coinsInfoAverageColor: Int = 0xFFFFFFFF.toInt(),
    var coinsInfoSessionTimeColor: Int = 0xFFFFFFFF.toInt(),
    var coinsInfoKillsColor: Int = 0xFFFFFFFF.toInt(),
    var coinsInfoDividerColor: Int = 0xFFFFD23C.toInt(),
    var coinsInfoDividerBackground: Int = 0x40FFFFFF,
    var coinsInfoBackground: Boolean = false,
    var coinsInfoBackgroundColor: Int = 0xAA08080C.toInt(),
    var coinsInfoOutline: Boolean = false,
    var coinsInfoOutlineColor: Int = 0x40FFFFFF,
    var coinsInfoTextShadow: Boolean = true,
    var coinsInfoShowSessionTime: Boolean = true,
    var coinsInfoShowKills: Boolean = true,

    var broadcastEnabled: Boolean = false,
    var broadcastHideChat: Boolean = false,
    var broadcastBossBar: Boolean = false,
    var broadcastSeconds: Float = 8f,
    var broadcastWriteFile: Boolean = false,
    var broadcastSound: Boolean = false,
    var broadcastSoundMode: String = "ENDER_DRAGON",

    var broadcast: BroadcastSettings = BroadcastSettings(),
    var defaultsVersion: Int = 0,
)

object CrashsLegacyConfigManager {

    @Volatile
    var config: CrashsLegacyConfig = CrashsLegacyConfig()
        private set

    /** Loads the split config folder, migrating the old single file on first run. */
    fun load() {
        val loaded = runCatching { ConfigStore.load() }.getOrElse {
            ConfigStore.Loaded(CrashsLegacyConfig(), false)
        }
        config = loaded.config
        if (!loaded.present) {
            config.broadcast.migrated = true
            config.defaultsVersion = 1
        }
        var changed = BroadcastConfigMigrator.migrate(config)
        if (config.defaultsVersion < 1) {
            applyDefaultProfile(config)
            config.defaultsVersion = 1
            changed = true
        }
        if (config.pattern == KillRewardParser.LEGACY_DEFAULT_PATTERN ||
            config.pattern == KillRewardParser.PREVIOUS_DEFAULT_PATTERN
        ) {
            config.pattern = KillRewardParser.DEFAULT_PATTERN
            changed = true
        }
        if (changed || !loaded.present) save()
    }

    /** Re-reads the folder after the user edited the JSON files. */
    fun reload() {
        load()
        KillFeedState.reset()
    }

    private fun applyDefaultProfile(config: CrashsLegacyConfig) {
        config.enabled = true
        config.intercept = true
        config.includeOverlay = false
        config.comboGroup = ComboGroup.NONE.name
        config.postMode = PostComboMode.SPECIFIC.name
        config.specificSound = PostComboSound.KILL_DF.name

        config.coinsInfoTwoLines = true
        config.coinsInfoX = 35f
        config.coinsInfoY = 50f
        config.coinsInfoRight = false

        config.itemTrackerEnabled = false
        config.itemTrackerInventoryEnabled = false
        config.itemTrackerIntercept = false
        config.itemTrackerHud = true
        config.itemTrackerHudDirection = "UP"
        config.itemTrackerShowWarehouse = true
        config.itemTrackerScale = 1f
        config.itemTrackerRight = false
        config.itemTrackerX = 50f
        config.itemTrackerY = 30f
        config.itemTrackerMaxRows = 8
        config.itemTrackerWindowSeconds = 6f
        config.itemTrackerBackground = true
        config.hudOutline = false
        config.coinsInfoOutline = false
        config.itemTrackerOutline = false
        config.itemTrackerTextShadow = true
        config.serverDamageEnabled = true
        config.serverDamageBackground = true
        config.serverDamageRemove = false
        config.serverDamageShadow = true
        config.serverDamagePlayerHurt = true
        config.serverDamageFreeze = false

        config.broadcast.enabled = false
        config.broadcast.writeFile = true
        config.broadcast.fileFormat = "CSV"
        config.broadcast.migrated = true
        config.broadcast.own = BroadcastPlayerSettings.default(showChat = true, showBossBar = true)
        config.broadcast.other = BroadcastPlayerSettings.default(showChat = false, showBossBar = true)
    }

    fun apply(newConfig: CrashsLegacyConfig) {
        config = newConfig.deepCopy()
        config.broadcast.migrated = true
        config.defaultsVersion = 1
        save()
    }

    fun save() {
        runCatching { ConfigStore.save(config) }
    }

    fun reset() {
        config = CrashsLegacyConfig()
        save()
        KillFeedState.reset()
    }
}
