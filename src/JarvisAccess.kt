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
        val unlock = has(w, "unlock", "lock khol", "lock hata", "mobile khol", "phone khol", "screen on", "screen chalu")
        val lock = !unlock && has(w, "lock karo", "lock kar do", "lock kar", "mobile lock", "phone lock", "screen lock", "screen band")
        val home = has(w, "home jao", "home screen")
        val back = has(w, "peeche jao", "pichhe jao", "back karo", "wapas jao")
        val recents = has(w, "recent apps", "recent app")
        val shot = has(w, "screenshot")
        val shade = has(w, "notification bar")
        val down = has(w, "neeche scroll", "niche scroll", "scroll down", "next video", "agla video")
        val up = has(w, "upar scroll", "ooper scroll", "scroll up", "pichla video")
        if (!(unlock || lock || home || back || recents || shot || shade || down || up)) return null
        if (unlock) {
            wake(ctx)
            Handler(Looper.getMainLooper()).postDelayed({ swipe(ctx, 0.5f, 0.85f, 0.5f, 0.2f, 350) }, 1200)
            return "Ji dost, screen chalu kar raha hoon. Agar PIN ya pattern laga hai toh wo nahi khulega"
        }
        val s = svc
        if (s == null) {
            val i = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(i)
            return "Accessibility mein Jarvis ko ON karo, phir dobara bolo"
        }
        if (lock) {
            if (Build.VERSION.SDK_INT < 28) return "Is phone par lock nahi ho sakta"
            s.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
            return "Theek hai dost, phone lock kar diya"
        }
        if (home) {
            s.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
            return "Home par aa gaya"
        }
        if (back) {
            s.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            return "Peeche aa gaya"
        }
        if (recents) {
            s.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
            return "Recent apps khol diye"
        }
        if (shade) {
            s.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
            return "Notification bar khol diya"
        }
        if (shot) {
            if (Build.VERSION.SDK_INT < 28) return "Is phone par screenshot nahi ho sakta"
            s.performGlobalAction(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT)
            return "Screenshot le liya"
        }
        if (down) {
            swipe(ctx, 0.5f, 0.75f, 0.5f, 0.25f, 300)
            return "Neeche kar diya"
        }
        swipe(ctx, 0.5f, 0.25f, 0.5f, 0.75f, 300)
        return "Upar kar diya"
    }
}
