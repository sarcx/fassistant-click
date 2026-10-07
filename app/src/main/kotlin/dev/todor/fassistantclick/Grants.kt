package dev.todor.fassistantclick

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/**
 * The two gates that stand between an install and a working tap, and the deep link to each.
 *
 * The accessibility gate has two answers rather than one: Android's list can say the service is
 * enabled while the service itself has not connected, and telling the difference is the only way
 * to explain a phone that looks configured and does nothing.
 */
object Grants {

    enum class Gate { OFF, HALF, ON }

    fun overlay(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun service(context: Context): Gate = when {
        ClickService.instance != null -> Gate.ON
        enabledInSettings(context) -> Gate.HALF
        else -> Gate.OFF
    }

    fun ready(context: Context): Boolean = overlay(context) && ClickService.instance != null

    fun overlayIntent(context: Context) = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.parse("package:${context.packageName}"),
    )

    fun serviceIntent() = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

    /**
     * What Android's own list says, which is not the same as the service being alive — a phone can
     * carry a stale entry after a reinstall.
     */
    private fun enabledInSettings(context: Context): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false

        val component = ComponentName(context, ClickService::class.java)
        val names = setOf(component.flattenToString(), component.flattenToShortString())
        return enabled.split(':').any { it.trim() in names }
    }
}
