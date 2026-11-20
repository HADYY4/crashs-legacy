package com.crashs.crashslegacy

import com.crashs.crashslegacy.feature.lightning.LightningSoundDeduper
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LightningSoundDeduperTest {

    @AfterEach
    fun reset() {
        LightningSoundDeduper.reset()
    }

    @Test
    fun dropsTheSamePositionInsideTheWindow() {
        assertTrue(LightningSoundDeduper.shouldPlay(1.0, 2.0, 3.0, 0L))
        assertFalse(LightningSoundDeduper.shouldPlay(1.0, 2.0, 3.0, 49L))
        assertFalse(LightningSoundDeduper.shouldPlay(1.0, 2.0, 3.0, 50L))
        assertTrue(LightningSoundDeduper.shouldPlay(1.0, 2.0, 3.0, 51L))
    }

    @Test
    fun keepsDifferentPositions() {
        assertTrue(LightningSoundDeduper.shouldPlay(1.0, 2.0, 3.0, 0L))
        assertTrue(LightningSoundDeduper.shouldPlay(4.0, 2.0, 3.0, 1L))
    }
}
