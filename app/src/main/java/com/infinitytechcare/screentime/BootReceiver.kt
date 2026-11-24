package com.infinitytechcare.screentime

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Restarts the enforcement service after the phone reboots, if it was on. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED && Prefs.isServiceEnabled(context)) {
            MonitorService.start(context)
        }
    }
}
