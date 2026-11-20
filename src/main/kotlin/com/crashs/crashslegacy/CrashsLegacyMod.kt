package com.crashs.crashslegacy

import com.crashs.crashslegacy.serverdamage.ServerDamageDisplayManager
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.loader.api.FabricLoader
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.resources.Identifier
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.KeyMapping
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW
import org.slf4j.LoggerFactory
import com.crashs.crashslegacy.chat.ChatIntake
import com.crashs.crashslegacy.command.CrashsLegacyCommands
import com.crashs.crashslegacy.config.CrashsImGuiConfigSync
import com.crashs.crashslegacy.config.ConfigMigration
import com.crashs.crashslegacy.config.CrashsConfigPaths
import com.crashs.crashslegacy.config.CrashsLegacyConfigManager
import com.crashs.crashslegacy.feature.broadcast.BroadcastManager
import com.crashs.crashslegacy.feature.itemtracker.ItemTrackerInventory
import com.crashs.crashslegacy.feature.itemtracker.ItemTrackerState
import com.crashs.crashslegacy.feature.quickactions.QuickActionIcons
import com.crashs.crashslegacy.feature.quickactions.QuickActionRunner
import com.crashs.crashslegacy.feature.killfeed.CrashsLegacySoundPlayer
import com.crashs.crashslegacy.feature.killfeed.KillFeedState
import com.crashs.crashslegacy.ui.CrashsImGuiLayout
import com.crashs.crashslegacy.ui.CrashsImGuiScreen
import com.crashs.crashslegacy.ui.CrashsUiPaths
import com.crashs.crashslegacy.ui.CrashsImGuiUiState

class CrashsLegacyMod : ClientModInitializer {

    companion object {
        const val MOD_ID = "crashs-legacy"
        val LOGGER = LoggerFactory.getLogger(MOD_ID)

        private var lastWorld: ClientLevel? = null
        private var pendingScreen: ((Screen?) -> Screen)? = null
        private lateinit var openUiKey: KeyMapping
        private data class PacketResult(val at: Long, val cancel: Boolean)
        /**
         * Called from the network-handler mixin.
         *
         * @return true when the message should be cancelled.
         */
        @JvmStatic
        fun requestScreen(factory: (Screen?) -> Screen) {
            pendingScreen = factory
        }

    }

    override fun onInitializeClient() {
        // All files live under config/crashs-legacy/; migrate the old single files first.
        CrashsConfigPaths.configDir = FabricLoader.getInstance().configDir
        ConfigMigration.run()
        CrashsUiPaths.root = CrashsConfigPaths.root
        CrashsLegacyConfigManager.load()

        // Derived client state refreshes on config changes instead of polling every tick.
        CrashsImGuiConfigSync.onChanged = { ServerDamageDisplayManager.markDirty() }

        ClientPlayConnectionEvents.JOIN.register { _, _, _ ->
            resetConnectionSession()
        }

        ClientPlayConnectionEvents.DISCONNECT.register { _, _ ->
            resetConnectionSession()
            lastWorld = null
        }

        openUiKey = KeyBindingHelper.registerKeyBinding(
            KeyMapping(
                "key.crashs-legacy.ui",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_F7,
                KeyMapping.Category.register(Identifier.fromNamespaceAndPath("crashs-legacy", "main")),
            ),
        )

        ClientTickEvents.END_CLIENT_TICK.register { client ->
            while (openUiKey.consumeClick()) {
                requestScreen { parent -> CrashsImGuiScreen(parent) }
            }

            pendingScreen?.let { factory ->
                pendingScreen = null
                client.setScreen(factory(client.screen))
            }

            ServerDamageDisplayManager.tick()
            ItemTrackerInventory.tick(client)
            QuickActionRunner.tick(client)
            BroadcastManager.tick()
            QuickActionIcons.renderPending()

            val world = client.level
            if (world !== lastWorld) {
                if (lastWorld != null) {
                    resetSession()
                }
                lastWorld = world
            }
        }

        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            CrashsLegacyCommands.register(dispatcher)
        }

        LOGGER.info("Crashs Legacy loaded for Minecraft 1.21.11")
    }

    private fun resetSession() {
        KillFeedState.reset()
        CrashsLegacySoundPlayer.reset()
        ServerDamageDisplayManager.restoreAll()
        ServerDamageDisplayManager.clearWorld()
        ItemTrackerInventory.reset()
        QuickActionRunner.reset()
    }

    private fun resetConnectionSession() {
        resetSession()
        CoinsSession.reset()
        BroadcastManager.clear()
        ItemTrackerState.reset()
        ChatIntake.reset()
    }
}
