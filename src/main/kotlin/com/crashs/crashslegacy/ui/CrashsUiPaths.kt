package com.crashs.crashslegacy.ui

import java.nio.file.Path

/**
 * Filesystem root for the UI stores, kept separate from the Fabric-facing config package so the
 * preview source set can compile the surface without Minecraft/Fabric on the classpath.
 *
 * The client initializer sets [root] to `config/crashs-legacy/`; when it is `null` (preview,
 * headless tests) the stores stay in memory.
 */
object CrashsUiPaths {

    @Volatile
    var root: Path? = null

    fun file(name: String): Path? = root?.resolve(name)
}
