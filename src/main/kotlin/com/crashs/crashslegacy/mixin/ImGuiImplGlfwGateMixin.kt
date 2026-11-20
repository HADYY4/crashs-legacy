package com.crashs.crashslegacy.mixin

import com.crashs.crashslegacy.ui.CrashsImGuiInput
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

/**
 * Screen-scoped input gate for `fabric-gui-imgui`.
 *
 * The library installs its GLFW callbacks at startup and feeds ImGuiIO from them permanently. Each
 * callback first chains to the previous callback (MC / other mods) and only then calls
 * `ImGuiIO.addXxx(...)`, so cancelling at those call sites skips only the ImGui feed and leaves the
 * game's input untouched.
 *
 * `require = 0` everywhere: if a future library version changes these call sites the mixin silently
 * does nothing, the screen logs a warning and the queue-draining fallback keeps working.
 */
@Mixin(targets = ["imgui.glfw.ImGuiImplGlfw"], remap = false)
abstract class ImGuiImplGlfwGateMixin {

    @Inject(
        method = ["init"],
        at = [At("TAIL")],
        require = 0,
        remap = false,
    )
    private fun markGateInstalled(window: Long, installCallbacks: Boolean, callbackInfo: CallbackInfoReturnable<Boolean>) {
        CrashsImGuiInput.gateInstalled = true
    }

    @Inject(
        method = ["mouseButtonCallback"],
        at = [
            At(
                value = "INVOKE",
                target = "Limgui/ImGuiIO;addMouseButtonEvent(IZ)V",
                shift = At.Shift.BEFORE,
            ),
        ],
        cancellable = true,
        require = 0,
        remap = false,
    )
    private fun gateMouseButton(
        window: Long,
        button: Int,
        action: Int,
        mods: Int,
        callbackInfo: CallbackInfo,
    ) {
        CrashsImGuiInput.gateInstalled = true
        if (!CrashsImGuiInput.active) callbackInfo.cancel()
    }

    @Inject(
        method = ["keyCallback"],
        at = [
            At(
                value = "INVOKE",
                target = "Limgui/ImGuiIO;addKeyEvent(IZ)V",
                shift = At.Shift.BEFORE,
            ),
        ],
        cancellable = true,
        require = 0,
        remap = false,
    )
    private fun gateKey(
        window: Long,
        key: Int,
        scancode: Int,
        action: Int,
        mods: Int,
        callbackInfo: CallbackInfo,
    ) {
        CrashsImGuiInput.gateInstalled = true
        if (!CrashsImGuiInput.active) callbackInfo.cancel()
    }

    @Inject(
        method = ["charCallback"],
        at = [
            At(
                value = "INVOKE",
                target = "Limgui/ImGuiIO;addInputCharacter(I)V",
                shift = At.Shift.BEFORE,
            ),
        ],
        cancellable = true,
        require = 0,
        remap = false,
    )
    private fun gateChar(window: Long, codepoint: Int, callbackInfo: CallbackInfo) {
        CrashsImGuiInput.gateInstalled = true
        if (!CrashsImGuiInput.active) callbackInfo.cancel()
    }

    @Inject(
        method = ["scrollCallback"],
        at = [
            At(
                value = "INVOKE",
                target = "Limgui/ImGuiIO;addMouseWheelEvent(FF)V",
                shift = At.Shift.BEFORE,
            ),
        ],
        cancellable = true,
        require = 0,
        remap = false,
    )
    private fun gateScroll(window: Long, xOffset: Double, yOffset: Double, callbackInfo: CallbackInfo) {
        CrashsImGuiInput.gateInstalled = true
        if (!CrashsImGuiInput.active) callbackInfo.cancel()
    }
}
