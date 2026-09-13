package dev.todor.fassistantclick.ui

import android.app.AlertDialog
import android.content.Intent
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import dev.todor.fassistantclick.ClickService
import dev.todor.fassistantclick.R
import dev.todor.fassistantclick.script.Script
import dev.todor.fassistantclick.script.Store

class ScriptsActivity : SubScreen() {

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val scripts = Store.all(this)
        val loadedId = Store.loadedId(this)
        val content = verticalLayout()

        content.addView(
            button(getString(R.string.scripts_new)) {
                askForName(getString(R.string.scripts_default_name, scripts.size + 1)) { name ->
                    val created = Store.create(this, name)
                    edit(created)
                }
            }
        )

        if (scripts.isEmpty()) {
            content.addView(spacer(8))
            content.addView(body(getString(R.string.scripts_empty)))
        } else {
            scripts.forEach { script ->
                content.addView(divider())
                content.addView(row(script, script.id == loadedId))
            }
        }

        setContentView(scrolling(content))
    }

    private fun row(script: Script, loaded: Boolean): View = verticalLayout(0).apply {
        addView(body(script.name))
        addView(caption(stepCount(script)))
        addView(
            horizontalLayout().apply {
                setPadding(0, dp(6), 0, dp(2))
                if (loaded) {
                    addView(badge(getString(R.string.scripts_loaded), getColor(R.color.badge_good)))
                    addView(spacerWidth(dp(8)))
                } else {
                    addView(
                        smallButton(getString(R.string.scripts_load)) {
                            Store.setLoadedId(this@ScriptsActivity, script.id)
                            ClickService.instance?.refresh()
                            render()
                        }
                    )
                }
                addView(smallButton(getString(R.string.scripts_edit)) { edit(script) })
                addView(
                    smallButton(getString(R.string.scripts_rename)) {
                        askForName(script.name) { name ->
                            Store.save(this@ScriptsActivity, script.copy(name = name))
                            render()
                        }
                    }
                )
            }
        )
        addView(
            horizontalLayout().apply {
                addView(
                    smallButton(getString(R.string.scripts_duplicate)) {
                        Store.create(
                            this@ScriptsActivity,
                            getString(R.string.scripts_copy_name, script.name),
                            copyOf = script,
                        )
                        render()
                    }
                )
                addView(
                    smallButton(getString(R.string.common_delete)) {
                        AlertDialog.Builder(this@ScriptsActivity)
                            .setMessage(getString(R.string.scripts_delete_confirm, script.name))
                            .setNegativeButton(R.string.common_cancel, null)
                            .setPositiveButton(R.string.common_delete) { _, _ ->
                                Store.delete(this@ScriptsActivity, script.id)
                                ClickService.instance?.refresh()
                                render()
                            }
                            .show()
                    }
                )
            }
        )
    }

    private fun edit(script: Script) {
        startActivity(
            Intent(this, ScriptEditorActivity::class.java)
                .putExtra(ScriptEditorActivity.EXTRA_SCRIPT_ID, script.id)
        )
    }

    private fun askForName(initial: String, onChosen: (String) -> Unit) {
        val field = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            hint = getString(R.string.scripts_name_hint)
            setText(initial)
            setSelection(text.length)
        }
        AlertDialog.Builder(this)
            .setView(field)
            .setNegativeButton(R.string.common_cancel, null)
            .setPositiveButton(R.string.common_save) { _, _ ->
                val chosen = field.text.toString().trim()
                onChosen(chosen.ifEmpty { initial })
            }
            .show()
    }

    private fun spacerWidth(width: Int): View = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT)
    }
}
