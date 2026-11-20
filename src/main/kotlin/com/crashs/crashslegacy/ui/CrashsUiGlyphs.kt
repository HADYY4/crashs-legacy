package com.crashs.crashslegacy.ui

import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.item.ItemStack

/** Item display names used as extra ImGui font glyphs by every Crashs Legacy screen. */
object CrashsUiGlyphs {

    fun itemNames(): String = buildString {
        for (item in BuiltInRegistries.ITEM) {
            append(ItemStack(item).hoverName.string)
        }
    }
}
