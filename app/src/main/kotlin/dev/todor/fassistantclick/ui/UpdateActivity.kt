package dev.todor.fassistantclick.ui

import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import dev.todor.fassistantclick.BuildConfig
import dev.todor.fassistantclick.R
import dev.todor.fassistantclick.update.UpdateInstaller
import dev.todor.fassistantclick.update.UpdateManifest

/**
 * Checks one address for a newer build and offers it. Nothing is automatic: the check happens
 * when you ask for it, and the install itself is Android's own confirmation.
 */
class UpdateActivity : SubScreen() {

    private val main = Handler(Looper.getMainLooper())

    private var address = BuildConfig.UPDATE_MANIFEST_URL
    private var status: CharSequence? = null
    private var found: UpdateManifest? = null
    private var working = false

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val content = verticalLayout()

        content.addView(
            body(
                getString(R.string.update_installed, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)
            )
        )

        content.addView(heading(getString(R.string.update_source)))
        if (BuildConfig.UPDATE_MANIFEST_URL.isEmpty() && address.isEmpty()) {
            content.addView(caption(getString(R.string.update_off)))
        }
        content.addView(addressField())

        if (!working) {
            content.addView(button(getString(R.string.update_check)) { check() })
        }

        status?.let {
            content.addView(spacer(8))
            content.addView(body(it))
        }

        val newer = found?.takeIf { it.versionCode > BuildConfig.VERSION_CODE }
        if (newer != null && !working) {
            if (newer.notes.isNotBlank()) {
                content.addView(heading(getString(R.string.update_notes)))
                content.addView(caption(newer.notes))
            }
            // No check first for permission to install apps. On Android 8 and later,
            // canRequestPackageInstalls() answers false for any app targeting below 26 — this one
            // targets 25 — however the setting is set. Android's own confirmation checks the real
            // setting, links to it when it is off, and then carries on with the install.
            content.addView(button(getString(R.string.update_install)) { install(newer) })
        }

        setContentView(scrolling(content))
    }

    private fun addressField() = EditText(this).apply {
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        hint = getString(R.string.update_url_hint)
        setText(address)
        addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(edited: Editable?) {
                address = edited?.toString()?.trim().orEmpty()
            }

            override fun beforeTextChanged(text: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) = Unit
        })
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
    }

    private fun check() {
        val from = address
        if (from.isEmpty()) {
            status = getString(R.string.update_off)
            render()
            return
        }

        working = true
        status = getString(R.string.update_checking)
        found = null
        render()

        offThread {
            val manifest = UpdateManifest.parse(UpdateInstaller.fetchText(from), from)
            main.post {
                found = manifest
                status = if (manifest.versionCode > BuildConfig.VERSION_CODE) {
                    getString(R.string.update_available, manifest.versionName, manifest.versionCode)
                } else {
                    getString(R.string.update_current)
                }
                working = false
                render()
            }
        }
    }

    private fun install(manifest: UpdateManifest) {
        working = true
        status = getString(R.string.update_downloading)
        render()

        offThread {
            val apk = UpdateInstaller.download(this, manifest)
            val problem = when {
                !UpdateInstaller.checksumMatches(apk, manifest.sha256) ->
                    getString(R.string.update_checksum_failed)
                !UpdateInstaller.signedLikeUs(this, apk) ->
                    getString(R.string.update_signature_failed)
                else -> null
            }

            main.post {
                working = false
                if (problem != null) {
                    apk.delete()
                    status = problem
                    render()
                    return@post
                }
                status = getString(R.string.update_handing_over)
                render()
                UpdateInstaller.handOver(this, apk)
            }
        }
    }

    /** No coroutines here: this app has no runtime dependencies, and one thread is enough. */
    private fun offThread(work: () -> Unit) {
        Thread {
            try {
                work()
            } catch (problem: Exception) {
                val reason = problem.message ?: problem.javaClass.simpleName
                main.post {
                    working = false
                    status = getString(R.string.update_failed, reason)
                    render()
                }
            }
        }.start()
    }
}
