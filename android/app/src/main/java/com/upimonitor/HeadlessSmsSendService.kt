package com.upimonitor

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * "Respond via message" service. Required only so the app qualifies as a
 * default SMS app (e.g. quick replies from the phone/dialer). This monitor does
 * not send SMS, so the service does nothing.
 */
class HeadlessSmsSendService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        stopSelf(startId)
        return START_NOT_STICKY
    }
}
