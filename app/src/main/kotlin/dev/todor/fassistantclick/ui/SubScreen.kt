package dev.todor.fassistantclick.ui

import android.app.Activity
import android.os.Bundle
import android.view.MenuItem

/**
 * Everything below the main screen. Without AndroidX there is no support-library up button, so
 * the action bar's home item is wired by hand.
 */
abstract class SubScreen : Activity() {

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        actionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
