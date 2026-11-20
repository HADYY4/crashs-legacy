package com.crashs.crashslegacy.config

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.crashs.crashslegacy.BroadcastSettings
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Reads and writes the split config folder.
 *
 * The runtime model ([CrashsLegacyConfig]) is unchanged: files are merged into one JSON object on
 * load and split again on save. Unknown keys written by hand are preserved when a file is rewritten.
 */
object ConfigStore {

    private val gson = GsonBuilder()
        .setPrettyPrinting()
        .disableHtmlEscaping()
        .create()

    private val LEGACY_NAMES = listOf("crashslegacy.json", "crashs-legacy.json")

    data class Loaded(val config: CrashsLegacyConfig, val present: Boolean)

    fun load(): Loaded {
        val root = CrashsConfigPaths.ensureRoot()

        val hasSections = ConfigSections.FILES.keys.any { Files.exists(root.resolve("$it.json")) }
        if (hasSections) {
            val merged = JsonObject()
            for (name in ConfigSections.FILES.keys) {
                val file = root.resolve("$name.json")
                if (!Files.exists(file)) continue
                val json = readJson(file) ?: continue
                for ((key, value) in json.entrySet()) merged.add(key, value)
            }
            return Loaded(readConfig(merged), true)
        }

        val legacy = LEGACY_NAMES
            .map { CrashsConfigPaths.legacy(it) }
            .firstOrNull { Files.exists(it) }
        if (legacy != null) {
            val json = readJson(legacy) ?: JsonObject()
            val config = readConfig(json)
            // Write the new folder first, then keep the old file as a backup.
            save(config)
            backup(legacy)
            return Loaded(config, true)
        }

        return Loaded(CrashsLegacyConfig(), false)
    }

    fun save(config: CrashsLegacyConfig) {
        val root = CrashsConfigPaths.ensureRoot()
        val tree = gson.toJsonTree(config).asJsonObject
        for ((name, fields) in ConfigSections.FILES) {
            val file = root.resolve("$name.json")
            val out = JsonObject()
            if (name == "general") {
                out.addProperty("formatVersion", ConfigSections.FORMAT_VERSION)
            }
            for (field in fields) {
                tree.get(field)?.let { out.add(field, it) }
            }
            // Preserve hand-written keys that this build does not know about.
            val existing = if (Files.exists(file)) readJson(file) else null
            existing?.entrySet()?.forEach { (key, value) ->
                if (key !in ConfigSections.ALL_FIELDS && key != "formatVersion" && !out.has(key)) {
                    out.add(key, value)
                }
            }
            writeAtomic(file, gson.toJson(out))
        }
        writeReadmeIfMissing(root)
    }

    /** Human-readable map of the folder; only written once so the user can edit it freely. */
    private fun writeReadmeIfMissing(root: Path) {
        val readme = root.resolve("README.md")
        if (Files.exists(readme)) return
        runCatching {
            Files.writeString(
                readme,
                """
                # Crashs Legacy 配置

                此文件夹由游戏自动生成。修改前请退出游戏；游戏运行中的保存会覆盖手改内容。
                改完后可在游戏内执行 `/crashs reload` 重新读取（字体/布局尺寸的改动仍需重启）。

                | 文件 | 内容 |
                |---|---|
                | `general.json` | 格式版本、默认配置版本 |
                | `killfeed.json` | 击杀反馈：解析正则、音效、HUD、样式、颜色 |
                | `coins.json` | 金币统计 |
                | `item-tracker.json` | 库存管理 / 管理背包 |
                | `broadcast.json` | 播报管理 |
                | `server-damage.json` | 服务器伤害显示 |
                | `lightning.json` | 闪电管理：隐藏闪烁/实体、颜色、音效 |
                | `quick-actions.json` | 快捷操作：宏列表、进入 UI 默认页 |
                | `ui.json` | 主题、强调色、界面缩放 |
                | `ui-state.json` | 窗口位置/大小、分类、折叠、标签页 |
                | `layout.json` | ImGui 布局尺寸（窗口宽高、导航栏、卡片间距等） |

                旧版单文件会保留为 `*.bak`，不会自动删除。
                """.trimIndent(),
            )
        }
    }

    private fun readConfig(json: JsonObject): CrashsLegacyConfig {
        val config = gson.fromJson(json, CrashsLegacyConfig::class.java) ?: CrashsLegacyConfig()
        @Suppress("SENSELESS_COMPARISON")
        if (config.broadcast == null) config.broadcast = BroadcastSettings()
        fillMissingDefaults(config, json)
        return config
    }

    private fun fillMissingDefaults(config: CrashsLegacyConfig, json: JsonObject) {
        val defaults = CrashsLegacyConfig()
        CrashsLegacyConfig::class.java.declaredFields
            .filter { !java.lang.reflect.Modifier.isStatic(it.modifiers) && !it.isSynthetic }
            .forEach { field ->
                if (!json.has(field.name)) {
                    runCatching {
                        field.isAccessible = true
                        field.set(config, field.get(defaults))
                    }
                }
            }
        // 1.11.64: itemTrackerOrder was replaced by itemTrackerHudDirection.
        if (!json.has("itemTrackerHudDirection") && json.has("itemTrackerOrder")) {
            config.itemTrackerHudDirection = when (json.get("itemTrackerOrder").asString.uppercase()) {
                "TOP" -> "DOWN"
                else -> "UP"
            }
        }
    }

    private fun readJson(file: Path): JsonObject? =
        runCatching {
            Files.newBufferedReader(file, StandardCharsets.UTF_8).use { reader ->
                JsonParser.parseReader(reader).asJsonObject
            }
        }.getOrNull()

    private fun writeAtomic(file: Path, text: String) {
        runCatching {
            Files.createDirectories(file.parent)
            val temp = file.resolveSibling(file.fileName.toString() + ".tmp")
            Files.writeString(temp, text, StandardCharsets.UTF_8)
            runCatching {
                Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            }.onFailure {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }

    /** Keeps the old single file as `<name>.bak`, with a timestamp if a backup already exists. */
    private fun backup(file: Path) {
        runCatching {
            var target = file.resolveSibling(file.fileName.toString() + ".bak")
            if (Files.exists(target)) {
                target = file.resolveSibling(file.fileName.toString() + ".bak-" + System.currentTimeMillis())
            }
            Files.move(file, target, StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
