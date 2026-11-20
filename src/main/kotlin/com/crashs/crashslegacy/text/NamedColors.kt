package com.crashs.crashslegacy.text

object NamedColors {

    private val named = mapOf(
        "black" to 0xFF000000.toInt(),
        "dark_blue" to 0xFF0000AA.toInt(),
        "dark_green" to 0xFF00AA00.toInt(),
        "dark_aqua" to 0xFF00AAAA.toInt(),
        "dark_red" to 0xFFAA0000.toInt(),
        "dark_purple" to 0xFFAA00AA.toInt(),
        "gold" to 0xFFFFAA00.toInt(),
        "gray" to 0xFFAAAAAA.toInt(),
        "dark_gray" to 0xFF555555.toInt(),
        "blue" to 0xFF5555FF.toInt(),
        "green" to 0xFF55FF55.toInt(),
        "aqua" to 0xFF55FFFF.toInt(),
        "red" to 0xFFFF5555.toInt(),
        "light_purple" to 0xFFFF55FF.toInt(),
        "yellow" to 0xFFFFFF55.toInt(),
        "white" to 0xFFFFFFFF.toInt(),
    )

    fun colorOf(name: String): Int? {
        if (name.startsWith("#") && name.length == 7) {
            return runCatching { 0xFF000000.toInt() or name.substring(1).toInt(16) }.getOrNull()
        }
        return named[name.lowercase()]
    }
}
