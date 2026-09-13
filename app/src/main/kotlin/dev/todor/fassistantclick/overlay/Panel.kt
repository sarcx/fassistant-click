package dev.todor.fassistantclick.overlay

import android.annotation.SuppressLint
import android.graphics.Rect
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import dev.todor.fassistantclick.ClickService
import dev.todor.fassistantclick.Prefs
import dev.todor.fassistantclick.R
import dev.todor.fassistantclick.gesture.Runner
import dev.todor.fassistantclick.script.Store
import dev.todor.fassistantclick.ui.dpf
import dev.todor.fassistantclick.ui.stepCount
import kotlin.math.abs

/**
 * The floating control panel. Never takes input focus, so the app underneath keeps its keyboard
 * and its selection while the panel sits on top of it.
 *
 * It stays touchable during a run, because Stop has to be reachable — which is exactly why the
 * service refuses to start a run whose points sit under it.
 */
internal class Panel(private val service: ClickService) {

    private val windows = service.getSystemService(WindowManager::class.java)
    private val params = overlayParams(
        ViewGroup.LayoutParams.WRAP_CONTENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
        touchable = true,
    )

    private var root: LinearLayout? = null
    private var status: TextView? = null
    private var startStop: TextView? = null
    private var pauseResume: TextView? = null

    private var downX = 0f
    private var downY = 0f
    private var originX = 0
    private var originY = 0
    private var dragged = false

    val visible: Boolean get() = root != null

    init {
        val saved = Prefs.panelPosition(service)
        params.x = saved?.x ?: service.dpf(12f)
        params.y = saved?.y ?: service.dpf(140f)
    }

    fun show() {
        if (visible) return
        val view = build()
        root = view
        windows.addView(view, params)
        render()
    }

    fun hide() {
        val view = root ?: return
        view.detachFrom(windows)
        root = null
        status = null
        startStop = null
        pauseResume = null
    }

    /** Rebuilds the view so a size or opacity change shows up immediately. */
    fun refreshLook() {
        if (!visible) return
        hide()
        show()
    }

    fun render() {
        val runner = service.runner

        status?.text = when (runner.state) {
            // The only branch that reads the store. The others run once per gesture, and a disk
            // read plus a JSON parse per tap at a 50 ms repeat rate is not free.
            Runner.State.IDLE -> {
                val script = Store.loaded(service)
                if (script == null) {
                    service.getString(R.string.panel_no_script)
                } else {
                    service.getString(R.string.panel_idle, script.name, service.stepCount(script))
                }
            }

            Runner.State.COUNTDOWN ->
                service.getString(R.string.panel_countdown, runner.countdownLeft)

            Runner.State.RUNNING -> {
                val running = runner.script
                if (running == null || running.forever) {
                    service.getString(R.string.panel_running_forever, runner.pass, runner.stepIndex + 1)
                } else {
                    service.getString(
                        R.string.panel_running,
                        runner.pass,
                        running.repeats,
                        runner.stepIndex + 1,
                    )
                }
            }

            Runner.State.PAUSED ->
                service.getString(R.string.panel_paused, runner.stepIndex + 1)
        }

        startStop?.apply {
            text = service.getString(if (runner.busy) R.string.panel_stop else R.string.panel_start)
            setTextColor(if (runner.busy) OVERLAY_DANGER else OVERLAY_ACCENT)
        }

        pauseResume?.apply {
            visibility = if (runner.busy) View.VISIBLE else View.GONE
            text = service.getString(
                if (runner.state == Runner.State.PAUSED) R.string.panel_resume else R.string.panel_pause
            )
        }
    }

    fun bounds(): Rect {
        val view = root ?: return Rect()
        val width = if (view.width > 0) view.width else view.measuredWidth
        val height = if (view.height > 0) view.height else view.measuredHeight
        return Rect(params.x, params.y, params.x + width, params.y + height)
    }

    private fun build(): LinearLayout {
        val scale = Prefs.panelSize(service) / 100f
        val space = service.dpf(8f * scale)

        val handle = TextView(service).apply {
            setTextColor(OVERLAY_INK)
            textSize = 12f * scale
            setTypeface(typeface, Typeface.BOLD)
            setPadding(service.dpf(3f * scale), 0, 0, service.dpf(5f * scale))
        }
        status = handle

        val buttons = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(service.overlayPill(service.getString(R.string.panel_add_tap), scale) { service.addTapStep() })
            addView(
                service.overlayPill(service.getString(R.string.panel_start), scale) { service.toggleRun() }
                    .also { startStop = it }
            )
            addView(
                service.overlayPill(service.getString(R.string.panel_pause), scale) { service.togglePause() }
                    .also { pauseResume = it }
            )
            addView(service.overlayPill(service.getString(R.string.panel_app), scale) { service.openApp() })
            addView(service.overlayPill(service.getString(R.string.panel_hide), scale) { service.hidePanel() })
        }

        return LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            background = service.overlayGround(scale)
            setPadding(space, space, space, space)
            alpha = Prefs.panelOpacity(service) / 100f
            addView(handle)
            addView(buttons)
            attachDragging(handle)
            // So bounds() has a usable answer if Start is pressed before the first layout pass.
            measure(
                View.MeasureSpec.makeMeasureSpec(
                    service.resources.displayMetrics.widthPixels,
                    View.MeasureSpec.AT_MOST,
                ),
                View.MeasureSpec.UNSPECIFIED,
            )
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun attachDragging(handle: View) {
        handle.setOnTouchListener { _, event ->
            val view = root ?: return@setOnTouchListener false
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
                    if (abs(event.rawX - downX) > service.dpf(4f) ||
                        abs(event.rawY - downY) > service.dpf(4f)
                    ) {
                        dragged = true
                    }
                    windows.updateViewLayout(view, params)
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (dragged) Prefs.setPanelPosition(service, params.x, params.y)
                    true
                }

                else -> false
            }
        }
    }
}
