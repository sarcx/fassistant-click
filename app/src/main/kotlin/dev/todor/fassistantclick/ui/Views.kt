package dev.todor.fassistantclick.ui

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import dev.todor.fassistantclick.R
import dev.todor.fassistantclick.script.Script

internal fun Context.dp(value: Int) = (value * resources.displayMetrics.density).toInt()

internal fun Context.stepCount(script: Script): String =
    resources.getQuantityString(R.plurals.scripts_step_count, script.steps.size, script.steps.size)

internal fun Context.dpf(value: Float) = (value * resources.displayMetrics.density).toInt()

internal fun Context.verticalLayout(padding: Int = 16): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    val space = dp(padding)
    setPadding(space, space, space, space)
    layoutParams = ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    )
}

internal fun Context.horizontalLayout(): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL
    gravity = Gravity.CENTER_VERTICAL
    layoutParams = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    )
}

internal fun Context.scrolling(content: View): ScrollView = ScrollView(this).apply {
    addView(content)
    isFillViewport = true
}

internal fun Context.textView(text: CharSequence, appearance: Int): TextView = TextView(this).apply {
    setTextAppearance(appearance)
    this.text = text
    layoutParams = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    )
}

internal fun Context.title(text: CharSequence) =
    textView(text, android.R.style.TextAppearance_DeviceDefault_Large).apply {
        setTypeface(typeface, Typeface.BOLD)
    }

internal fun Context.body(text: CharSequence) =
    textView(text, android.R.style.TextAppearance_DeviceDefault)

internal fun Context.caption(text: CharSequence) =
    textView(text, android.R.style.TextAppearance_DeviceDefault_Small)

internal fun Context.heading(text: CharSequence) =
    textView(text, android.R.style.TextAppearance_DeviceDefault_Small).apply {
        setTypeface(typeface, Typeface.BOLD)
        isAllCaps = true
        letterSpacing = 0.09f
        setPadding(0, dp(20), 0, dp(4))
    }

internal fun Context.button(text: CharSequence, onClick: () -> Unit): Button = Button(this).apply {
    this.text = text
    setOnClickListener { onClick() }
    layoutParams = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    )
}

internal fun Context.smallButton(text: CharSequence, onClick: () -> Unit): Button = Button(this).apply {
    this.text = text
    setTextAppearance(android.R.style.TextAppearance_DeviceDefault_Small)
    minWidth = 0
    minimumWidth = 0
    setPadding(dp(12), 0, dp(12), 0)
    setOnClickListener { onClick() }
    layoutParams = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    ).apply { rightMargin = dp(6) }
}

internal fun Context.badge(text: CharSequence, color: Int): TextView = TextView(this).apply {
    this.text = text
    setTextAppearance(android.R.style.TextAppearance_DeviceDefault_Small)
    setTextColor(color)
    isAllCaps = true
    setPadding(dp(8), dp(2), dp(8), dp(2))
    background = GradientDrawable().apply {
        cornerRadius = dp(4).toFloat()
        setColor((color and 0x00FFFFFF) or 0x22000000)
        setStroke(dp(1), color)
    }
}

internal fun Context.divider(): View = View(this).apply {
    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply {
        topMargin = dp(8)
        bottomMargin = dp(8)
    }
    setBackgroundColor(0x22808080)
}

internal fun Context.spacer(height: Int): View = View(this).apply {
    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(height))
}

/**
 * A labelled whole-number field. Reports every edit, because there is no Save button to wait for.
 *
 * The value is deliberately not locale-formatted: it is read back with [String.toLongOrNull], so
 * locale-specific digits or a thousands separator would make the field unparseable.
 */
@Suppress("SetTextI18n")
internal fun Context.numberField(
    label: CharSequence,
    value: Long,
    onChange: (Long) -> Unit,
): View = verticalLayout(0).apply {
    addView(caption(label))
    addView(
        EditText(this@numberField).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(value.toString())
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(edited: Editable?) {
                    onChange(edited?.toString()?.toLongOrNull() ?: 0L)
                }

                override fun beforeTextChanged(text: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) = Unit
            })
        }
    )
    layoutParams = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    ).apply { topMargin = dp(8) }
}

internal fun Context.spinnerField(
    label: CharSequence,
    entries: List<CharSequence>,
    selected: Int,
    onSelect: (Int) -> Unit,
): View = verticalLayout(0).apply {
    addView(caption(label))
    addView(
        Spinner(this@spinnerField).apply {
            adapter = ArrayAdapter(
                this@spinnerField,
                android.R.layout.simple_spinner_dropdown_item,
                entries,
            )
            setSelection(selected, false)
            // Spinner fires a selection as soon as it is attached; only a later one is the user.
            var settled = false
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    if (settled) onSelect(position) else settled = true
                }

                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
        }
    )
    layoutParams = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    ).apply { topMargin = dp(8) }
}

internal fun Context.seekField(
    label: CharSequence,
    range: IntRange,
    value: Int,
    describe: (Int) -> CharSequence,
    onChange: (Int) -> Unit,
): View = verticalLayout(0).apply {
    val readout = caption(describe(value))
    addView(caption(label))
    addView(readout)
    addView(
        SeekBar(this@seekField).apply {
            max = range.last - range.first
            progress = value - range.first
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val picked = range.first + progress
                    readout.text = describe(picked)
                    if (fromUser) onChange(picked)
                }

                override fun onStartTrackingTouch(bar: SeekBar?) = Unit
                override fun onStopTrackingTouch(bar: SeekBar?) = Unit
            })
        }
    )
    layoutParams = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    ).apply { topMargin = dp(12) }
}
