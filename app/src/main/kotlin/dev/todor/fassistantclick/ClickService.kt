package dev.todor.fassistantclick

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Rect
import android.util.Log
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import dev.todor.fassistantclick.gesture.Gestures
import dev.todor.fassistantclick.gesture.Runner
import dev.todor.fassistantclick.overlay.Capture
import dev.todor.fassistantclick.overlay.Marker
import dev.todor.fassistantclick.overlay.Panel
import dev.todor.fassistantclick.script.Kind
import dev.todor.fassistantclick.script.Pt
import dev.todor.fassistantclick.script.Script
import dev.todor.fassistantclick.script.Step
import dev.todor.fassistantclick.script.Store
import dev.todor.fassistantclick.ui.MainActivity

const val TAG = "fclick"

/**
 * The only component that can send a touch, and therefore the one that owns everything touching
 * the screen: the panel, the markers, the point-placing overlay and the run itself.
 *
 * There is no second service behind it. An accessibility service is already bound by the system
 * and long-lived, so a foreground service would add a notification requirement and buy nothing.
 */
class ClickService : AccessibilityService(), Runner.Host {

    companion object {
        @Volatile
        var instance: ClickService? = null
            private set
    }

    val runner = Runner(this)

    private var panel: Panel? = null
    private var capture: Capture? = null
    private val markers = mutableListOf<Marker>()

