package com.crashs.crashslegacy

import com.crashs.crashslegacy.config.ConfigStore
import com.crashs.crashslegacy.config.CrashsConfigPaths
import com.crashs.crashslegacy.config.CrashsLegacyConfig
import com.google.gson.JsonParser
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class ConfigStoreTest {

    @TempDir
    lateinit var dir: Path

    @BeforeEach
    fun setUp() {
        CrashsConfigPaths.configDir = dir
    }

    @AfterEach
    fun tearDown() {
        CrashsConfigPaths.configDir = null
    }

    @Test
    fun savesOneFilePerModuleAndReloadsIt() {
        val config = CrashsLegacyConfig().apply {
            enabled = false
            coinsInfoEnabled = true
            itemTrackerEnabled = true
            broadcastSound = true
            serverDamageShadow = false
        }
        ConfigStore.save(config)

        val root = dir.resolve("crashs-legacy")
        listOf("general", "killfeed", "coins", "item-tracker", "broadcast", "server-damage").forEach {
            assertTrue(Files.exists(root.resolve("$it.json")), "missing $it.json")
        }

        val loaded = ConfigStore.load()
        assertTrue(loaded.present)
        assertFalse(loaded.config.enabled)
        assertTrue(loaded.config.coinsInfoEnabled)
        assertTrue(loaded.config.itemTrackerEnabled)
        assertTrue(loaded.config.broadcastSound)
        assertFalse(loaded.config.serverDamageShadow)
    }

    @Test
    fun migratesTheLegacySingleFileAndKeepsABackup() {
        val legacy = dir.resolve("crashslegacy.json")
        Files.writeString(legacy, """{"enabled":false,"coinsInfoEnabled":true}""")

        val loaded = ConfigStore.load()
        assertTrue(loaded.present)
        assertFalse(loaded.config.enabled)
        assertTrue(loaded.config.coinsInfoEnabled)
        assertFalse(Files.exists(legacy), "legacy file should be moved")
        assertTrue(Files.exists(dir.resolve("crashslegacy.json.bak")), "backup missing")
        assertTrue(Files.exists(dir.resolve("crashs-legacy/killfeed.json")))
    }

    @Test
    fun preservesHandWrittenUnknownKeys() {
        ConfigStore.save(CrashsLegacyConfig())
        val killfeed = dir.resolve("crashs-legacy/killfeed.json")
        val json = JsonParser.parseString(Files.readString(killfeed)).asJsonObject
        json.addProperty("myCustomKey", 42)
        Files.writeString(killfeed, json.toString())

        val config = ConfigStore.load().config
        ConfigStore.save(config)

        val rewritten = JsonParser.parseString(Files.readString(killfeed)).asJsonObject
        assertEquals(42, rewritten.get("myCustomKey").asInt)
    }
}
