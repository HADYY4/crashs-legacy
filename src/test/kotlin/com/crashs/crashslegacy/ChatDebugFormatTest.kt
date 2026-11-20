package com.crashs.crashslegacy

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import com.crashs.crashslegacy.chat.ChatDebug
import com.crashs.crashslegacy.chat.ChatDebugFormat
import com.crashs.crashslegacy.text.ChatPart

/**
 * Log layout for `/crashs chatdebug`. A chat line has to stay on one log line and must show exactly
 * the styles that were set, otherwise the dump is useless for developing chat features.
 */
class ChatDebugFormatTest {

    @Test
    fun escapesQuotesBackslashesAndControlCharacters() {
        assertEquals("a\\\"b", ChatDebugFormat.escape("a\"b", 64))
        assertEquals("a\\\\b", ChatDebugFormat.escape("a\\b", 64))
        assertEquals("a\\nb", ChatDebugFormat.escape("a\nb", 64))
        assertEquals("a\\tb", ChatDebugFormat.escape("a\tb", 64))
        assertEquals("a\\u0001b", ChatDebugFormat.escape("a\u0001b", 64))
    }

    @Test
    fun truncatesLongTextWithAnEllipsis() {
        val escaped = ChatDebugFormat.escape("abcdef", 3)
        assertEquals("abc…", escaped)
    }

    @Test
    fun keepsCjkTextUntouched() {
        assertEquals("你获得了 10 金币", ChatDebugFormat.escape("你获得了 10 金币", 64))
    }

    @Test
    fun headerMarksDuplicatesAndEscapesTheText() {
        val normal = ChatDebugFormat.header("chat", 5, 2, "a\"b", duplicate = false)
        assertTrue(normal.startsWith("[ChatDebug] chat dup=false len=5 parts=2 text="), normal)
        assertTrue(normal.contains("\\\""))

        val duplicate = ChatDebugFormat.header("actionbar", 3, 1, "abc", duplicate = true)
        assertTrue(duplicate.startsWith("[ChatDebug] actionbar dup=true len=3 parts=1"), duplicate)
    }

    @Test
    fun plainPartHasNoStyleSuffix() {
        assertEquals("", ChatDebugFormat.describeStyles(ChatPart("x")))
    }

    @Test
    fun describesOnlyTheStylesThatAreSet() {
        val part = ChatPart(
            text = "x",
            color = "#ff5555",
            bold = true,
            underlined = true,
            click = "RUN_COMMAND:/spawn",
        )
        val described = ChatDebugFormat.describeStyles(part)
        assertTrue(described.contains("color=#ff5555"), described)
        assertTrue(described.contains("bold"), described)
        assertTrue(described.contains("underlined"), described)
        assertTrue(described.contains("click=RUN_COMMAND:/spawn"), described)
        assertTrue(!described.contains("italic"), described)
        assertTrue(!described.contains("hover"), described)
    }

    @Test
    fun clickValuesAreEscapedToo() {
        val part = ChatPart(text = "x", click = "RUN_COMMAND:/msg \"a\"")
        assertTrue(ChatDebugFormat.describeStyles(part).contains("\\\""))
    }
}
