package com.crashs.crashslegacy.feature.quickactions

import com.crashs.crashslegacy.config.CrashsConfigPaths
import com.google.gson.GsonBuilder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * `config/crashs-legacy/quick-actions.json`: the Quick Actions page content plus its start-page
 * preference. Kept separate from the module config because actions are an editable nested list.
 */
object QuickActionsStore {

    private const val FILE_NAME = "quick-actions.json"
    private const val DEFAULT_ICON = "minecraft:command_block"

    private data class Payload(
        var enterOnOpen: Boolean = true,
        var actions: MutableList<QuickAction> = mutableListOf(),
    )

    private val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

    private var payload: Payload? = null
    private var nextNumber = 1

    private fun ensureLoaded(): Payload {
        payload?.let { return it }
        val loaded = runCatching {
            val file = CrashsConfigPaths.file(FILE_NAME)
            if (!Files.exists(file)) return@runCatching null
            gson.fromJson(Files.readString(file), Payload::class.java)
        }.getOrNull()
            ?: loadPreset()
            ?: Payload(actions = defaultActions())

        if (loaded.actions.isEmpty()) {
            loaded.actions = loadPreset()?.actions ?: defaultActions()
        }
        // Fill fields that old/hand-written files may omit.
        for (action in loaded.actions) {
            if (action.name.isBlank()) action.name = "宏${nextNumber++}"
            if (action.icon.isBlank()) action.icon = DEFAULT_ICON
            if (action.operations == null) action.operations = mutableListOf()
        }
        payload = loaded
        ensureNumbers()
        save()
        return loaded
    }


    /** Bundled quick actions for a first-time install; existing configs are never overwritten. */
    private fun loadPreset(): Payload? = runCatching {
        QuickActionsStore::class.java
            .getResourceAsStream("/assets/crashs-legacy/quick-actions-default.json")
            ?.use { stream ->
                gson.fromJson(stream.reader(StandardCharsets.UTF_8), Payload::class.java)
            }
    }.getOrNull()

    private fun defaultActions(): MutableList<QuickAction> = mutableListOf(
        QuickAction(
            id = "open-menu",
            name = "打开菜单",
            icon = DEFAULT_ICON,
            operations = mutableListOf(QuickOperation(QuickOperationTypes.COMMAND, "menu")),
        ),
        QuickAction(
            id = "leave-party",
            name = "退出副本",
            icon = DEFAULT_ICON,
            operations = mutableListOf(QuickOperation(QuickOperationTypes.COMMAND, "leave")),
        ),
    )

    private fun ensureNumbers() {
        val used = payload?.actions?.mapNotNull { action ->
            Regex("""^宏(\d+)$""").find(action.name)?.groupValues?.get(1)?.toIntOrNull()
        }?.maxOrNull() ?: 0
        nextNumber = used + 1
    }

    fun enterOnOpen(): Boolean = ensureLoaded().enterOnOpen

    fun setEnterOnOpen(value: Boolean) {
        ensureLoaded().enterOnOpen = value
        save()
    }

    fun actions(): MutableList<QuickAction> = ensureLoaded().actions

    fun addAction(): QuickAction {
        val data = ensureLoaded()
        val action = QuickAction(
            id = "quick-${System.currentTimeMillis()}",
            name = "宏${nextNumber++}",
            icon = DEFAULT_ICON,
            operations = mutableListOf(QuickOperation(QuickOperationTypes.COMMAND, "")),
        )
        data.actions += action
        save()
        return action
    }

    fun removeAction(action: QuickAction) {
        ensureLoaded().actions.remove(action)
        save()
    }

    fun save() {
        val data = payload ?: return
        runCatching {
            val file = CrashsConfigPaths.file(FILE_NAME)
            Files.createDirectories(file.parent)
            val temp = file.resolveSibling(file.fileName.toString() + ".tmp")
            Files.writeString(temp, gson.toJson(data), StandardCharsets.UTF_8)
            runCatching {
                Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            }.onFailure {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }

    /** Reload after `/crashs reload`. */
    fun reload() {
        payload = null
        ensureLoaded()
    }
}
