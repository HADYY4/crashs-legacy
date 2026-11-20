package com.crashs.crashslegacy.config

import net.fabricmc.loader.api.FabricLoader
import java.nio.file.Files
import java.nio.file.Path

/**
 * Single source of truth for where Crashs Legacy keeps its files.
 *
 * Everything lives in `config/crashs-legacy/`; the old single files stay next to it as `.bak`
 * after migration.
 */
object CrashsConfigPaths {

    /** Set by the client initializer; tests may point it at a temporary directory. */
    @Volatile
    var configDir: Path? = null

    val baseDir: Path
        get() = configDir ?: FabricLoader.getInstance().configDir

    val root: Path
        get() = baseDir.resolve("crashs-legacy")

    fun ensureRoot(): Path {
        val dir = root
        Files.createDirectories(dir)
        return dir
    }

    fun file(name: String): Path = root.resolve(name)

    fun legacy(name: String): Path = baseDir.resolve(name)
}
