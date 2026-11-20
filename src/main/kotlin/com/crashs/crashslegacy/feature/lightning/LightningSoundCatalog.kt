package com.crashs.crashslegacy.feature.lightning

/**
 * The bundled `funnysound` files, grouped by base name.
 *
 * Files ending in `-<number>` are variants of one sound (e.g. `boykisser-1` ... `boykisser-6`);
 * Minecraft randomly picks one of the variants when the group's `SoundEvent` plays. Single files
 * form their own group.
 *
 * This list and `assets/crashs-legacy/sounds.json` are updated together on purpose — there is no
 * runtime directory scan.
 */
object LightningSoundCatalog {

    val FILES: List<String> = listOf(
        "aimbooster",
        "applepay",
        "bonk",
        "boykisser-1", "boykisser-2", "boykisser-3", "boykisser-4", "boykisser-5", "boykisser-6",
        "brick",
        "bring",
        "bump",
        "click-1", "click-2", "click-3",
        "coin",
        "glass-1", "glass-2", "glass-3",
        "magic_squash",
        "meow",
        "moan-1", "moan-2", "moan-3", "moan-4",
        "nya",
        "osu",
        "pop",
        "schoolboy",
        "skeet",
        "slap",
        "soft",
        "squash",
        "tf2-crit",
        "tung",
        "uwu",
    )

    /** Group name -> files, in declaration order. */
    val GROUPS: Map<String, List<String>> = buildMap {
        for (file in FILES) {
            val group = groupOf(file)
            put(group, (get(group) ?: emptyList()) + file)
        }
    }

    val NAMES: List<String> = GROUPS.keys.toList()

    /** The two vanilla lightning sound ids the network mixin reacts to. */
    val LIGHTNING_SOUND_IDS: Set<String> = setOf(
        "entity.lightning_bolt.thunder",
        "entity.lightning_bolt.impact",
    )

    fun groupOf(file: String): String =
        file.replace(Regex("""-\d+$"""), "")

    fun contains(group: String): Boolean = GROUPS.containsKey(group)

    fun filesOf(group: String): List<String> = GROUPS[group] ?: emptyList()
}
