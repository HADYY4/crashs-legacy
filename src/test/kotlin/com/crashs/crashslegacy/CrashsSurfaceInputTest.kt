package com.crashs.crashslegacy

import imgui.ImGui
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.io.File
import com.crashs.crashslegacy.ui.CrashsImGuiFrameState
import com.crashs.crashslegacy.ui.CrashsImGuiLayout
import com.crashs.crashslegacy.ui.CrashsImGuiScreen
import com.crashs.crashslegacy.ui.CrashsImGuiSurface
import com.crashs.crashslegacy.ui.CrashsUiHost
import com.crashs.crashslegacy.ui.CrashsUiModule

/**
 * Regression tests for the ClickGUI input path — the bug class that kept coming back without any
 * test coverage:
 *
 * > pointer events queued while another screen was open replay onto the cards when the surface comes
 * > back, so one click can open the broadcast list twice and ESC has to be pressed several times.
 *
 * The fix has two parts: draining the queue at screen-entry time, and a two-frame input guard that
 * hides whatever arrives in the same GLFW poll batch. Both are asserted here against a real ImGui
 * context. The observable is exactly what the bug was about: whether the widgets of a frame can see
 * a click. [FakeHost.frameTick] runs right after the guard is applied and before any widget, so it
 * records the state on which the UI actually acts.
 */
class CrashsSurfaceInputTest {

    private class FakeHost : CrashsUiHost {
        var widgetsSawClick: Boolean? = null
        var widgetsSawMouseDown: Boolean? = null
        /** Set to reproduce a screen switch from inside a frame (card action / 完成 button). */
        var drainFromInsideFrame: (() -> Unit)? = null
        var drainThrew: Boolean = false

        override val modules: List<CrashsUiModule> = emptyList()

        override fun frameTick() {
            val io = ImGui.getIO()
            widgetsSawClick = io.getMouseClicked(0)
            widgetsSawMouseDown = io.getMouseDown(0)
            drainFromInsideFrame?.let { drain ->
                drainFromInsideFrame = null
                try {
                    drain()
                } catch (error: Throwable) {
                    drainThrew = true
                    throw error
                }
            }
        }

        override fun configChanged() = Unit

        override fun closeScreen() = Unit

        override fun openExternal(moduleId: String) = Unit

        override fun openHudEditor() = Unit

        override fun resetConfig() = Unit

        val logs = mutableListOf<String>()

        override fun log(message: String) {
            logs += message
        }
    }

    companion object {
        private val projectDir = File(System.getProperty("crashs.projectDir") ?: ".")

        @JvmStatic
        @BeforeAll
        fun setUp() {
            ImGui.createContext()
            val io = ImGui.getIO()
            // No imgui.ini side file: the headless context must not write into the repository.
            io.setIniFilename(null)
            io.setDisplaySize(1040f, 760f)
            val size = CrashsImGuiLayout.fontSize()
            val fontFile = File(projectDir, "src/main/resources/assets/crashs-legacy/fonts/misans.ttf")
            assertTrue(fontFile.isFile, "bundled font not found: ${fontFile.absolutePath}")
            val font = io.fonts.addFontFromFileTTF(fontFile.absolutePath, size)
            assertNotNull(font, "ImGui refused to load MiSans")
            io.fonts.build()
            ImGui.pushFont(font, size)
        }
    }

    private fun frame(surface: CrashsImGuiSurface) {
        val io = ImGui.getIO()
        ImGui.newFrame()
        surface.frame(io)
        ImGui.endFrame()
        ImGui.render()
    }

    /** Queues the press/release pair the way the GLFW backend does while no frame is running. */
    private fun queueClick(x: Float = 600f, y: Float = 130f) {
        val io = ImGui.getIO()
        io.addMousePosEvent(x, y)
        io.addMouseButtonEvent(0, true)
        io.addMouseButtonEvent(0, false)
    }

    private fun settle(): Pair<FakeHost, CrashsImGuiSurface> {
        val host = FakeHost()
        val surface = CrashsImGuiSurface(host)
        repeat(3) { frame(surface) }
        return host to surface
    }

    @Test
    fun aQueuedClickReachesTheWidgetsWithoutTheGuard() {
        // Control: this is the replay the bug was about. Without protection the widgets of the next
        // frame see the click, which is what re-triggered the card.
        val (host, surface) = settle()
        queueClick()
        frame(surface)
        assertEquals(true, host.widgetsSawClick, "a queued click must be visible to widgets (harness sanity)")
    }

    @Test
    fun theEntryGuardHidesAReplayedClick() {
        val (host, surface) = settle()
        surface.armInputGuard(2)
        queueClick()

        frame(surface)
        assertFalse(host.widgetsSawClick!!, "first frame after entry must not see the replayed click")
        assertFalse(host.widgetsSawMouseDown!!, "the guard must clear the pressed state too")

        frame(surface)
        assertFalse(host.widgetsSawClick!!, "second guard frame must hide it as well")

        frame(surface)
        assertFalse(host.widgetsSawClick!!, "the click is gone, and the guard expiring must not revive it")
    }

    @Test
    fun aSmallDisplayFallsBackInsteadOfBuildingTheFullMenu() {
        val host = FakeHost()
        val surface = CrashsImGuiSurface(host)
        val io = ImGui.getIO()
        val width = io.displaySizeX
        val height = io.displaySizeY
        try {
            // 320x240 is far below the 480x360 threshold: the full rail/card layout must be skipped.
            io.setDisplaySize(320f, 240f)
            repeat(2) { frame(surface) }
            assertTrue(host.logs.any { it.contains("UI layout fallback") }, "fallback must be logged")
        } finally {
            io.setDisplaySize(width, height)
        }
    }

    @Test
    fun drainingTheQueueRemovesTheClickBeforeItCanBeSeen() {
        val (host, surface) = settle()
        queueClick()
        surface.discardQueuedPointerInput("test")
        frame(surface)
        assertFalse(host.widgetsSawClick!!, "drained events must never become a click")
    }

    @Test
    fun aClickAfterTheGuardStillWorks() {
        val (host, surface) = settle()
        surface.armInputGuard(1)
        frame(surface)
        assertFalse(host.widgetsSawClick!!, "the guard frame hides clicks")

        queueClick()
        frame(surface)
        assertEquals(true, host.widgetsSawClick, "after the guard a deliberate click must work again")
    }

    @Test
    fun drainingFromInsideAFrameDoesNotNestFrames() {
        // Regression: the HUD editor's 完成 button called setScreen from inside the ImGui frame,
        // which reached CrashsImGuiScreen.init() -> discardQueuedPointerInput() -> newFrame() and
        // tripped ImGui's "nested frame" assertion.
        val host = FakeHost()
        val surface = CrashsImGuiSurface(host)
        frame(surface)
        host.drainFromInsideFrame = { surface.discardQueuedPointerInput("inside-frame") }
        frame(surface)
        assertFalse(host.drainThrew, "draining inside a frame must be a no-op")
        assertEquals(false, CrashsImGuiFrameState.active, "the frame flag must be restored afterwards")
    }

    @Test
    fun openingTheScreenTwiceArmsAFreshGuardEachTime() {
        val (host, surface) = settle()

        surface.armInputGuard(2)
        queueClick()
        repeat(3) { frame(surface) }
        assertFalse(host.widgetsSawClick!!)

        queueClick()
        frame(surface)
        assertEquals(true, host.widgetsSawClick, "a click after the first entry still opens the screen")

        surface.armInputGuard(2)
        queueClick()
        frame(surface)
        assertFalse(host.widgetsSawClick!!, "the second entry is guarded again")
    }
}
