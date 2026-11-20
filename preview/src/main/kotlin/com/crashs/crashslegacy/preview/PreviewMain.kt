package com.crashs.crashslegacy.preview

import imgui.ImGui
import imgui.ImGuiIO
import imgui.gl3.ImGuiImplGl3
import imgui.glfw.ImGuiImplGlfw
import org.lwjgl.glfw.Callbacks.glfwFreeCallbacks
import org.lwjgl.glfw.GLFW
import org.lwjgl.glfw.GLFWErrorCallback
import org.lwjgl.opengl.GL
import org.lwjgl.system.MemoryUtil.NULL
import java.io.File
import com.crashs.crashslegacy.ui.CrashsImGuiLayout
import com.crashs.crashslegacy.ui.CrashsUiPaths
import com.crashs.crashslegacy.ui.CrashsImGuiSurface
import com.crashs.crashslegacy.ui.CrashsImGuiUiState
import com.crashs.crashslegacy.ui.theme.CrashsImGuiTheme

/**
 * Desktop preview for the Crashs Legacy surface.
 *
 * No Minecraft and no mod jar: the window renders [CrashsImGuiSurface], the same class the game
 * uses, with demo module bodies. Layout, typography and motion are therefore identical to the
 * client; only the settings widgets are stand-ins.
 */
fun main() {
    val errorCallback = GLFWErrorCallback.createPrint(System.err)
    GLFW.glfwSetErrorCallback(errorCallback)
    check(GLFW.glfwInit()) { "Unable to initialize GLFW" }
    GLFW.glfwDefaultWindowHints()
    GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE)
    GLFW.glfwWindowHint(GLFW.GLFW_RESIZABLE, GLFW.GLFW_TRUE)
    GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3)
    GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 3)
    GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE)
    val window = GLFW.glfwCreateWindow(1040, 760, "Crashs Legacy - Preview", NULL, NULL)
    check(window != NULL) { "Unable to create GLFW window" }
    GLFW.glfwMakeContextCurrent(window)
    GLFW.glfwSwapInterval(1)
    GLFW.glfwShowWindow(window)
    GL.createCapabilities()

    ImGui.createContext()
    val io = ImGui.getIO()
    io.iniSavingRate = 5f

    // Same seams the mod uses: no config directory means in-memory defaults, no file IO.
    CrashsUiPaths.root = null
    CrashsImGuiLayout.load()
    io.setDisplaySize(1040f, 760f)

    val fontFile = File("src/main/resources/assets/crashs-legacy/fonts/misans.ttf")
        .takeIf { it.isFile }
        ?: File("../src/main/resources/assets/crashs-legacy/fonts/misans.ttf")
    CrashsImGuiTheme.logSink = { println(it) }
    CrashsImGuiTheme.initialize(if (fontFile.isFile) fontFile.readBytes() else null)

    val implGlfw = ImGuiImplGlfw()
    val implGl3 = ImGuiImplGl3()
    implGlfw.init(window, true)
    implGl3.init("#version 330 core")

    val host = DemoHost()
    val surface = CrashsImGuiSurface(host)

    while (!GLFW.glfwWindowShouldClose(window) && !host.shouldClose) {
        GLFW.glfwPollEvents()
        implGl3.newFrame()
        implGlfw.newFrame()
        ImGui.newFrame()
        surface.frame(io)
        ImGui.render()
        implGl3.renderDrawData(ImGui.getDrawData())
        GLFW.glfwSwapBuffers(window)
    }

    implGl3.shutdown()
    implGlfw.shutdown()
    ImGui.destroyContext()
    glfwFreeCallbacks(window)
    GLFW.glfwDestroyWindow(window)
    GLFW.glfwTerminate()
    errorCallback.free()
}
