package dev.todor.fassistantclick.gesture

import android.accessibilityservice.GestureDescription
import android.graphics.Path
import dev.todor.fassistantclick.script.Kind
import dev.todor.fassistantclick.script.Pt
import dev.todor.fassistantclick.script.Step

/**
 * Turns a [Step] into the one thing Android accepts: a [GestureDescription] holding one stroke
 * per finger, each a path with a start offset and a duration.
 *
 * The two ceilings are read from the platform rather than assumed, because the sibling repo
 * learned that guessing what a phone allows is how you ship something that silently does nothing.
 */
object Gestures {
    val maxStrokes: Int = GestureDescription.getMaxStrokeCount()
    val maxDurationMs: Long = GestureDescription.getMaxGestureDuration()

    fun build(step: Step): GestureDescription? {
        if (!step.placed || step.strokeCount > maxStrokes) return null
        val duration = step.durationMs.coerceIn(1L, maxDurationMs)
        val builder = GestureDescription.Builder()
        pathsFor(step).forEach { path ->
            builder.addStroke(GestureDescription.StrokeDescription(path, 0L, duration))
        }
        return builder.build()
    }

    private fun pathsFor(step: Step): List<Path> {
        val points = step.points.map { Pt(it.x.coerceAtLeast(0f), it.y.coerceAtLeast(0f)) }
        return when (step.kind) {
            Kind.TAP -> listOf(dot(points[0]))
            Kind.MULTI_TAP -> points.map { dot(it) }
            Kind.SWIPE -> listOf(line(points[0], points[1]))
            Kind.CURVE -> listOf(curve(points[0], points[1], points[2]))
            Kind.PINCH_IN -> {
                val middle = midpoint(points[0], points[1])
                listOf(line(points[0], middle), line(points[1], middle))
            }
            Kind.PINCH_OUT -> {
                val middle = midpoint(points[0], points[1])
                listOf(line(middle, points[0]), line(middle, points[1]))
            }
        }
    }

    /**
     * A tap is a stroke that goes nowhere. The second point is what keeps the path non-empty —
     * StrokeDescription rejects a path with no operations in it.
     */
    private fun dot(point: Pt) = Path().apply {
        moveTo(point.x, point.y)
        lineTo(point.x, point.y)
    }

    private fun line(from: Pt, to: Pt) = Path().apply {
        moveTo(from.x, from.y)
        lineTo(to.x, to.y)
    }

    private fun curve(from: Pt, control: Pt, to: Pt) = Path().apply {
        moveTo(from.x, from.y)
        quadTo(control.x, control.y, to.x, to.y)
    }

    private fun midpoint(first: Pt, second: Pt) =
        Pt((first.x + second.x) / 2f, (first.y + second.y) / 2f)
}
