package com.crashs.crashslegacy.preview

import imgui.ImGui
import imgui.type.ImInt
import java.io.File
import kotlin.system.exitProcess
import com.crashs.crashslegacy.ui.CrashsImGuiLayout
import com.crashs.crashslegacy.ui.CrashsUiPaths
import com.crashs.crashslegacy.ui.CrashsImGuiSurface
import com.crashs.crashslegacy.ui.CrashsImGuiUiState
import com.crashs.crashslegacy.ui.theme.CrashsImGuiTheme

/**
 * Headless CPU benchmark for the real Crashs Legacy surface.
 *
 * Run with `./gradlew :preview:perf`. Unlike the old scratch probe this drives
 * [CrashsImGuiSurface] itself — same layout, same theme, same modules as the desktop preview — so
 * the numbers follow the shipped UI instead of a hand-written imitation.
 *
 * Properties (all optional):
 * - `perf.all=true`    expand every card body (worst case, much more content per frame)
 * - `perf.frames=1000` measured frames (250 warm-up frames always run first)
 * - `perf.maxUs=200`   fail the run when the mean frame time exceeds this many microseconds
 * - `perf.json=true`   print a single JSON line instead of the human-readable report
 */
fun main(args: Array<String>) {
    val expandAll = flag("perf.all")
    val measuredFrames = intProperty("perf.frames", 1000)
    val maxUs = doubleProperty("perf.maxUs", 0.0)
    val json = flag("perf.json")
    val warmupFrames = 250

    // Same seams the mod uses: no config directory means in-memory defaults, no file IO.
    CrashsUiPaths.root = null
    CrashsImGuiLayout.load()

    val fontBytes = fontFile()?.readBytes()
    val fontSource = fontFile()?.path ?: "<none>"

    ImGui.createContext()
    val io = ImGui.getIO()
    io.setIniFilename(null)
    io.setDisplaySize(CrashsImGuiLayout.windowWidthPx(), CrashsImGuiLayout.windowHeightPx())

    val host = DemoHost(verbose = false)
    if (expandAll) {
        host.modules.forEach { CrashsImGuiUiState.setOpen("card.${it.id}", true) }
    }

    CrashsImGuiTheme.logSink = { if (!json) println(it) }
    val bakeStart = System.nanoTime()
    CrashsImGuiTheme.initialize(fontBytes)
    val bakeMs = (System.nanoTime() - bakeStart) / 1_000_000.0

    val atlasWidth = ImInt()
    val atlasHeight = ImInt()
    runCatching { io.fonts.getTexDataAsRGBA32(atlasWidth, atlasHeight) }
    val atlasMb = atlasWidth.get().toDouble() * atlasHeight.get() * 4.0 / (1024.0 * 1024.0)

    val surface = CrashsImGuiSurface(host)
    val ioRef = io

    fun frame() {
        ImGui.newFrame()
        surface.frame(ioRef)
        ImGui.endFrame()
        ImGui.render()
    }

    repeat(warmupFrames) { frame() }

    val samples = DoubleArray(measuredFrames)
    for (index in 0 until measuredFrames) {
        val start = System.nanoTime()
        frame()
        samples[index] = (System.nanoTime() - start) / 1000.0
    }
    val sorted = samples.sortedArray()
    val mean = samples.average()
    val median = sorted[sorted.size / 2]
    val p95 = sorted[((sorted.size * 95) / 100).coerceAtMost(sorted.size - 1)]
    val max = sorted.last()
    val drawData = ImGui.getDrawData()
    val verts = drawData.totalVtxCount
    val indices = drawData.totalIdxCount
    val cmdLists = drawData.cmdListsCount

    ImGui.destroyContext()

    if (json) {
        println(
            """{"meanUs":%.2f,"medianUs":%.2f,"p95Us":%.2f,"maxUs":%.2f,"verts":%d,"indices":%d,"cmdLists":%d,"atlasWidth":%d,"atlasHeight":%d,"atlasMb":%.1f,"bakeMs":%.1f,"expandAll":%s}"""
                .format(mean, median, p95, max, verts, indices, cmdLists, atlasWidth.get(), atlasHeight.get(), atlasMb, bakeMs, expandAll),
        )
    } else {
        println("Crashs Legacy UI benchmark")
        println("  font:        $fontSource")
        println("  scheme:      ${CrashsImGuiTheme.mode}  accent=#${CrashsImGuiTheme.accent.toUInt().toString(16)}")
        println("  cards:       ${if (expandAll) "all expanded (worst case)" else "default folds"}")
        println("  frames:      $warmupFrames warm-up + $measuredFrames measured")
        println("  atlas:       ${atlasWidth.get()}x${atlasHeight.get()}  %.1f MB  baked in %.0f ms".format(atlasMb, bakeMs))
        println("  CPU/frame:   mean %.1f us | median %.1f us | p95 %.1f us | max %.1f us".format(mean, median, p95, max))
        println("  draw:        $verts verts, $indices indices, $cmdLists cmd lists")
        println("  budget:      %.2f%% of a 16.67 ms frame (mean)".format(mean / 16670.0 * 100.0))
    }

    if (maxUs > 0.0 && mean > maxUs) {
        System.err.println("perf regression: mean %.1f us exceeds the %.1f us budget".format(mean, maxUs))
        exitProcess(1)
    }
}

private fun flag(name: String): Boolean = System.getProperty(name)?.toBoolean() ?: false

private fun intProperty(name: String, fallback: Int): Int =
    System.getProperty(name)?.toIntOrNull()?.takeIf { it > 0 } ?: fallback

private fun doubleProperty(name: String, fallback: Double): Double =
    System.getProperty(name)?.toDoubleOrNull() ?: fallback

private fun fontFile(): File? {
    val candidates = listOf(
        File("src/main/resources/assets/crashs-legacy/fonts/misans.ttf"),
        File("../src/main/resources/assets/crashs-legacy/fonts/misans.ttf"),
    )
    return candidates.firstOrNull { it.isFile }
}
