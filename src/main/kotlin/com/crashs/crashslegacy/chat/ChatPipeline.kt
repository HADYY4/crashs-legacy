package com.crashs.crashslegacy.chat

/** One chat processing step. Returning true means "consume": keep the message out of chat. */
fun interface ChatHandler<E> {
    fun handle(event: E): Boolean
}

/**
 * Ordered handler chain for incoming chat, with packet-level dedupe.
 *
 * Semantics (aggregate): **every** handler runs, even when an earlier one already asked to consume —
 * hiding a chat line must not stop accounting side effects such as kill counting or coin totals. The
 * aggregated decision cancels the packet at the end.
 *
 * The first dispatch of a packet remembers its decision; a second delivery of the same packet within
 * [dedupeWindowMs] returns it without running the handlers again. The clock is monotonic and
 * injectable so the window is testable; [fingerprint] guards against identity-hash collisions.
 */
class ChatPipeline<E>(
    private val dedupeWindowMs: Long = 2_000L,
    private val clock: () -> Long = { System.nanoTime() / 1_000_000L },
    private val onError: (Throwable) -> Unit = {},
) {

    data class Result(val consume: Boolean, val duplicate: Boolean)

    private data class Entry(
        val at: Long,
        val fingerprint: Int,
        val consume: Boolean,
        val processing: Boolean,
    )

    private val handlers = ArrayList<ChatHandler<E>>()
    private val recent = LinkedHashMap<Long, Entry>()

    fun register(handler: ChatHandler<E>): ChatPipeline<E> = apply { handlers += handler }

    fun handlerCount(): Int = handlers.size

    fun clear() {
        synchronized(recent) { recent.clear() }
    }

    /**
     * @param packetKey identifies the packet object (identity hash of the packet).
     * @param fingerprint cheap content hash, so a key collision cannot swallow a different message.
     */
    fun dispatch(packetKey: Long, fingerprint: Int, event: E): Result {
        synchronized(recent) {
            val now = clock()
            recent.entries.removeIf { now - it.value.at > dedupeWindowMs }
            val previous = recent[packetKey]
            if (previous != null && previous.fingerprint == fingerprint) {
                return Result(previous.consume, duplicate = true)
            }
            // Reserve the slot before running handlers so a concurrent duplicate is deduped too.
            recent[packetKey] = Entry(now, fingerprint, consume = false, processing = true)
        }

        var consume = false
        for (handler in handlers) {
            consume = try {
                handler.handle(event) || consume
            } catch (error: Throwable) {
                onError(error)
                consume
            }
        }

        synchronized(recent) {
            recent[packetKey] = Entry(clock(), fingerprint, consume, processing = false)
        }
        return Result(consume, duplicate = false)
    }
}
