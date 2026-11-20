package com.crashs.crashslegacy

import net.fabricmc.loader.api.FabricLoader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.sql.DriverManager
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import com.crashs.crashslegacy.config.CrashsLegacyConfigManager

object BroadcastQueryRepository {

    private val dbPath get() = FabricLoader.getInstance().configDir.resolve("crashs-legacy-announcements.db")
    private val csvPath get() = FabricLoader.getInstance().configDir.resolve("crashs-legacy-announcements.csv")

    fun load(): List<BroadcastQueryRow> {
        val settings = CrashsLegacyConfigManager.config.broadcast
        if (settings.fileFormat.equals("SQLITE", true) && Files.exists(dbPath)) {
            runCatching { readSqlite() }.getOrNull()?.let { return it }
        }
        if (Files.exists(csvPath)) {
            runCatching { readCsv() }.getOrNull()?.let { return it }
        }
        return emptyList()
    }

    private fun readSqlite(): List<BroadcastQueryRow> {
        Class.forName("org.sqlite.JDBC")
        val result = mutableListOf<BroadcastQueryRow>()
        DriverManager.getConnection("jdbc:sqlite:${dbPath.toAbsolutePath()}").use { db ->
            db.createStatement().use { statement ->
                statement.executeQuery("SELECT * FROM announcements ORDER BY epoch_ms DESC").use { rs ->
                    while (rs.next()) {
                        result += BroadcastQueryRow(
                            id = runCatching { rs.getLong("id") }.getOrDefault(0L),
                            receivedAt = rs.stringOrEmpty("received_at"),
                            epochMs = runCatching { rs.getLong("epoch_ms") }.getOrDefault(0L),
                            type = rs.stringOrEmpty("type"),
                            player = rs.stringOrEmpty("player"),
                            container = rs.stringOrEmpty("container"),
                            mob = rs.stringOrEmpty("mob"),
                            level = rs.intOrZero("level"),
                            itemPrefix = rs.stringOrEmpty("item_prefix"),
                            itemName = rs.stringOrEmpty("item_name"),
                            quality = rs.stringOrEmpty("quality"),
                            itemColor = rs.stringOrEmpty("item_color"),
                            rawText = rs.stringOrEmpty("raw_text"),
                            self = runCatching { rs.getInt("is_self") != 0 }.getOrDefault(false),
                            material = rs.stringOrEmpty("material"),
                            category = rs.stringOrEmpty("category").ifBlank { "其他装备" },
                        )
                    }
                }
            }
        }
        return result
    }

    private fun readCsv(): List<BroadcastQueryRow> {
        val lines = Files.readAllLines(csvPath, StandardCharsets.UTF_8)
        if (lines.isEmpty()) return emptyList()
        val header = parseCsvLine(lines.first()).map { it.trim() }
        fun index(name: String): Int = header.indexOfFirst { it.equals(name, ignoreCase = true) }
        val indices = mapOf(
            "id" to index("id"), "time" to index("time"), "isoTime" to index("isoTime"),
            "type" to index("type"), "player" to index("player"), "self" to index("self"),
            "container" to index("container"), "mob" to index("mob"), "level" to index("level"),
            "material" to index("material"), "prefix" to index("itemPrefix"), "name" to index("itemName"),
            "quality" to index("quality"), "color" to index("itemColor"), "item" to index("itemText"),
            "raw" to index("rawText"), "category" to index("category"),
        )
        return lines.drop(1).mapIndexedNotNull { lineIndex, line ->
            if (line.isBlank()) return@mapIndexedNotNull null
            val cells = parseCsvLine(line)
            fun value(key: String): String = indices[key]?.takeIf { it >= 0 }?.let { cells.getOrNull(it) }.orEmpty()
            BroadcastQueryRow(
                id = value("id").toLongOrNull() ?: (lineIndex + 1L),
                receivedAt = value("isoTime").ifBlank { value("time") },
                epochMs = 0L,
                type = value("type"),
                player = value("player"),
                container = value("container"),
                mob = value("mob"),
                level = value("level").toIntOrNull() ?: 0,
                itemPrefix = value("prefix"),
                itemName = value("name"),
                quality = value("quality"),
                itemColor = value("color"),
                rawText = value("raw"),
                self = value("self").equals("true", true),
                material = value("material"),
                category = value("category").ifBlank { "其他装备" },
            )
        }.reversed()
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var quoted = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            if (c == '"') {
                if (quoted && i + 1 < line.length && line[i + 1] == '"') {
                    current.append('"')
                    i++
                } else {
                    quoted = !quoted
                }
            } else if (c == ',' && !quoted) {
                result += current.toString()
                current.setLength(0)
            } else {
                current.append(c)
            }
            i++
        }
        result += current.toString()
        return result
    }

    private fun java.sql.ResultSet.stringOrEmpty(column: String): String =
        runCatching { getString(column) }.getOrNull().orEmpty()

    private fun java.sql.ResultSet.intOrZero(column: String): Int =
        runCatching { getInt(column) }.getOrDefault(0)
}
