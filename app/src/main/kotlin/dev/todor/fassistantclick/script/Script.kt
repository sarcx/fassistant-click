package dev.todor.fassistantclick.script

import dev.todor.fassistantclick.R

/**
 * Every gesture the app can send, and how many points on the screen each one needs.
 *
 * [fixedPoints] of 0 means the count is not fixed — [Step.fingers] decides it.
 */
enum class Kind(val labelRes: Int, val fixedPoints: Int, val defaultDurationMs: Long) {
    TAP(R.string.kind_tap, 1, 60L),
    SWIPE(R.string.kind_swipe, 2, 300L),
    CURVE(R.string.kind_curve, 3, 400L),
    PINCH_IN(R.string.kind_pinch_in, 2, 400L),
    PINCH_OUT(R.string.kind_pinch_out, 2, 400L),
    MULTI_TAP(R.string.kind_multi_tap, 0, 60L),
}

/** A point in screen pixels, which is what dispatchGesture works in. */
data class Pt(val x: Float, val y: Float)

data class Step(
    val kind: Kind = Kind.TAP,
    val points: List<Pt> = emptyList(),
    val durationMs: Long = Kind.TAP.defaultDurationMs,
    val delayMs: Long = 300L,
    val fingers: Int = 2,
) {
    val requiredPoints: Int get() = if (kind.fixedPoints == 0) fingers else kind.fixedPoints

    /** How many fingers the phone has to put down at once for this step. */
    val strokeCount: Int get() = when (kind) {
        Kind.PINCH_IN, Kind.PINCH_OUT -> 2
        Kind.MULTI_TAP -> fingers
        else -> 1
    }

    val placed: Boolean get() = points.size == requiredPoints

    /**
     * Keeps [points] and [fingers] consistent with a newly chosen [kind], because a swipe's two
     * points are not a curve's three and a stale list would silently produce the wrong gesture.
     */
    fun withKind(newKind: Kind): Step {
        val duration = if (durationMs == kind.defaultDurationMs) newKind.defaultDurationMs else durationMs
        val kept = if (newKind == kind) points else emptyList()
        return copy(kind = newKind, points = kept, durationMs = duration)
    }
}

data class Script(
    val id: String,
    val name: String,
    val steps: List<Step> = emptyList(),
    /** Passes over [steps]. Zero means keep going until stopped. */
    val repeats: Int = 1,
    val countdownMs: Long = 3_000L,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val forever: Boolean get() = repeats <= 0
}
