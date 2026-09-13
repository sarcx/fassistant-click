package dev.todor.fassistantclick.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import dev.todor.fassistantclick.ClickService
import dev.todor.fassistantclick.R
import dev.todor.fassistantclick.script.Kind
import dev.todor.fassistantclick.script.Script
import dev.todor.fassistantclick.script.Step
import dev.todor.fassistantclick.script.Store

class ScriptEditorActivity : SubScreen() {

    companion object {
        const val EXTRA_SCRIPT_ID = "scriptId"
    }

    private var scriptId = ""

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        scriptId = intent.getStringExtra(EXTRA_SCRIPT_ID).orEmpty()
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val script = Store.find(this, scriptId)
        if (script == null) {
            finish()
            return
        }
        title = script.name

        val content = verticalLayout()

        content.addView(heading(getString(R.string.editor_steps)))
        if (script.steps.isEmpty()) {
            content.addView(body(getString(R.string.editor_empty)))
        } else {
            script.steps.forEachIndexed { index, step ->
                content.addView(stepRow(script, index, step))
                content.addView(divider())
            }
        }

        content.addView(
            button(getString(R.string.editor_add_step)) {
                save(script.copy(steps = script.steps + Step(kind = Kind.TAP)))
                render()
            }
        )

        content.addView(
            numberField(getString(R.string.editor_repeats), script.repeats.toLong()) { value ->
                save(Store.find(this, scriptId)?.copy(repeats = value.toInt()))
            }
        )
        content.addView(
            caption(
                if (script.forever) {
                    getString(R.string.editor_repeats_forever)
                } else {
                    getString(R.string.editor_repeats_hint)
                }
            )
        )
        content.addView(
            numberField(getString(R.string.editor_countdown), script.countdownMs) { value ->
                save(Store.find(this, scriptId)?.copy(countdownMs = value))
            }
        )

        content.addView(spacer(16))
        content.addView(button(getString(R.string.editor_run)) { run() })

        setContentView(scrolling(content))
    }

    private fun stepRow(script: Script, index: Int, step: Step): View = verticalLayout(0).apply {
        addView(body(getString(R.string.editor_step_line, index + 1, getString(step.kind.labelRes))))
        addView(caption(getString(R.string.editor_step_detail, step.durationMs, step.delayMs)))
        addView(
            caption(
                if (step.placed) {
                    getString(R.string.editor_step_placed, describe(step))
                } else {
                    getString(R.string.editor_step_unplaced)
                }
            )
        )
        addView(
            horizontalLayout().apply {
                setPadding(0, dp(6), 0, dp(2))
                addView(
                    smallButton(getString(R.string.scripts_edit)) {
                        startActivity(
                            Intent(this@ScriptEditorActivity, StepEditorActivity::class.java)
                                .putExtra(StepEditorActivity.EXTRA_SCRIPT_ID, scriptId)
                                .putExtra(StepEditorActivity.EXTRA_STEP_INDEX, index)
                        )
                    }
                )
                addView(smallButton(getString(R.string.step_place)) { place(index) })
                if (index > 0) {
                    addView(smallButton(getString(R.string.editor_up)) { swap(script, index, index - 1) })
                }
                if (index < script.steps.size - 1) {
                    addView(smallButton(getString(R.string.editor_down)) { swap(script, index, index + 1) })
                }
            }
        )
    }

    private fun describe(step: Step) =
        step.points.joinToString { "(${it.x.toInt()}, ${it.y.toInt()})" }

    private fun swap(script: Script, from: Int, to: Int) {
        val steps = script.steps.toMutableList()
        steps.add(to, steps.removeAt(from))
        save(script.copy(steps = steps))
        render()
    }

    private fun save(script: Script?) {
        if (script == null) return
        Store.save(this, script)
        if (Store.loadedId(this) == script.id) ClickService.instance?.refresh()
    }

    /** Placing a point means aiming at another app, so the editor gets out of the way. */
    private fun place(index: Int) {
        val service = ClickService.instance
        if (service == null) {
            Toast.makeText(this, R.string.run_service_off, Toast.LENGTH_SHORT).show()
            return
        }
        service.placeStepPoints(scriptId, index)
        moveTaskToBack(true)
    }

    private fun run() {
        val service = ClickService.instance
        if (service == null) {
            Toast.makeText(this, R.string.run_service_off, Toast.LENGTH_SHORT).show()
            return
        }
        Store.setLoadedId(this, scriptId)
        service.refresh()
        service.startRun()
        if (service.runner.busy) moveTaskToBack(true)
    }
}
