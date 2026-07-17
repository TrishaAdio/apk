package com.upimonitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import kotlin.concurrent.thread

/**
 * Receives incoming SMS (even when the app is closed), reassembles multipart
 * messages per-sender, and hands them to the Forwarder on a background thread.
 */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        if (!Prefs(context).enabled) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (messages.isEmpty()) return

        // Concatenate multipart SMS bodies; use the first message for metadata.
        val sender = messages[0].displayOriginatingAddress ?: messages[0].originatingAddress ?: ""
        val sentAt = messages[0].timestampMillis
        val body = StringBuilder()
        for (m in messages) body.append(m.messageBody ?: "")

        val appContext = context.applicationContext
        val fullBody = body.toString()

        // Receivers must return quickly; do network work off the main thread.
        val pending = goAsync()
        thread {
            try {
                Forwarder.forward(appContext, sender, fullBody, sentAt)
            } finally {
                pending.finish()
            }
        }
    }
}
