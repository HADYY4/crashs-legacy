package com.crashs.crashslegacy.serverdamage

import java.util.concurrent.atomic.AtomicLong

/**
 * Temporary diagnostics for "which channel actually moves the damage text".
 *
 * Toggled by `/crashs debug damage`: the first run arms the counters and clears them, the second
 * run prints a summary. While armed each hook is a boolean check plus an atomic increment; while
 * disarmed the hooks return immediately, so leaving this in the build costs nothing during play.
 *
 * Channels:
 * - A: entity position packets (`onEntity` relative, `onEntityPosition` absolute,
 *   `onEntityPositionSync` full sync);
 * - B: `EntityTrackerUpdate` carrying a translation/transform vector (DisplayEntity animates the
 *   offset locally, no position packet involved);
 * - C: `EntityVelocityUpdate`.
 */
object ServerDamageDebug {

    @Volatile
    var recording: Boolean = false
        private set

    private val relativeMoves = AtomicLong()
    private val absoluteMoves = AtomicLong()
    private val syncMoves = AtomicLong()
    private val velocityUpdates = AtomicLong()
    private val trackerUpdates = AtomicLong()
    private val trackerWithVector = AtomicLong()
    private val frozenCancels = AtomicLong()

    fun begin(): List<String> {
        reset()
        recording = true
        return listOf(
            "[DamageDebug] 开始记录：打几下怪让伤害数字出现并飘起来，然后再执行一次 /crashs debug damage 看结果",
        )
    }

    fun end(): List<String> {
        recording = false
        val rel = relativeMoves.get()
        val abs = absoluteMoves.get()
        val sync = syncMoves.get()
        val velocity = velocityUpdates.get()
        val trackers = trackerUpdates.get()
        val vectors = trackerWithVector.get()
        val frozen = frozenCancels.get()
        val lines = listOf(
            "[DamageDebug] A 位置包：相对(Entity)=$rel  绝对(EntityPosition)=$abs  同步(EntityPositionSync)=$sync",
            "[DamageDebug] B 追踪数据：伤害文本 tracker=$trackers（其中含向量/变换=$vectors）",
            "[DamageDebug] C 速度包：$velocity",
            "[DamageDebug] 被『禁止实体移动』丢弃的位置包：$frozen",
            "[DamageDebug] 结论：${verdict(rel, abs, sync, vectors, velocity)}",
        )
        reset()
        return lines
    }

    fun onRelativeMove() {
        if (recording) relativeMoves.incrementAndGet()
    }

    fun onAbsoluteMove() {
        if (recording) absoluteMoves.incrementAndGet()
    }

    fun onSyncMove() {
        if (recording) syncMoves.incrementAndGet()
    }

    fun onVelocity() {
        if (recording) velocityUpdates.incrementAndGet()
    }

    fun onTracker(hasVector: Boolean) {
        if (!recording) return
        trackerUpdates.incrementAndGet()
        if (hasVector) trackerWithVector.incrementAndGet()
    }

    fun onFrozenCancel() {
        if (recording) frozenCancels.incrementAndGet()
    }

    private fun reset() {
        relativeMoves.set(0)
        absoluteMoves.set(0)
        syncMoves.set(0)
        velocityUpdates.set(0)
        trackerUpdates.set(0)
        trackerWithVector.set(0)
        frozenCancels.set(0)
    }

    private fun verdict(relative: Long, absolute: Long, sync: Long, vectors: Long, velocity: Long): String {
        val positions = relative + absolute + sync
        return when {
            positions == 0L && vectors == 0L && velocity == 0L ->
                "没记录到位移数据（伤害文本没出现，或被『移除伤害显示』提前丢弃）"
            positions > 0L && vectors == 0L -> "A 通道（位置包）"
            positions == 0L && vectors > 0L -> "B 通道（追踪数据里的变换/插值）"
            positions > 0L && vectors > 0L -> "A+B 都有：位置包可能只负责首次定位，飘动来自 B 的变换"
            else -> "C 通道（速度包）"
        }
    }
}
