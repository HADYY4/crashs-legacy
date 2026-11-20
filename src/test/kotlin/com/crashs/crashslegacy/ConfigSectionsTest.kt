package com.crashs.crashslegacy

import com.crashs.crashslegacy.config.ConfigSections
import com.crashs.crashslegacy.config.CrashsLegacyConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Every config field must be classified into exactly one file of the split config. */
class ConfigSectionsTest {

    private val fields: Set<String> =
        CrashsLegacyConfig::class.java.declaredFields
            .filter { !java.lang.reflect.Modifier.isStatic(it.modifiers) && !it.isSynthetic }
            .map { it.name }
            .toSet()

    @Test
    fun everyConfigFieldHasExactlyOneSection() {
        val mapped = ConfigSections.FILES.values.flatten()
        assertEquals(mapped.size, mapped.toSet().size, "a field is listed in more than one section")
        assertEquals(fields, mapped.toSet(), "config fields and ConfigSections are out of sync")
    }

    @Test
    fun everySectionFieldExistsOnTheConfig() {
        ConfigSections.ALL_FIELDS.forEach { field ->
            assertNotNull(ConfigSections.fileOf(field), "no file for $field")
            assertTrue(fields.contains(field), "unknown field $field")
        }
    }
}
