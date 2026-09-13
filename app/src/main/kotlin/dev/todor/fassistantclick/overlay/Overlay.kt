package dev.todor.fassistantclick.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import dev.todor.fassistantclick.ui.dpf

internal const val OVERLAY_GROUND = 0xEE1B2430.toInt()
internal const val OVERLAY_INK = 0xFFF2F3F5.toInt()
internal const val OVERLAY_PILL = 0x33FFFFFF
internal const val OVERLAY_ACCENT = 0xFF48C3CD.toInt()
internal const val OVERLAY_DANGER = 0xFFE0655C.toInt()
internal const val OVERLAY_SCRIM = 0x40000000

/**
 * The window coordinates have to be raw screen pixels, because that is what dispatchGesture
 * works in — hence LAYOUT_IN_SCREEN and LAYOUT_NO_LIMITS on everything here. Without them a
 * marker sitting at y=0 would be a status bar's height away from where the tap lands.
 */
@Suppress("DEPRECATION")
internal fun overlayWindowType(): Int =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
    } else {
        WindowManager.LayoutParams.TYPE_PHONE
    }

internal fun overlayParams(width: Int, height: Int, touchable: Boolean): WindowManager.LayoutParams {
    var flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
    if (!touchable) flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE

    return WindowManager.LayoutParams(width, height, overlayWindowType(), flags, PixelFormat.TRANSLUCENT).apply {
        gravity = Gravity.TOP or Gravity.START
    }
}

internal fun WindowManager.LayoutParams.setTouchable(touchable: Boolean) {
    flags = if (touchable) {
        flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
    } else {
        flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
    }
}

internal fun Context.overlayGround(scale: Float): GradientDrawable = GradientDrawable().apply {
    cornerRadius = dpf(10f * scale).toFloat()
    setColor(OVERLAY_GROUND)
}

internal fun Context.overlayPill(
    text: CharSequence,
    scale: Float,
    onClick: () -> Unit,
): TextView = TextView(this).apply {
    this.text = text
    setTextColor(OVERLAY_INK)
    textSize = 13f * scale
    gravity = Gravity.CENTER
    setPadding(dpf(11f * scale), dpf(6f * scale), dpf(11f * scale), dpf(6f * scale))
    background = GradientDrawable().apply {
        cornerRadius = dpf(6f * scale).toFloat()
        setColor(OVERLAY_PILL)
    }
    isClickable = true
    setOnClickListener { onClick() }
    layoutParams = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    ).apply { rightMargin = dpf(5f * scale) }
}

internal fun View.detachFrom(windows: WindowManager) {
    try {
        windows.removeView(this)
    } catch (ignored: IllegalArgumentException) {
        // Already gone — the window can be torn down by the system when the service unbinds.
    }
}
