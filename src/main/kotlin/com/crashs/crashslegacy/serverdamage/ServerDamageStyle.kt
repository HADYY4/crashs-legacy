package com.crashs.crashslegacy.serverdamage

/**
 * Pure display-state math for the server damage TextDisplays.
 *
 * Kept free of Minecraft types so the "文字阴影 / 背景" switches can be unit tested instead of
 * being verified by eye in game. Bit values mirror `DisplayEntity.Display.TextDisplay` in 1.21.11:
 * `SHADOW_FLAG = 1`, `SEE_THROUGH_FLAG = 2`, `DEFAULT_BACKGROUND_FLAG = 4`.
 */
object ServerDamageStyle {

    const val SHADOW_BIT = 0x01
    const val DEFAULT_BACKGROUND_BIT = 0x04

    /** `INITIAL_BACKGROUND` of a TextDisplay: the backdrop used when the default-bit is set. */
    const val DEFAULT_BACKGROUND_COLOR = 0x40000000

    /** Fully transparent background colour. */
    const val TRANSPARENT_BACKGROUND = 0

    /**
     * Desired display flags for one entity.
     *
     * [baselineFlags] is what the server sent before we touched the entity. Shadow is a hard
     * override in both directions: on forces the bit, off clears it — otherwise a server that
     * already shows a shadow could never be turned off. The transparent-background switch only
     * overrides while it is on; when it is off the server's own default-background bit is put
     * back, so the entity keeps looking exactly like the server intended.
     */
    fun flags(baselineFlags: Int, shadow: Boolean, transparentBackground: Boolean): Int {
        var flags = baselineFlags
        flags = if (shadow) flags or SHADOW_BIT else flags and SHADOW_BIT.inv()
        flags = if (transparentBackground) {
            flags and DEFAULT_BACKGROUND_BIT.inv()
        } else {
            flags or (baselineFlags and DEFAULT_BACKGROUND_BIT)
        }
        return flags
    }

    /** Desired background colour; transparent while the switch is on, otherwise untouched. */
    fun background(baselineBackground: Int, transparentBackground: Boolean): Int =
        if (transparentBackground) TRANSPARENT_BACKGROUND else baselineBackground

    /**
     * Whether a position update for an entity must be dropped.
     *
     * Freezing only applies to entities already classified as damage texts, so every other display
     * entity in the world keeps moving normally.
     */
    fun shouldFreeze(serverDamageEnabled: Boolean, freezeEnabled: Boolean, tracked: Boolean): Boolean =
        serverDamageEnabled && freezeEnabled && tracked
}
