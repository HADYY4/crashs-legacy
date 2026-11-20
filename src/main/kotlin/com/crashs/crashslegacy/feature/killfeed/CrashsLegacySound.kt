package com.crashs.crashslegacy.feature.killfeed

import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.sounds.SoundEvent
import net.minecraft.resources.Identifier
import com.crashs.crashslegacy.CrashsLegacyMod

enum class ComboGroup(val files: List<String>) {
    NONE(emptyList()),
    CF((1..6).map { "combokillsound_${it}_cf" }),
    VALORANT_BUBBLEGUM((1..5).map { "valorant_bubblegumdeathwishkill_$it" }),
    VALORANT_CHAMPIONS((1..5).map { "valorant_champions2021kill_$it" }),
    VALORANT_GAIA((1..5).map { "valorant_gaiakill_$it" }),
    VALORANT_GAIA_VENGEANCE((1..5).map { "valorant_gaiavengeancekill_$it" }),
    VALORANT_GLITCHPOP((1..5).map { "valorant_glitchpopkill_$it" }),
    VALORANT_PRELUDE((1..5).map { "valorant_preludetochaoskill_$it" }),
    VALORANT_PRIME((1..5).map { "valorant_primekill_$it" }),
    VALORANT_PRIMORDIUM((1..5).map { "valorant_primordiumkill_$it" }),
    VALORANT_RADIANT_CRISIS((1..5).map { "valorant_radiantcrisis001kill_$it" }),
    VALORANT_RGX((1..5).map { "valorant_rgx11zprokill_$it" }),
    VALORANT_SINGULARITY((1..5).map { "valorant_singularitykill_$it" });

    companion object {
        fun byName(value: String): ComboGroup =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: CF
    }
}

enum class PostComboMode {
    LOOP_GROUP,
    LAST_OF_GROUP,
    NONE,
    SPECIFIC;

    companion object {
        fun byName(value: String): PostComboMode =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: LAST_OF_GROUP
    }
}

enum class PostComboSound(val baseName: String) {
    CARD_CRIT("cardkillsound_crit_cs"),
    CARD_EXPLOSION("cardkillsound_explosion_cs"),
    CARD_HEADSHOT("cardkillsound_headshot_cs"),
    COLLISION("collisionkillsound_df"),
    CRIT("critkillsound_df"),
    EXPLOSION("explosionkillsound_df"),
    HEADSHOT_BF1("headshotkillsound_bf1"),
    HEADSHOT_BF5("headshotkillsound_bf5"),
    HEADSHOT_BF6("headshotkillsound_bf6"),
    HEADSHOT_DF("headshotkillsound_df"),
    HIT("hitsound_df"),
    HONOR_HIGH("honorkillsound_high"),
    HONOR_NORMAL("honorkillsound_normal"),
    KILL_BF1("killsound_bf1"),
    KILL_BF5("killsound_bf5"),
    KILL_BF6("killsound_bf6"),
    KILL_DF("killsound_df"),
    VEHICLE_BF5("vehiclekillsound_bf5");

    companion object {
        fun byName(value: String): PostComboSound =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: KILL_DF
    }
}

object CrashsLegacySoundPlayer {

    private const val PLAYBACK_COOLDOWN_MS = 10L
    private var lastPlayedAt = 0L
    private val sounds = HashMap<String, SoundEvent>()

    fun play(
        comboCount: Int,
        group: ComboGroup,
        postMode: PostComboMode,
        specific: PostComboSound,
        volume: Float = 1f,
        pitch: Float = 1f,
    ) {
        val now = System.currentTimeMillis()
        val baseName = resolve(comboCount, group, postMode, specific) ?: return
        if (now - lastPlayedAt < PLAYBACK_COOLDOWN_MS) return
        lastPlayedAt = now

        Minecraft.getInstance().soundManager.play(
            // 1.21.11 signature: ui(sound, pitch, volume).
            net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                sound(baseName),
                pitch.coerceIn(0.5f, 2f),
                volume.coerceIn(0f, 1f),
            ),
        )
    }

    fun reset() {
        lastPlayedAt = 0L
    }

    private fun resolve(
        comboCount: Int,
        group: ComboGroup,
        postMode: PostComboMode,
        specific: PostComboSound,
    ): String? {
        if (group == ComboGroup.NONE) {
            return if (postMode == PostComboMode.SPECIFIC) specific.baseName else null
        }

        val files = group.files
        if (files.isEmpty()) return null
        if (comboCount <= files.size) return files[comboCount - 1]

        return when (postMode) {
            PostComboMode.LOOP_GROUP -> files[(comboCount - 1) % files.size]
            PostComboMode.LAST_OF_GROUP -> files.last()
            PostComboMode.NONE -> null
            PostComboMode.SPECIFIC -> specific.baseName
        }
    }

    private fun sound(baseName: String): SoundEvent = sounds.getOrPut(baseName) {
        SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath(CrashsLegacyMod.MOD_ID, "killfeed.$baseName"))
    }
}
