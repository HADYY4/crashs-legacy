package com.crashs.crashslegacy

import com.crashs.crashslegacy.config.ConfigMigration
import com.crashs.crashslegacy.config.CrashsConfigPaths
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class ConfigMigrationTest {

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
    fun splitsTheLegacyUiFileIntoPrefsAndState() {
        Files.writeString(
            dir.resolve("crashs-legacy-imgui.json"),
            """{"themeMode":"LIGHT","accentColor":1,"uiScale":1.25,"category":1,"page":"CONFIG"}""",
        )
        ConfigMigration.run()

        val root = dir.resolve("crashs-legacy")
        assertTrue(Files.exists(root.resolve("ui.json")))
        assertTrue(Files.exists(root.resolve("ui-state.json")))
        assertTrue(Files.exists(dir.resolve("crashs-legacy-imgui.json.bak")))
    }

    @Test
    fun copiesTheLegacyLayoutFile() {
        Files.writeString(dir.resolve("crashs-legacy-imgui-layout.json"), """{"windowWidth":900}""")
        ConfigMigration.run()

        assertTrue(Files.exists(dir.resolve("crashs-legacy/layout.json")))
        assertTrue(Files.exists(dir.resolve("crashs-legacy-imgui-layout.json.bak")))
    }
}
