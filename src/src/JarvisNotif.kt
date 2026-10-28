package com.jarvis.app

import android.app.Notification
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

object Msgs {
    var last: Notification.Action? = null
    var lastWho = ""
    var onNew: ((String, String, String) -> Unit)? = null

    fun sendReply(ctx: Context, text: String): Boolean {
        try {
            val a = last ?: return false
            val ris = a.remoteInputs ?: return false
            if (ris.isEmpty()) return false
            val i = Intent()
            val b = Bundle()
            for (r in ris) b.putCharSequence(r.resultKey, text)
            RemoteInput.addResultsToIntent(ris, i, b)
            a.actionIntent.send(ctx, 0, i)
            return true
        } catch (e: Exception) {
            return false
        }
    }
}

class JarvisNotif : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        try {
            val pkg = sbn.packageName
            var app = ""
            if (pkg == "com.whatsapp" || pkg == "com.whatsapp.w4b") app = "WhatsApp"
            else if (pkg == "org.telegram.messenger") app = "Telegram"
            else if (pkg == "com.google.android.apps.messaging") app = "Message"
            else if (pkg == "com.instagram.android") app = "Instagram"
            if (app.isEmpty()) return
            val n = sbn.notification
            if ((n.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return
            val ex = n.extras
            val who = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
            var text = ex.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
            if (text.isEmpty()) return
            if (text.length > 200) text = text.substring(0, 200)
            var act: Notification.Action? = null
            val acts = n.actions
            if (acts != null) {
                for (a in acts) {
                    val ri = a.remoteInputs
                    if (ri != null && ri.isNotEmpty()) act = a
                }
            }
            Msgs.last = act
            Msgs.lastWho = who
            Msgs.onNew?.invoke(app, who, text)
        } catch (e: Exception) {
        }
    }
}
