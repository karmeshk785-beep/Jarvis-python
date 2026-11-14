package com.jarvis.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent

class JarvisAccess : AccessibilityService() {
    override fun onAccessibilityEvent(e: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onServiceConnected() {
        super.onServiceConnected()
        Phone.svc = this
    }

    override fun onUnbind(i: Intent?): Boolean {
        Phone.svc = null
        return super.onUnbind(i)
    }
}

object Phone {
    var svc: JarvisAccess? = null

    private fun has(s: String, vararg k: String): Boolean {
        return k.any { s.contains(it) }
    }

    private fun swipe(ctx: Context, fx: Float, fy: Float, tx: Float, ty: Float, ms: Long) {
        val s = svc ?: return
        val dm = ctx.resources.displayMetrics
        val p = Path()
        p.moveTo(dm.widthPixels * fx, dm.heightPixels * fy)
        p.lineTo(dm.widthPixels * tx, dm.heightPixels * ty)
        val g = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(p, 0, ms)).build()
        s.dispatchGesture(g, null, null)
    }

    private fun wake(ctx: Context) {
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wl = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP, "jarvis:wake")
        wl.acquire(4000)
    }

    fun handle(ctx: Context, w: String): String? {
        val unlock = has(w, "unlock", "अनलॉक", "lock khol", "lock hata", "लॉक खोल", "लॉक हटा", "mobile khol", "phone khol", "screen on", "screen chalu")
        val lock = !unlock && has(w, "lock karo", "lock kar do", "lock kar", "mobile lock", "phone lock", "screen lock", "लॉक कर", "screen band", "स्क्रीन बंद")
        val home = has(w, "home jao", "home screen", "होम जाओ", "होम स्क्रीन")
        val back = has(w, "peeche jao", "pichhe jao", "back karo", "wapas jao", "पीछे जाओ", "वापस जाओ")
        val recents = has(w, "recent apps", "recent app", "रीसेंट")
        val shot = has(w, "screenshot", "स्क्रीनशॉट")
        val shade = has(w, "notification bar", "नोटिफिकेशन बार")
        val down = has(w, "neeche scroll", "niche scroll", "scroll down", "next video", "agla video", "नीचे स्क्रॉल", "अगला वीडियो")
        val up = has(w, "upar scroll", "ooper scroll", "scroll up", "pichla video", "पिछला वीडियो", "ऊपर स्क्रॉल")
        if (!(unlock || lock || home || back || recents || shot || shade || down || up)) r
