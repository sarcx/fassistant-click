package dev.todor.fassistantclick.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import dev.todor.fassistantclick.Prefs
import dev.todor.fassistantclick.script.Pt
import dev.todor.fassistantclick.ui.dpf
import kotlin.math.abs

/**
 * One point of one step, as its own overlay window so it can be dragged with a finger.
 *
 * The window is touchable while you are aiming and not touchable while a run is going. That
 * second state is the whole reason this is a separate window: a dispatched tap is a real touch,
 * so a touchable marker sitting exactly where the tap lands would swallow its own gesture.
 */
internal class Marker(
    private val context: Context,
    private val windows: WindowManager,
    private val label: String,
    private val colour: Int,
    start: Pt,
    touchable: Boolean,
    private val onMoved: (Pt) -> Unit,
) {
    private val size = context.dpf(44f * Prefs.markerSize(context) / 100f)
    private val slop = context.dpf(4f)
    private val view = MarkerView(context, label, colour)
    private val params = overlayParams(size, size, touchable)

    private var touchable = touchable
    private var attached = false
    private var downX = 0f
    private var downY = 0f
    private var originX = 0
    private var originY = 0
    private var dragged = false

    var point: Pt = start
        private set

    private companion object {
        /** How much a marker fades while a run has it locked. */
        const val DIMMED = 0.65f
    }

    init {
        params.x = (start.x - size / 2f).toInt()
        params.y = (start.y - size / 2f).toInt()
        view.alpha = if (touchable) 1f else DIMMED
        attachDragging()
    }

    fun show() {
        if (attached) return
        windows.addView(view, params)
        attached = true
    }

    fun hide() {
        if (!attached) return
        view.detachFrom(windows)
        attached = false
    }

    /**
     * A no-op when nothing changes. The runner reports on every step, and pushing a window
     * update per marker per gesture would be a lot of churn at a 50 ms repeat rate.
     */
    fun setTouchable(value: Boolean) {
        if (touchable == value) return
        touchable = value
        params.setTouchable(value)
        view.alpha = if (value) 1f else DIMMED
        if (attached) windows.updateViewLayout(view, params)
    }

    fun bounds() = Rect(params.x, params.y, params.x + size, params.y + size)

    @SuppressLint("ClickableViewAccessibility")
    private fun attachDragging() {
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    originX = params.x
                    originY = params.y
                    dragged = false
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    params.x = originX + (event.rawX - downX).toInt()
                    params.y = originY + (event.rawY - downY).toInt()
                    if (abs(event.rawX - downX) > slop || abs(event.rawY - downY) > slop) dragged = true
                    if (attached) windows.updateViewLayout(view, params)
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    point = Pt(params.x + size / 2f, params.y + size / 2f)
                    if (dragged) onMoved(point)
                    true
                }

                else -> false
            }
        }
    }

    private class MarkerView(
        context: Context,
        private val label: String,
        private val colour: Int,
    ) : View(context) {

        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = (colour and 0x00FFFFFF) or 0x55000000
        }
        private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = colour
            strokeWidth = context.dpf(2f).toFloat()
        }
        private val cross = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.WHITE
            strokeWidth = context.dpf(1f).toFloat()
        }
        private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            textSize = context.dpf(11f).toFloat()
            isFakeBoldText = true
        }

        override fun onDraw(canvas: Canvas) {
            val centreX = width / 2f
            val centreY = height / 2f
            val radius = minOf(width, height) / 2f - ring.strokeWidth

            canvas.drawCircle(centreX, centreY, radius, fill)
            canvas.drawCircle(centreX, centreY, radius, ring)

            val arm = radius * 0.55f
            canvas.drawLine(centreX - arm, centreY, centreX + arm, centreY, cross)
            canvas.drawLine(centreX, centreY - arm, centreX, centreY + arm, cross)

            canvas.drawText(label, centreX, centreY - radius * 0.28f, text)
        }
    }
}
