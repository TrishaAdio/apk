package com.upimonitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * MMS (WAP push) receiver. Required only so the app qualifies as a default SMS
 * app. This monitor doesn't process MMS, so this is intentionally a no-op.
 */
class MmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // No-op: MMS is not used for UPI payment alerts.
    }
}
