package com.jarvis.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
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
    private lateinit var btn: Button
    private lateinit var settings: SettingsView
    private val ui = Handler(Looper.getMainLooper())
    private var lastVer = -1
    private var polling = false
    private val tick = object : Runnable {
        override fun run() {
            hud.mode = Bus.mode
            btn.text = if (Bus.running) "Jarvis band karo" else "Jarvis shuru karo"
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
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        val gear = Button(this)
        gear.text = "Settings"
        gear.setOnClickListener {
            settings.visibility = View.VISIBLE
        }
        btn = Button(this)
        btn.text = "Jarvis shuru karo"
        btn.setOnClickListener {
            if (Bus.running) stopJarvis() else startJarvis()
        }
        row.addView(gear, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(btn, LinearLayout.LayoutParams(0, -2, 2f))
        panel.addView(row, LinearLayout.LayoutParams(-1, -2))
        val lp = FrameLayout.LayoutParams(-1, -2)
        lp.gravity = Gravity.BOTTOM
        root.addView(panel, lp)
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
