package com.upimonitor

import android.app.Activity
import android.os.Bundle
import android.widget.Toast

/**
 * Compose/send activity. Required only so the app qualifies as a default SMS
 * app. This monitor doesn't send messages, so it simply informs the user and
 * closes. (If you actually tap "message" somewhere, nothing is composed.)
 */
class ComposeSmsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Toast.makeText(
            this,
            "UPI Monitor only reads payment SMS. It does not send messages.",
            Toast.LENGTH_LONG
        ).show()
        finish()
    }
}
