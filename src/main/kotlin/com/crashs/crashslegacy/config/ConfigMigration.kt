package com.crashs.crashslegacy.config

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * One-time migration from the old single-file layout to the config folder.
 *
 * The main config is migrated by [ConfigStore.load]; this object handles the two UI files.
 */
object ConfigMigration {

    private val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

    fun run() {
        migrateUiLegacy()
        migrateLayoutLegacy()
    }

    private fun migrateUiLegacy() {
        val legacy = CrashsConfigPaths.legacy("crashs-legacy-imgui.json")
        if (!Files.exists(legacy)) return
        val root = CrashsConfigPaths.ensureRoot()
        val stateFile = root.resolve("ui-state.json")
        val prefsFile = root.resolve("ui.json")
        if (Files.exists(stateFile) && Files.exists(prefsFile)) return

        val json = read(legacy) ?: return
        if (!Files.exists(stateFile)) {
            val state = JsonObject()
            listOf(
                "category", "windowX", "windowY", "windowWidth", "windowHeight",
                "page", "hudPanelX", "hudPanelY", "folds", "tabs",
            ).forEach { key -> json.get(key)?.let { state.add(key, it) } }
            writeAtomic(stateFile, gson.toJson(state))
        }
        if (!Files.exists(prefsFile)) {
            val prefs = JsonObject()
            listOf("themeMode", "accentColor", "uiScale").forEach { key ->
                json.get(key)?.let { prefs.add(key, it) }
            }
            writeAtomic(prefsFile, gson.toJson(prefs))
        }
        backup(legacy)
    }

    private fun migrateLayoutLegacy() {
        val legacy = CrashsConfigPaths.legacy("crashs-legacy-imgui-layout.json")
        if (!Files.exists(legacy)) return
        val target = CrashsConfigPaths.file("layout.json")
        if (!Files.exists(target)) {
            CrashsConfigPaths.ensureRoot()
            runCatching {
                Files.copy(legacy, target, StandardCopyOption.REPLACE_EXISTING)
            }
        }
        backup(legacy)
    }

    private fun read(file: Path): JsonObject? =
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
