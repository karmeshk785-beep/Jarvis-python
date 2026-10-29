package com.jarvis.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class JarvisActivity : Activity() {
    private lateinit var hud: Hud
    private lateinit var log: TextView
    private lateinit var scroll: ScrollView
    private lateinit var toggle: Button
    private var lastRun = -1
    private lateinit var settings: SettingsView
    private val ui = Handler(Looper.getMainLooper())
    private var lastVer = -1
    private var polling = false
    private val tick = object : Runnable {
        override fun run() {
            hud.mode = Bus.mode
            val rn = if (Bus.running) 1 else 0
            if (rn != lastRun) {
                lastRun = rn
                val bg = GradientDrawable()
                bg.shape = GradientDrawable.OVAL
                bg.setColor(if (rn == 1) Color.argb(210, 200, 30, 30) else Color.argb(210, 20, 160, 80))
                toggle.background = bg
                toggle.text = if (rn == 1) "BAND" else "CHALU"
            }
            if (Bus.version != lastVer) {
                lastVer = Bus.version
                log.text = Bus.logs.toString()
                scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
            }
            if (polling) ui.postDelayed(this, 250)
        }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val d = resources.displayMetrics.density
        val root = FrameLayout(this)
        hud = Hud(this)
        root.addView(hud, FrameLayout.LayoutParams(-1, -1))
        root.addView(EdgeGlow(this, true), FrameLayout.LayoutParams(-1, -1))
        val panel = LinearLayout(this)
        panel.orientation = LinearLayout.VERTICAL
        panel.setPadding((16 * d).toInt(), 0, (16 * d).toInt(), (20 * d).toInt())
        log = TextView(this)
        log.setTextColor(Color.WHITE)
        log.textSize = 14f
        log.setShadowLayer(4f, 0f, 0f, Color.BLACK)
        log.text = "Settings mein Groq key save karo, phir Jarvis shuru karo dabao. Bolte waqt shuru mein Jarvis bolo."
        scroll = ScrollView(this)
        scroll.addView(log)
        panel.addView(scroll, LinearLayout.LayoutParams(-1, (150 * d).toInt()))
        val lp = FrameLayout.LayoutParams(-1, -2)
        lp.gravity = Gravity.BOTTOM
        root.addView(panel, lp)
        toggle = Button(this)
        toggle.textSize = 13f
        toggle.setTextColor(Color.WHITE)
        toggle.setOnClickListener {
            if (Bus.running) stopJarvis() else startJarvis()
        }
        val size = (88 * d).toInt()
        val tlp = FrameLayout.LayoutParams(size, size)
        tlp.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        tlp.bottomMargin = (210 * d).toInt()
        root.addView(toggle, tlp)
        val gear = Button(this)
        gear.text = "Settings"
        gear.textSize = 11f
        gear.setOnClickListener {
            settings.visibility = View.VISIBLE
        }
        val glp = FrameLayout.LayoutParams(-2, -2)
        glp.gravity = Gravity.TOP or Gravity.END
        glp.topMargin = (36 * d).toInt()
        glp.rightMargin = (10 * d).toInt()
        root.addView(gear, glp)
        settings = SettingsView(this)
        settings.visibility = View.GONE
        root.addView(settings, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
        goFull()
        applySettings()
        loadPhoto()
    }

    private fun goFull() {
        window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= 28) {
            val a = window.attributes
            a.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = a
        }
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
    }

    override fun onWindowFocusChanged(has: Boolean) {
        super.onWindowFocusChanged(has)
        if (has) goFull()
    }

    override fun onResume() {
        super.onResume()
        Bus.appVisible = true
        polling = true
        ui.removeCallbacks(tick)
        ui.post(tick)
    }

    override fun onPause() {
        super.onPause()
        Bus.appVisible = false
        polling = false
    }

    private fun startJarvis() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS), 1)
            toast("Mic aur Notification Allow karke Jarvis shuru dobara dabao")
            return
        }
        val p = getSharedPreferences("j", 0)
        if (!Settings.canDrawOverlays(this) && !p.getBoolean("asked", false)) {
            p.edit().putBoolean("asked", true).apply()
            toast("Jarvis ko Display over other apps Allow karo, phir wapas aakar shuru dabao")
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + packageName)))
            return
        }
        val i = Intent(this, JarvisService::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            startForegroundService(i)
        } else {
            startService(i)
        }
    }

    private fun stopJarvis() {
        stopService(Intent(this, JarvisService::class.java))
    }

    fun toast(s: String) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
    }

    fun applySettings() {
        val p = getSharedPreferences("j", 0)
        hud.theme = p.getInt("theme", 0)
        hud.ringsOn = p.getBoolean("rings", false)
        hud.zoom = 1f + p.getInt("zoom", 0) * 0.05f
        hud.invalidate()
    }

    fun testVoice() {
        if (!Bus.running) {
            toast("Pehle Jarvis shuru karo")
            return
        }
        val i = Intent(this, JarvisService::class.java)
        i.putExtra("say", "Namaste mere dost, main Jarvis hoon. Ye meri awaaz hai")
        startService(i)
    }

    fun pickPhoto() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
        i.addCategory(Intent.CATEGORY_OPENABLE)
        i.type = "image/*"
        startActivityForResult(i, 77)
    }

    fun clearPhoto() {
        getSharedPreferences("j", 0).edit().remove("photo").apply()
        hud.photo = null
    }

    fun closeSettings() {
        settings.visibility = View.GONE
    }

    override fun onActivityResult(code: Int, res: Int, data: Intent?) {
        super.onActivityResult(code, res, data)
        if (code == 77 && res == RESULT_OK && data != null) {
            val u = data.data
            if (u != null) {
                try {
                    contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (e: Exception) {
                }
                getSharedPreferences("j", 0).edit().putString("photo", u.toString()).apply()
                loadPhoto()
            }
        }
    }

    private fun loadPhoto() {
        val s = getSharedPreferences("j", 0).getString("photo", null)
        if (s == null) {
            hud.photo = null
            return
        }
        try {
            val o = BitmapFactory.Options()
            o.inJustDecodeBounds = true
            contentResolver.openInputStream(Uri.parse(s))?.use { BitmapFactory.decodeStream(it, null, o) }
            var sample = 1
            while (o.outWidth / sample > 1600 || o.outHeight / sample > 1600) sample *= 2
            val o2 = BitmapFactory.Options()
            o2.inSampleSize = sample
            val bmp = contentResolver.openInputStream(Uri.parse(s))?.use { BitmapFactory.decodeStream(it, null, o2) }
            hud.photo = if (bmp != null) trim(bmp) else null
        } catch (e: Exception) {
            hud.photo = null
            toast("Photo nahi khul payi")
        }
    }

    private fun trim(b: Bitmap): Bitmap {
        val w = b.width
        val h = b.height
        val px = IntArray(w * h)
        b.getPixels(px, 0, w, 0, 0, w, h)
        var top = 0
        var bottom = h - 1
        var left = 0
        var right = w - 1
        while (top < bottom && rowBlack(px, w, top)) top++
        while (bottom > top && rowBlack(px, w, bottom)) bottom--
        while (left < right && colBlack(px, w, left, top, bottom)) left++
        while (right > left && colBlack(px, w, right, top, bottom)) right--
        if (right - left < w / 4 || bottom - top < h / 4) return b
        return Bitmap.createBitmap(b, left, top, right - left + 1, bottom - top + 1)
    }

    private fun isDark(c: Int): Boolean {
        return Color.red(c) + Color.green(c) + Color.blue(c) < 75
    }

    private fun rowBlack(px: IntArray, w: Int, y: Int): Boolean {
        var n = 0
        var x = 0
        while (x < w) {
            if (!isDark(px[y * w + x])) n++
            x += 3
        }
        return n < w / 3 / 25
    }

    private fun colBlack(px: IntArray, w: Int, x: Int, y0: Int, y1: Int): Boolean {
        var n = 0
        var y = y0
        while (y <= y1) {
            if (!isDark(px[y * w + x])) n++
            y += 3
        }
        return n < (y1 - y0 + 1) / 3 / 25
    }

    override fun onBackPressed() {
        if (settings.visibility == View.VISIBLE) {
            settings.visibility = View.GONE
        } else {
            super.onBackPressed()
        }
    }
}
