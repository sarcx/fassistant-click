package dev.todor.fassistantclick.ui

import dev.todor.fassistantclick.ClickService
import dev.todor.fassistantclick.Prefs
import dev.todor.fassistantclick.R

class SettingsActivity : SubScreen() {

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val content = verticalLayout()
        val percent: (Int) -> CharSequence = { getString(R.string.settings_percent, it) }

        content.addView(
            seekField(
                getString(R.string.settings_panel_size),
                Prefs.sizeRange,
                Prefs.panelSize(this),
                percent,
            ) { picked ->
                Prefs.setPanelSize(this, picked)
                ClickService.instance?.refreshLook()
            }
        )
        content.addView(
            seekField(
                getString(R.string.settings_panel_opacity),
                Prefs.opacityRange,
                Prefs.panelOpacity(this),
                percent,
            ) { picked ->
                Prefs.setPanelOpacity(this, picked)
                ClickService.instance?.refreshLook()
            }
        )
        content.addView(
            seekField(
                getString(R.string.settings_marker_size),
                Prefs.sizeRange,
                Prefs.markerSize(this),
                percent,
            ) { picked ->
                Prefs.setMarkerSize(this, picked)
                ClickService.instance?.refreshLook()
            }
        )

        content.addView(spacer(16))
        content.addView(
            button(getString(R.string.settings_reset)) {
                Prefs.reset(this)
                ClickService.instance?.refreshLook()
                render()
            }
        )

        setContentView(scrolling(content))
    }
}
