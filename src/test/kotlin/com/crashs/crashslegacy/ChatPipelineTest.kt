package com.crashs.crashslegacy

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import com.crashs.crashslegacy.chat.ChatHandler
import com.crashs.crashslegacy.chat.ChatPipeline

/**
 * The chat pipeline owns two behaviours that used to be implicit inside `handleMessage`: the
 * packet-level dedupe window and the aggregate consume decision. Both are pure logic here, with an
 * injected clock so the window can be tested without sleeping.
 */
class ChatPipelineTest {

    private var now = 0L
    private val seen = mutableListOf<String>()

    private fun pipeline(vararg decisions: Boolean): ChatPipeline<String> {
        val pipeline = ChatPipeline<String>(dedupeWindowMs = 1_000L, clock = { now })
        decisions.forEach { decision ->
            pipeline.register(
                ChatHandler { event ->
                    seen += event
                    decision
                },
            )
        }
        return pipeline
    }

    @Test
    fun handlersRunInRegistrationOrder() {
        val pipeline = ChatPipeline<String>(clock = { now })
            .register(ChatHandler { event -> seen += "first:$event"; false })
            .register(ChatHandler { event -> seen += "second:$event"; false })
        pipeline.dispatch(packetKey = 1L, fingerprint = 7, event = "hi")
        assertEquals(listOf("first:hi", "second:hi"), seen)
    }

    @Test
    fun hidingTheLineDoesNotStopLaterHandlers() {
        // Aggregate semantics: the broadcast handler may consume, the kill handler still runs.
        val pipeline = ChatPipeline<String>(clock = { now })
        val order = mutableListOf<String>()
        pipeline.register(ChatHandler { order += "broadcast"; true })
        pipeline.register(ChatHandler { order += "kill"; false })

        val result = pipeline.dispatch(packetKey = 2L, fingerprint = 1, event = "msg")

        assertTrue(result.consume, "one consumer is enough to cancel the packet")
        assertEquals(listOf("broadcast", "kill"), order, "later handlers must still run")
    }

    @Test
    fun aSecondDeliveryWithinTheWindowIsDeduped() {
        val pipeline = pipeline(false)
        val first = pipeline.dispatch(3L, 5, "hi")
        assertFalse(first.duplicate)

        now += 500
        val second = pipeline.dispatch(3L, 5, "hi")
        assertTrue(second.duplicate, "same packet within the window")
        assertEquals(first.consume, second.consume)
        assertEquals(listOf("hi"), seen, "handlers must not run twice")
    }

    @Test
    fun aReplayKeepsTheFirstConsumeDecision() {
        val pipeline = pipeline(true)
        assertTrue(pipeline.dispatch(4L, 1, "hide me").consume)
        now += 100
        val replay = pipeline.dispatch(4L, 1, "hide me")
        assertTrue(replay.duplicate)
        assertTrue(replay.consume, "the replay must not let the line through")
    }

    @Test
    fun afterTheWindowTheMessageIsProcessedAgain() {
        val pipeline = pipeline(false)
        pipeline.dispatch(5L, 1, "hi")
        now += 1_001
        val result = pipeline.dispatch(5L, 1, "hi")
        assertFalse(result.duplicate)
        assertEquals(2, seen.size)
    }

    @Test
    fun aKeyCollisionWithDifferentTextIsNotADuplicate() {
        val pipeline = pipeline(false)
        pipeline.dispatch(6L, 111, "first")
        val second = pipeline.dispatch(6L, 222, "second")
        assertFalse(second.duplicate, "different fingerprint means a different message")
        assertEquals(listOf("first", "second"), seen)
    }

    @Test
    fun aFailingHandlerDoesNotStopTheChain() {
        val errors = mutableListOf<String>()
        val pipeline = ChatPipeline<String>(clock = { now }, onError = { errors += it.message ?: "?" })
        pipeline.register(ChatHandler { throw IllegalStateException("boom") })
        pipeline.register(ChatHandler { seen += "after"; true })

        val result = pipeline.dispatch(7L, 1, "hi")

        assertEquals(1, errors.size)
        assertTrue(result.consume, "the surviving handler still decides")
        assertEquals(listOf("after"), seen)
    }

    @Test
    fun clearingForgetsTheWindow() {
        val pipeline = pipeline(false)
        pipeline.dispatch(8L, 1, "hi")
        pipeline.clear()
        val result = pipeline.dispatch(8L, 1, "hi")
        assertFalse(result.duplicate)
    }

    @Test
    fun staleEntriesArePurged() {
        val pipeline = pipeline(false)
        repeat(10) { index ->
            now += 100
            pipeline.dispatch(index.toLong(), index, "m$index")
        }
        // Nothing to assert directly on the map; the purge must at least not grow unbounded and
        // must keep the recent entry deduped.
        val replay = pipeline.dispatch(9L, 9, "m9")
        assertTrue(replay.duplicate)
    }
}
