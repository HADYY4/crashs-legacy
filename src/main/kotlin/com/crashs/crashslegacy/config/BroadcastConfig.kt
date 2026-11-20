package com.crashs.crashslegacy
import com.crashs.crashslegacy.config.CrashsLegacyConfig

data class BroadcastCategorySettings(
    var showChat: Boolean = true,
    var showBossBar: Boolean = false,
    var bossBarSeconds: Int = 18,
    var sound: String = "ENDER_DRAGON",
) {
    fun copySettings(): BroadcastCategorySettings = copy()
}

data class BroadcastPlayerSettings(
    var keyword: String = "未命名",
    var keywordCategory: BroadcastCategorySettings = BroadcastCategorySettings(sound = "ENDER_DRAGON"),
    var redCategory: BroadcastCategorySettings = BroadcastCategorySettings(sound = "WITHER"),
    var otherCategory: BroadcastCategorySettings = BroadcastCategorySettings(sound = "EXPERIENCE"),
) {
    fun copySettings(): BroadcastPlayerSettings = copy(
        keywordCategory = keywordCategory.copySettings(),
        redCategory = redCategory.copySettings(),
        otherCategory = otherCategory.copySettings(),
    )

    fun category(name: String): BroadcastCategorySettings = when (name) {
        "KEYWORD" -> keywordCategory
        "RED" -> redCategory
        else -> otherCategory
    }

    companion object {
        fun default(showChat: Boolean, showBossBar: Boolean): BroadcastPlayerSettings =
            BroadcastPlayerSettings(
                keywordCategory = BroadcastCategorySettings(showChat, showBossBar, 18, "ENDER_DRAGON"),
                redCategory = BroadcastCategorySettings(showChat, showBossBar, 18, "WITHER"),
                otherCategory = BroadcastCategorySettings(showChat, showBossBar, 18, "EXPERIENCE"),
            )
    }
}

data class BroadcastSettings(
    var enabled: Boolean = false,
    var own: BroadcastPlayerSettings = BroadcastPlayerSettings.default(showChat = true, showBossBar = true),
    var other: BroadcastPlayerSettings = BroadcastPlayerSettings.default(showChat = false, showBossBar = true),
    var writeFile: Boolean = true,
    var fileFormat: String = "CSV",
    var migrated: Boolean = false,
) {
    fun copySettings(): BroadcastSettings = copy(
        own = own.copySettings(),
        other = other.copySettings(),
    )
}

fun CrashsLegacyConfig.deepCopy(): CrashsLegacyConfig = copy(broadcast = broadcast.copySettings())

object BroadcastConfigMigrator {
    fun migrate(config: CrashsLegacyConfig): Boolean {
        val settings = config.broadcast
        if (settings.migrated) return false

        val category = BroadcastCategorySettings(
            showChat = !config.broadcastHideChat,
            showBossBar = config.broadcastBossBar,
            bossBarSeconds = config.broadcastSeconds.toInt().coerceIn(3, 60),
            sound = if (config.broadcastSound) config.broadcastSoundMode else "OFF",
        )
        val group = BroadcastPlayerSettings(
            keywordCategory = category.copySettings(),
            redCategory = category.copySettings(),
            otherCategory = category.copySettings(),
        )
        settings.own = group.copySettings()
        settings.other = group.copySettings()
        settings.enabled = config.broadcastEnabled
        settings.writeFile = config.broadcastWriteFile
        settings.fileFormat = "CSV"
        settings.migrated = true
        return true
    }
}
