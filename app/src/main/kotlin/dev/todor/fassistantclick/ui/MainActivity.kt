package dev.todor.fassistantclick.ui

import android.app.Activity
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import dev.todor.fassistantclick.BuildConfig
import dev.todor.fassistantclick.ClickService
import dev.todor.fassistantclick.Grants
import dev.todor.fassistantclick.R
import dev.todor.fassistantclick.gesture.Gestures
import dev.todor.fassistantclick.script.Store

/**
 * Where you find out why nothing is happening. The two gates are the first thing on the screen
 * because they are the answer nine times out of ten.
 */
class MainActivity : Activity() {

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val content = verticalLayout()

        content.addView(heading(getString(R.string.main_permissions)))

        val overlayOn = Grants.overlay(this)
        content.addView(
            gateRow(
                getString(R.string.main_gate_overlay),
                getString(R.string.main_gate_overlay_why),
                if (overlayOn) Grants.Gate.ON else Grants.Gate.OFF,
            )
        )
        if (!overlayOn) {
            content.addView(
                button(getString(R.string.main_grant_overlay)) {
                    startActivity(Grants.overlayIntent(this))
                }
            )
        }

        val serviceGate = Grants.service(this)
        content.addView(
            gateRow(
                getString(R.string.main_gate_service),
                getString(R.string.main_gate_service_why),
                serviceGate,
            )
        )
        if (serviceGate != Grants.Gate.ON) {
            content.addView(
                button(getString(R.string.main_grant_service)) {
                    startActivity(Grants.serviceIntent())
                }
            )
            content.addView(caption(getString(R.string.main_restricted_hint)))
        }

        content.addView(heading(getString(R.string.main_panel)))
        val service = ClickService.instance
        if (service == null || !overlayOn) {
            content.addView(caption(getString(R.string.main_panel_blocked)))
        } else if (service.panelVisible) {
            content.addView(button(getString(R.string.main_hide_panel)) { service.hidePanel(); render() })
        } else {
            content.addView(button(getString(R.string.main_show_panel)) { service.showPanel(); render() })
        }

        content.addView(heading(getString(R.string.main_script)))
        val loaded = Store.loaded(this)
        content.addView(
            body(
                if (loaded == null) {
                    getString(R.string.main_loaded_none)
                } else {
                    getString(R.string.main_loaded, loaded.name)
                }
            )
        )
        content.addView(
            button(getString(R.string.main_manage_scripts)) {
                startActivity(Intent(this, ScriptsActivity::class.java))
            }
        )

        content.addView(heading(getString(R.string.main_phone)))
        content.addView(
            caption(getString(R.string.main_caps, Gestures.maxStrokes, Gestures.maxDurationMs))
        )

        content.addView(spacer(12))
        content.addView(
            button(getString(R.string.main_settings)) {
                startActivity(Intent(this, SettingsActivity::class.java))
            }
        )
        content.addView(
            button(getString(R.string.main_updates)) {
                startActivity(Intent(this, UpdateActivity::class.java))
            }
        )
        content.addView(
            caption(
                getString(R.string.main_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)
            )
        )

        setContentView(scrolling(content))
    }

    private fun gateRow(label: String, why: String, gate: Grants.Gate): View {
        val labels = verticalLayout(0).apply {
            addView(body(label))
            addView(caption(why))
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f,
            )
        }

        val (text, colour) = when (gate) {
            Grants.Gate.ON -> getString(R.string.gate_on) to getColor(R.color.badge_good)
            Grants.Gate.HALF -> getString(R.string.gate_half) to getColor(R.color.badge_warn)
            Grants.Gate.OFF -> getString(R.string.gate_off) to getColor(R.color.badge_bad)
        }

        return horizontalLayout().apply {
            setPadding(0, dp(6), 0, dp(6))
            addView(labels)
            addView(badge(text, colour))
        }
    }
}
