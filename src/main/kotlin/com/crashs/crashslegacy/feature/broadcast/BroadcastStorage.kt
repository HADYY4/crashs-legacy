package com.crashs.crashslegacy

import net.fabricmc.loader.api.FabricLoader
import org.slf4j.LoggerFactory
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.sql.Connection
import java.sql.DriverManager
import com.crashs.crashslegacy.feature.broadcast.BroadcastRecord

object BroadcastStorage {

    private val logger = LoggerFactory.getLogger("crashs-legacy-announcements")
    private var connection: Connection? = null

    private const val CSV_HEADER =
        "time,isoTime,type,player,self,container,mob,level,material,itemPrefix,itemName,quality,itemColor,itemText,rawText,category"
    private val dbPath get() = FabricLoader.getInstance().configDir.resolve("crashs-legacy-announcements.db")
    private val csvPath get() = FabricLoader.getInstance().configDir.resolve("crashs-legacy-announcements.csv")

    fun append(record: BroadcastRecord, format: String) {
        when (format.uppercase()) {
            "SQLITE" -> {
                if (!appendSqlite(record)) appendCsv(record)
            }
            else -> appendCsv(record)
        }
    }

    @Synchronized
    private fun appendSqlite(record: BroadcastRecord): Boolean = runCatching {
        val db = connection ?: open()
        db.prepareStatement(
            """
            INSERT INTO announcements (
                received_at, epoch_ms, type, player, is_self, container, mob, level,
                material, item_prefix, item_name, quality, item_color, item_text,
                raw_text, category
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, record.isoTime)
            statement.setLong(2, record.epochMs)
            statement.setString(3, record.type.name.lowercase())
            statement.setString(4, record.player)
            statement.setInt(5, if (record.isSelf) 1 else 0)
            statement.setString(6, record.container)
            statement.setString(7, record.mob)
            statement.setInt(8, record.level)
            statement.setString(9, record.material)
            statement.setString(10, record.prefix)
            statement.setString(11, record.name)
            statement.setString(12, record.quality)
            statement.setString(13, record.color)
            statement.setString(14, record.item)
            statement.setString(15, record.rawText)
            statement.setString(16, record.category)
            statement.executeUpdate()
        }
        true
    }.getOrElse {
        logger.warn("SQLite announcement write failed", it)
        close()
        false
    }

    private fun open(): Connection {
        Files.createDirectories(dbPath.parent)
        Class.forName("org.sqlite.JDBC")
        val db = DriverManager.getConnection("jdbc:sqlite:${dbPath.toAbsolutePath()}")
        db.createStatement().use { statement ->
            statement.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS announcements (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    received_at TEXT NOT NULL,
                    epoch_ms INTEGER NOT NULL,
                    type TEXT NOT NULL,
                    player TEXT NOT NULL,
                    is_self INTEGER NOT NULL DEFAULT 0,
                    container TEXT,
                    mob TEXT,
                    level INTEGER,
                    material TEXT,
                    item_prefix TEXT,
                    item_name TEXT,
                    quality TEXT,
                    item_color TEXT,
                    item_text TEXT,
                    raw_text TEXT,
                    category TEXT
                )
                """.trimIndent(),
            )
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_announcements_time ON announcements(epoch_ms)")
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_announcements_player ON announcements(player)")
            // Upgrade databases created before raw text/category existed.
            runCatching { statement.executeUpdate("ALTER TABLE announcements ADD COLUMN raw_text TEXT") }
            runCatching { statement.executeUpdate("ALTER TABLE announcements ADD COLUMN category TEXT") }
        }
        connection = db
        return db
    }

    @Synchronized
    private fun appendCsv(record: BroadcastRecord) {
        runCatching {
            Files.createDirectories(csvPath.parent)
            if (Files.exists(csvPath) && Files.size(csvPath) > 0L) {
                migrateCsvHeader()
            }
            val needsHeader = !Files.exists(csvPath) || Files.size(csvPath) == 0L
            val line = listOf(
                record.time,
                record.isoTime,
                record.type.name.lowercase(),
                record.player,
                if (record.isSelf) "true" else "false",
                record.container,
                record.mob,
                record.level.toString(),
                record.material,
                record.prefix,
                record.name,
                record.quality,
                record.color,
                record.item,
                record.rawText,
                record.category,
            ).joinToString(",", postfix = "\n") { csv(it) }
            Files.writeString(
                csvPath,
                (if (needsHeader) "$CSV_HEADER\n" else "") + line,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND,
            )
        }
    }

    private fun migrateCsvHeader() {
        val lines = Files.readAllLines(csvPath, StandardCharsets.UTF_8)
        if (lines.isEmpty() || lines.first() == CSV_HEADER) return
        Files.writeString(csvPath, (listOf(CSV_HEADER) + lines.drop(1)).joinToString("\n"), StandardCharsets.UTF_8)
    }

    private fun csv(value: String): String = "\"" + value.replace("\"", "\"\"") + "\""

    @Synchronized
    fun close() {
        runCatching { connection?.close() }
        connection = null
    }
}