    val panelVisible: Boolean get() = panel?.visible == true

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        panel = Panel(this)
        capture = Capture(this)
        // The two ceilings, on this phone, once — so a puzzling refusal later has a number to
        // check it against.
        Log.i(TAG, "connected: maxStrokes=${Gestures.maxStrokes} maxDurationMs=${Gestures.maxDurationMs}")
        if (Grants.overlay(this)) showPanel()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        teardown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        teardown()
        super.onDestroy()
    }

    /** Nothing is read from the screen, so there is nothing to do here. */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() {
        stopRun()
    }

    // ---- panel and markers ----

    fun showPanel() {
        if (!Grants.overlay(this)) {
            say(getString(R.string.main_panel_blocked))
            return
        }
        panel?.show()
        refreshMarkers()
    }

    fun hidePanel() {
        panel?.hide()
        clearMarkers()
    }

    /** Called after the loaded script changed somewhere else. */
    fun refresh() {
        refreshMarkers()
        panel?.render()
    }

    /** Called after a size or opacity change, which needs the panel's views rebuilt. */
    fun refreshLook() {
        panel?.refreshLook()
        refreshMarkers()
        panel?.render()
    }

    private fun refreshMarkers() {
        clearMarkers()
        if (panel?.visible != true) return
        val script = Store.loaded(this) ?: return
        val windows = getSystemService(WindowManager::class.java)

        script.steps.forEachIndexed { stepIndex, step ->
            step.points.forEachIndexed { pointIndex, point ->
                val marker = Marker(
                    context = this,
                    windows = windows,
                    label = markerLabel(stepIndex, pointIndex, step),
                    colour = markerColour(step, pointIndex),
                    start = point,
                    // Set up front rather than after the window is added: a marker that is
                    // touchable for even one frame of a run can swallow its own gesture.
                    touchable = !runner.busy,
                    onMoved = { moved -> movePoint(script.id, stepIndex, pointIndex, moved) },
                )
                marker.show()
                markers += marker
            }
        }
    }

    private fun clearMarkers() {
        markers.forEach { it.hide() }
        markers.clear()
    }

    private fun markerLabel(stepIndex: Int, pointIndex: Int, step: Step) =
        if (step.requiredPoints > 1) "${stepIndex + 1}.${pointIndex + 1}" else "${stepIndex + 1}"

    private fun markerColour(step: Step, pointIndex: Int) = when {
        step.kind != Kind.SWIPE && step.kind != Kind.CURVE -> getColor(R.color.marker_tap)
        pointIndex == 0 -> getColor(R.color.marker_from)
        pointIndex == step.requiredPoints - 1 -> getColor(R.color.marker_to)
        else -> getColor(R.color.marker_control)
    }

    private fun movePoint(scriptId: String, stepIndex: Int, pointIndex: Int, to: Pt) {
        val script = Store.find(this, scriptId) ?: return
        val step = script.steps.getOrNull(stepIndex) ?: return
        if (pointIndex !in step.points.indices) return

        val points = step.points.toMutableList().also { it[pointIndex] = to }
        val steps = script.steps.toMutableList().also { it[stepIndex] = step.copy(points = points) }
        Store.save(this, script.copy(steps = steps))
    }

    // ---- placing points ----

    /** The panel's one editing shortcut: append a tap and go straight to placing it. */
    fun addTapStep() {
        val script = Store.loaded(this)
        if (script == null) {
            say(getString(R.string.run_no_script))
            return
        }
        placePoints(script.id, script.steps.size, Step(kind = Kind.TAP))
    }

    fun placeStepPoints(scriptId: String, stepIndex: Int) {
        val step = Store.find(this, scriptId)?.steps?.getOrNull(stepIndex) ?: return
        placePoints(scriptId, stepIndex, step)
    }

    private fun placePoints(scriptId: String, stepIndex: Int, step: Step) {
        if (!Grants.overlay(this)) {
            say(getString(R.string.step_place_blocked))
            return
        }
        val started = capture?.collect(step.requiredPoints) { points ->
            if (points != null) writePoints(scriptId, stepIndex, step, points)
        } ?: false
        if (!started) say(getString(R.string.capture_busy))
    }

    private fun writePoints(scriptId: String, stepIndex: Int, step: Step, points: List<Pt>) {
        val script = Store.find(this, scriptId) ?: return
        val steps = script.steps.toMutableList()
        val placed = step.copy(points = points)
        if (stepIndex in steps.indices) steps[stepIndex] = placed else steps.add(placed)

        Store.save(this, script.copy(steps = steps))
        say(resources.getQuantityString(R.plurals.capture_saved, points.size, points.size))
        refreshMarkers()
        panel?.render()
    }

    // ---- running ----

    fun toggleRun() {
        if (runner.busy) stopRun() else startRun()
    }

    fun startRun() {
        val script = Store.loaded(this)
        if (script == null) {
            say(getString(R.string.run_no_script))
            return
        }
        val problem = problemWith(script)
        if (problem != null) {
            say(problem)
            return
        }
        runner.start(script)
    }

    fun stopRun() {
        val wasBusy = runner.busy
        runner.stop()
        if (wasBusy) say(getString(R.string.run_stopped))
    }

    fun togglePause() {
        when (runner.state) {
            Runner.State.PAUSED -> runner.resume()
            Runner.State.RUNNING -> {
                runner.pause()
                say(getString(R.string.run_pause_pending))
            }
            Runner.State.COUNTDOWN -> runner.pause()
            Runner.State.IDLE -> Unit
        }
    }

    /**
     * Everything that would make the run silently do nothing, checked before it starts. A point
     * under the panel is the one worth spelling out: the panel has to stay touchable so Stop
     * works, so it would eat that gesture and the run would look fine from the outside.
     */
    private fun problemWith(script: Script): String? {
        if (script.steps.isEmpty()) return getString(R.string.run_no_steps)
        val panelRect = panel?.bounds() ?: Rect()

        script.steps.forEachIndexed { index, step ->
            val number = index + 1
            if (!step.placed) return getString(R.string.run_no_points, number)
            if (step.strokeCount > Gestures.maxStrokes) {
                return getString(
                    R.string.run_too_many_strokes,
                    number,
                    step.strokeCount,
                    Gestures.maxStrokes,
                )
            }
            if (step.points.any { panelRect.contains(it.x.toInt(), it.y.toInt()) }) {
                return getString(R.string.run_under_panel, number)
            }
        }
        return null
    }

    override fun dispatch(step: Step, onDone: (Boolean) -> Unit): Boolean {
        val gesture = Gestures.build(step) ?: return false
        return dispatchGesture(
            gesture,
            object : GestureResultCallback() {
                override fun onCompleted(description: GestureDescription?) = onDone(true)
                override fun onCancelled(description: GestureDescription?) = onDone(false)
            },
            null,
        )
    }

    override fun onRunnerChanged() {
        panel?.render()
        markers.forEach { it.setTouchable(!runner.busy) }

        val running = runner.script
        if (runner.busy && running != null) {
            Notifications.showRunning(this, running.name)
        } else {
            Notifications.clearRunning(this)
        }
    }

    override fun onRunnerRejected(stepNumber: Int) {
        say(getString(R.string.run_rejected, stepNumber))
    }

    override fun onRunnerFinished() {
        say(getString(R.string.run_finished))
    }

    // ---- odds and ends ----

    fun openApp() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        )
    }

    private fun say(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }

    private fun teardown() {
        runner.stop()
        capture?.cancel()
        clearMarkers()
        panel?.hide()
        Notifications.clearRunning(this)
        panel = null
        capture = null
        instance = null
    }
}
