package com.crashs.crashslegacy

import com.crashs.crashslegacy.config.CrashsConfigPaths
import com.crashs.crashslegacy.feature.quickactions.QuickActionsStore
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class QuickActionsStoreTest {

    @TempDir
    lateinit var dir: Path

    @BeforeEach
    fun setUp() {
        CrashsConfigPaths.configDir = dir
        QuickActionsStore.reload()
    }

    @AfterEach
    fun tearDown() {
        CrashsConfigPaths.configDir = null
    }

    @Test
    fun createsTheBundledDefaultActions() {
        assertEquals(13, QuickActionsStore.actions().size)
        assertEquals("退出副本", QuickActionsStore.actions()[0].name)
        assertEquals("minecraft:ender_pearl", QuickActionsStore.actions()[0].icon)
        assertFalse(QuickActionsStore.enterOnOpen())
        assertTrue(Files.exists(dir.resolve("crashs-legacy/quick-actions.json")))
    }

    @Test
    fun addsANewMacroWithTheNextNumber() {
        val action = QuickActionsStore.addAction()
        assertEquals("宏1", action.name)
        assertEquals("minecraft:command_block", action.icon)
        assertEquals(14, QuickActionsStore.actions().size)

        QuickActionsStore.reload()
        assertEquals(14, QuickActionsStore.actions().size)
        assertEquals("宏1", QuickActionsStore.actions().last().name)
    }

    @Test
    fun enterOnOpenRoundTrips() {
        QuickActionsStore.setEnterOnOpen(true)
        QuickActionsStore.reload()
        assertTrue(QuickActionsStore.enterOnOpen())

        QuickActionsStore.setEnterOnOpen(false)
        QuickActionsStore.reload()
        assertFalse(QuickActionsStore.enterOnOpen())
    }
}
