package com.crashs.crashslegacy.config

/**
 * Field-to-file mapping for the split config.
 *
 * Every field of [CrashsLegacyConfig] must appear exactly once. `ConfigSectionsTest` reflects over
 * the data class and fails when a new field is not classified here.
 */
object ConfigSections {

    const val FORMAT_VERSION = 1

    val GENERAL = listOf("defaultsVersion")

    val KILLFEED = listOf(
        "enabled", "intercept", "includeOverlay", "pattern",
        "sound", "soundVolume", "soundPitch", "comboGroup", "postMode", "specificSound",
        "hud", "hudAlwaysVisible", "hudScale", "hudRight", "hudX", "hudY",
        "statsWindowSeconds", "maxRows", "entryDurationSeconds", "headerStyle",
        "background", "hudOutline", "hudTextShadow",
        "entryPushAnimation", "textRevealAnimation", "showProgress",
        "showCoins", "showKills", "showSource", "showLevel", "showAmount",
        "numberFormat", "statsMode", "rowSpacing", "pendingEvictionAlpha",
        "progressHeight", "amountScroll",
        "coinColor", "sourceColor", "levelColor", "headerColor", "dividerColor",
        "backgroundColor", "outlineColor", "progressColor", "progressBackground",
    )

    val COINS = listOf(
        "coinsInfoEnabled", "coinsInfoAlwaysVisible", "coinsInfoScale", "coinsInfoRight", "coinsInfoX", "coinsInfoY",
        "coinsInfoTwoLines", "coinsInfoScroll", "coinsInfoShowTotal", "coinsInfoShowAverage",
        "coinsInfoAverageUnit", "coinsInfoNumberFormat",
        "coinsInfoTotalColor", "coinsInfoAverageColor", "coinsInfoSessionTimeColor",
        "coinsInfoKillsColor", "coinsInfoDividerColor", "coinsInfoDividerBackground",
        "coinsInfoBackground", "coinsInfoBackgroundColor", "coinsInfoOutline",
        "coinsInfoOutlineColor", "coinsInfoTextShadow", "coinsInfoShowSessionTime",
        "coinsInfoShowKills",
    )

    val ITEM_TRACKER = listOf(
        "itemTrackerEnabled", "itemTrackerInventoryEnabled", "itemTrackerIntercept",
        "itemTrackerPattern", "itemTrackerHud", "itemTrackerAlwaysVisible", "itemTrackerHudDirection",
        "itemTrackerShowWarehouse", "itemTrackerScale", "itemTrackerRight",
        "itemTrackerX", "itemTrackerY", "itemTrackerMaxRows", "itemTrackerWindowSeconds",
        "itemTrackerBackground", "itemTrackerOutline", "itemTrackerTextShadow",
        "itemTrackerBackgroundColor", "itemTrackerOutlineColor",
    )

    val BROADCAST = listOf(
        "broadcastEnabled", "broadcastHideChat", "broadcastBossBar", "broadcastSeconds",
        "broadcastWriteFile", "broadcastSound", "broadcastSoundMode", "broadcast",
    )

    val SERVER_DAMAGE = listOf(
        "serverDamageEnabled", "serverDamageBackground", "serverDamageRemove",
        "serverDamageShadow", "serverDamagePlayerHurt", "serverDamageFreeze",
    )

    val LIGHTNING = listOf(
        "lightningEnabled", "lightningHideFlash", "lightningHideEntity", "lightningRecolor",
        "lightningColor", "lightningSoundMode", "lightningSound",
    )

    /** File name (without `.json`) to its fields, in write order. */
    val FILES: Map<String, List<String>> = linkedMapOf(
        "general" to GENERAL,
        "killfeed" to KILLFEED,
        "coins" to COINS,
        "item-tracker" to ITEM_TRACKER,
        "broadcast" to BROADCAST,
        "server-damage" to SERVER_DAMAGE,
        "lightning" to LIGHTNING,
    )

    val ALL_FIELDS: Set<String> = FILES.values.flatten().toSet()

    private val fieldToFile: Map<String, String> =
        FILES.entries.flatMap { (file, fields) -> fields.map { it to file } }.toMap()

    fun fileOf(field: String): String? = fieldToFile[field]
}
