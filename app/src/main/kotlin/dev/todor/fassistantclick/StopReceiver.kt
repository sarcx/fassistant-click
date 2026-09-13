package dev.todor.fassistantclick

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** The Stop action on the run notification. */
class StopReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ClickService.instance?.stopRun()
    }
}
