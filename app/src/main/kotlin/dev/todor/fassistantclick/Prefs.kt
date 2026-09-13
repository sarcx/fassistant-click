package dev.todor.fassistantclick

import android.content.Context
import android.graphics.Point

/**
 * The handful of numbers that decide how the overlay looks, plus where the panel was last
 * dragged to. Kept apart from the scripts because they belong to the phone, not to what you are
 * automating.
 */
object Prefs {
    val sizeRange = 60..160
    val opacityRange = 20..100

    private const val PREFS = "fclick"
    private const val PANEL_SIZE = "panelSizePercent"
    private const val PANEL_OPACITY = "panelOpacityPercent"
    private const val MARKER_SIZE = "markerSizePercent"
    private const val PANEL_X = "panelX"
    private const val PANEL_Y = "panelY"

    fun panelSize(context: Context) = read(context, PANEL_SIZE, 100, sizeRange)
    fun panelOpacity(context: Context) = read(context, PANEL_OPACITY, 90, opacityRange)
    fun markerSize(context: Context) = read(context, MARKER_SIZE, 100, sizeRange)

    fun setPanelSize(context: Context, percent: Int) = write(context, PANEL_SIZE, percent, sizeRange)
    fun setPanelOpacity(context: Context, percent: Int) = write(context, PANEL_OPACITY, percent, opacityRange)
    fun setMarkerSize(context: Context, percent: Int) = write(context, MARKER_SIZE, percent, sizeRange)

    /** Null until the panel has been moved, which is what tells the caller to use its default. */
    fun panelPosition(context: Context): Point? {
        val saved = prefs(context)
        if (!saved.contains(PANEL_X)) return null
        return Point(saved.getInt(PANEL_X, 0), saved.getInt(PANEL_Y, 0))
    }

    fun setPanelPosition(context: Context, x: Int, y: Int) {
        prefs(context).edit().putInt(PANEL_X, x).putInt(PANEL_Y, y).apply()
    }

    fun reset(context: Context) {
        prefs(context).edit()
            .remove(PANEL_SIZE)
            .remove(PANEL_OPACITY)
            .remove(MARKER_SIZE)
            .apply()
    }

    private fun read(context: Context, key: String, fallback: Int, range: IntRange) =
        prefs(context).getInt(key, fallback).coerceIn(range)

    private fun write(context: Context, key: String, percent: Int, range: IntRange) {
        prefs(context).edit().putInt(key, percent.coerceIn(range)).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
