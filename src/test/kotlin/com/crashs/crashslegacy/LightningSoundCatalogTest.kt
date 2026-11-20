package com.crashs.crashslegacy

import com.crashs.crashslegacy.feature.lightning.LightningSoundCatalog
import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The static catalog and the bundled sounds.json must stay in sync. */
class LightningSoundCatalogTest {

    @Test
    fun filesAreGroupedByNumericSuffix() {
        assertEquals(36, LightningSoundCatalog.FILES.size)
        assertEquals(24, LightningSoundCatalog.GROUPS.size)
        assertEquals(6, LightningSoundCatalog.filesOf("boykisser").size)
        assertEquals(listOf("click-1", "click-2", "click-3"), LightningSoundCatalog.filesOf("click"))
        assertEquals(listOf("meow"), LightningSoundCatalog.filesOf("meow"))
    }

    @Test
    fun everyGroupHasASoundsJsonEntry() {
        val stream = javaClass.getResourceAsStream("/assets/crashs-legacy/sounds.json")
            ?: error("sounds.json missing from the test classpath")
        val root = stream.use { reader ->
            JsonParser.parseReader(reader.bufferedReader()).asJsonObject
        }
        for (group in LightningSoundCatalog.NAMES) {
            assertTrue(root.has("lightning.$group"), "sounds.json missing lightning.$group")
        }
    }
}
