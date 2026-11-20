package com.crashs.crashslegacy

import com.crashs.crashslegacy.ui.CrashsImGuiInput
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The gate defaults to closed and only forwards events while the ClickGUI is open. */
class CrashsImGuiInputTest {

    @AfterEach
    fun reset() {
        CrashsImGuiInput.deactivate()
        CrashsImGuiInput.gateInstalled = false
    }

    @Test
    fun theGateStartsClosedAndToggles() {
        assertFalse(CrashsImGuiInput.active)
        CrashsImGuiInput.activate()
        assertTrue(CrashsImGuiInput.active)
        CrashsImGuiInput.deactivate()
        assertFalse(CrashsImGuiInput.active)
    }

    @Test
    fun theInstalledFlagDefaultsToFalse() {
        assertFalse(CrashsImGuiInput.gateInstalled)
    }
}
