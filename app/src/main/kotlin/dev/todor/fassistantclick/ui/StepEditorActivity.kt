package dev.todor.fassistantclick.ui

import android.os.Bundle
import android.widget.Toast
import dev.todor.fassistantclick.ClickService
import dev.todor.fassistantclick.R
import dev.todor.fassistantclick.gesture.Gestures
import dev.todor.fassistantclick.script.Kind
import dev.todor.fassistantclick.script.Step
import dev.todor.fassistantclick.script.Store

class StepEditorActivity : SubScreen() {

    companion object {
        const val EXTRA_SCRIPT_ID = "scriptId"
        const val EXTRA_STEP_INDEX = "stepIndex"
    }

    private var scriptId = ""
    private var stepIndex = 0

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        scriptId = intent.getStringExtra(EXTRA_SCRIPT_ID).orEmpty()
        stepIndex = intent.getIntExtra(EXTRA_STEP_INDEX, 0)
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val step = Store.find(this, scriptId)?.steps?.getOrNull(stepIndex)
        if (step == null) {
            finish()
            return
        }
        title = getString(R.string.step_title, stepIndex + 1)

        val content = verticalLayout()

        content.addView(
            spinnerField(
                getString(R.string.step_kind),
                Kind.entries.map { getString(it.labelRes) },
                Kind.entries.indexOf(step.kind),
            ) { picked ->
                update { it.withKind(Kind.entries[picked]) }
                ClickService.instance?.refresh()
                render()
            }
        )

        content.addView(
            numberField(
                getString(
                    if (step.kind == Kind.TAP || step.kind == Kind.MULTI_TAP) {
                        R.string.step_duration_tap
                    } else {
                        R.string.step_duration
                    }
                ),
                step.durationMs,
            ) { value -> update { it.copy(durationMs = value) } }
        )

        content.addView(
            numberField(getString(R.string.step_delay), step.delayMs) { value ->
                update { it.copy(delayMs = value) }
            }
        )

        if (step.kind == Kind.MULTI_TAP) {
            content.addView(
                numberField(getString(R.string.step_fingers), step.fingers.toLong()) { value ->
                    update { it.copy(fingers = value.toInt().coerceIn(2, Gestures.maxStrokes)) }
                }
            )
        }

        content.addView(heading(getString(R.string.step_points)))
        content.addView(
            body(
                resources.getQuantityString(
                    R.plurals.step_place_needs,
                    step.requiredPoints,
                    getString(step.kind.labelRes),
                    step.requiredPoints,
                )
            )
        )
        content.addView(
            caption(
                if (step.placed) {
                    getString(
                        R.string.editor_step_placed,
                        step.points.joinToString { "(${it.x.toInt()}, ${it.y.toInt()})" },
                    )
                } else {
                    getString(R.string.editor_step_unplaced)
                }
            )
        )
        content.addView(button(getString(R.string.step_place)) { place() })

        content.addView(spacer(20))
        content.addView(button(getString(R.string.step_delete)) { deleteStep() })

        setContentView(scrolling(content))
    }

    /**
     * Reads the step back from disk before changing it, because every field reports on each
     * keystroke and a captured copy would undo whatever was typed in the field before it.
     */
    private fun update(transform: (Step) -> Step) {
        val script = Store.find(this, scriptId) ?: return
        val current = script.steps.getOrNull(stepIndex) ?: return
        val steps = script.steps.toMutableList().also { it[stepIndex] = transform(current) }
        Store.save(this, script.copy(steps = steps))
    }

    private fun deleteStep() {
        val script = Store.find(this, scriptId) ?: return
        if (stepIndex !in script.steps.indices) return
        val steps = script.steps.toMutableList().also { it.removeAt(stepIndex) }
        Store.save(this, script.copy(steps = steps))
        ClickService.instance?.refresh()
        finish()
    }

    private fun place() {
        val service = ClickService.instance
        if (service == null) {
            Toast.makeText(this, R.string.run_service_off, Toast.LENGTH_SHORT).show()
            return
        }
        service.placeStepPoints(scriptId, stepIndex)
        moveTaskToBack(true)
    }
}
