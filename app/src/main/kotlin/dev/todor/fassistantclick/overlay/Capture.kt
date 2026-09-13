package dev.todor.fassistantclick.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import dev.todor.fassistantclick.R
import dev.todor.fassistantclick.script.Pt
import dev.todor.fassistantclick.ui.dpf

/**
 * A full-screen window that collects the points for one step: you tap where the gesture belongs
 * and it writes down the raw screen coordinates.
 *
 * It swallows the taps rather than passing them through, so the app underneath does not react
 * while you are aiming. That is a deliberate trade — reading another app's touches without
 * consuming them is not something the accessibility API offers.
 */
internal class Capture(private val context: Context) {

    private val windows = context.getSystemService(WindowManager::class.java)
    private val collected = mutableListOf<Pt>()

    private var root: View? = null
    private var hint: TextView? = null
    private var wanted = 0
    private var report: ((List<Pt>?) -> Unit)? = null

    val active: Boolean get() = root != null

    /** Returns false when a capture is already up. */
    fun collect(count: Int, onDone: (List<Pt>?) -> Unit): Boolean {
        if (active || count <= 0) return false

        wanted = count
        report = onDone
        collected.clear()

        val view = build()
        root = view
        windows.addView(
            view,
            overlayParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                touchable = true,
            ),
        )
        showProgress()
        return true
    }

    fun cancel() {
        if (active) finish(null)
    }

    private fun finish(points: List<Pt>?) {
        root?.detachFrom(windows)
        root = null
        hint = null
        val callback = report
        report = null
        callback?.invoke(points)
    }

    private fun showProgress() {
        hint?.text = context.getString(R.string.capture_hint, collected.size + 1, wanted)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun build(): View {
        val scrim = View(context).apply {
            setBackgroundColor(OVERLAY_SCRIM)
            setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_UP) {
                    collected += Pt(event.rawX, event.rawY)
                    if (collected.size >= wanted) finish(collected.toList()) else showProgress()
                }
                true
            }
        }

        val heading = TextView(context).apply {
            setTextColor(OVERLAY_INK)
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
        }
        hint = heading

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = context.overlayGround(1f)
            setPadding(context.dpf(14f), context.dpf(12f), context.dpf(14f), context.dpf(12f))
            // Clickable so a tap meant for Cancel is not also recorded as a point.
            isClickable = true
            addView(heading)
            addView(
                TextView(context).apply {
                    setTextColor(OVERLAY_INK)
                    textSize = 12f
                    alpha = 0.8f
                    text = context.getString(R.string.capture_sub)
                    setPadding(0, context.dpf(4f), 0, context.dpf(10f))
                }
            )
            addView(
                LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    addView(
                        context.overlayPill(context.getString(R.string.capture_cancel), 1f) {
                            finish(null)
                        }
                    )
                }
            )
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP,
            ).apply {
                leftMargin = context.dpf(12f)
                rightMargin = context.dpf(12f)
                topMargin = context.dpf(40f)
            }
        }

        return FrameLayout(context).apply {
            addView(
                scrim,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
            addView(card)
        }
    }
}
