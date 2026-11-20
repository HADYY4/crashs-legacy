package com.crashs.crashslegacy.ui

import com.crashs.crashslegacy.feature.lightning.LightningSoundCatalog

/** One selectable option: [value] is what the config stores, [label] is what the user sees. */
data class ChoiceOption(val value: String, val label: String)

/**
 * Chinese display labels for every multi-choice setting.
 *
 * The config keeps storing the stable English enum names, so existing files and older clients keep
 * working; only the dropdown text is localized. `CrashsUiLabelsTest` checks that every option has a
 * Chinese label and that every enum constant used by the UI is covered.
 */
object CrashsUiLabels {

    val comboGroups = listOf(
        ChoiceOption("NONE", "无"),
        ChoiceOption("CF", "CF 连杀"),
        ChoiceOption("VALORANT_BUBBLEGUM", "无畏契约·泡泡糖"),
        ChoiceOption("VALORANT_CHAMPIONS", "无畏契约·冠军"),
        ChoiceOption("VALORANT_GAIA", "无畏契约·盖亚"),
        ChoiceOption("VALORANT_GAIA_VENGEANCE", "无畏契约·盖亚复仇"),
        ChoiceOption("VALORANT_GLITCHPOP", "无畏契约·故障艺术"),
        ChoiceOption("VALORANT_PRELUDE", "无畏契约·混沌序曲"),
        ChoiceOption("VALORANT_PRIME", "无畏契约·至尊"),
        ChoiceOption("VALORANT_PRIMORDIUM", "无畏契约·原初"),
        ChoiceOption("VALORANT_RADIANT_CRISIS", "无畏契约·辐射危机"),
        ChoiceOption("VALORANT_RGX", "无畏契约·RGX 11z Pro"),
        ChoiceOption("VALORANT_SINGULARITY", "无畏契约·奇点"),
    )

    val postModes = listOf(
        ChoiceOption("LOOP_GROUP", "循环整组"),
        ChoiceOption("LAST_OF_GROUP", "组内最后一个"),
        ChoiceOption("NONE", "不播放"),
        ChoiceOption("SPECIFIC", "指定音效"),
    )

    val postSounds = listOf(
        ChoiceOption("CARD_CRIT", "卡牌·暴击"),
        ChoiceOption("CARD_EXPLOSION", "卡牌·爆炸"),
        ChoiceOption("CARD_HEADSHOT", "卡牌·爆头"),
        ChoiceOption("COLLISION", "碰撞击杀"),
        ChoiceOption("CRIT", "暴击"),
        ChoiceOption("EXPLOSION", "爆炸"),
        ChoiceOption("HEADSHOT_BF1", "爆头（BF1）"),
        ChoiceOption("HEADSHOT_BF5", "爆头（BF5）"),
        ChoiceOption("HEADSHOT_BF6", "爆头（BF6）"),
        ChoiceOption("HEADSHOT_DF", "爆头（DF）"),
        ChoiceOption("HIT", "命中"),
        ChoiceOption("HONOR_HIGH", "荣誉·高"),
        ChoiceOption("HONOR_NORMAL", "荣誉·普通"),
        ChoiceOption("KILL_BF1", "击杀（BF1）"),
        ChoiceOption("KILL_BF5", "击杀（BF5）"),
        ChoiceOption("KILL_BF6", "击杀（BF6）"),
        ChoiceOption("KILL_DF", "击杀（DF）"),
        ChoiceOption("VEHICLE_BF5", "载具击杀（BF5）"),
    )

    val headerStyles = listOf(
        ChoiceOption("FULL", "完整"),
        ChoiceOption("COMPACT", "紧凑"),
        ChoiceOption("COINS_ONLY", "仅金币"),
        ChoiceOption("KILLS_ONLY", "仅击杀"),
        ChoiceOption("HIDDEN", "隐藏"),
    )

    val numberFormats = listOf(
        ChoiceOption("RAW", "原始数字"),
        ChoiceOption("COMMA", "千分位"),
        ChoiceOption("SHORT", "缩写（K/M）"),
    )

    val statsModes = listOf(
        ChoiceOption("TIMED", "限时统计"),
        ChoiceOption("SESSION", "整局统计"),
    )

    val averageUnits = listOf(
        ChoiceOption("MINUTE", "每分钟"),
        ChoiceOption("HOUR", "每小时"),
    )

    val fileFormats = listOf(
        ChoiceOption("CSV", "CSV 文件"),
        ChoiceOption("SQLITE", "SQLite 数据库"),
    )

    val itemTrackerDirections = listOf(
        ChoiceOption("UP", "上（向上展开，新条目在底部）"),
        ChoiceOption("DOWN", "下（向下展开，新条目在顶部）"),
    )

    val lightningSoundModes = listOf(
        ChoiceOption("VANILLA", "原版"),
        ChoiceOption("SILENT", "无音效"),
        ChoiceOption("SPECIFIC", "特定音效"),
    )

    val lightningSounds = LightningSoundCatalog.NAMES.map { ChoiceOption(it, it) }

    val broadcastSounds = listOf(
        ChoiceOption("ENDER_DRAGON", "末影龙"),
        ChoiceOption("WITHER", "凋零"),
        ChoiceOption("EXPERIENCE", "经验"),
        ChoiceOption("OFF", "关闭"),
    )
}
