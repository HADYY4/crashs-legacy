package com.crashs.crashslegacy

data class BroadcastQueryRow(
    val id: Long,
    val receivedAt: String,
    val epochMs: Long,
    val type: String,
    val player: String,
    val container: String,
    val mob: String,
    val level: Int,
    val itemPrefix: String,
    val itemName: String,
    val quality: String,
    val itemColor: String,
    val rawText: String,
    val self: Boolean,
    val material: String,
    val category: String,
) {
    val selfText: String get() = if (self) "true" else "false"
    val itemText: String get() = listOf(itemPrefix, itemName).filter { it.isNotBlank() }.joinToString(" ")
}
