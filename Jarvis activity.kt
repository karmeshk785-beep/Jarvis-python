package com.jarvis.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import rikka.shizuku.Shizuku

class JarvisActivity : Activity() {
    private lateinit var hud: Hud
    private lateinit var log: TextView
    private lateinit var scroll: ScrollView
    private lateinit var toggle: Button
    private var lastRun = -1
    private lateinit var settings: LinearLayout
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
        scroll = ScrollView(this)
        scroll.addView(log)
        panel.addView(scroll, LinearLayout.LayoutParams(-1, (150 * d).toInt()))
        
        val lp = FrameLayout.LayoutParams(-1, -2)
        lp.gravity = Gravity.BOTTOM
        root.addView(panel, lp)
        
        toggle = Button(this)
        toggle.textSize = 13f
        toggle.setTextColor(Color.WHITE)
        toggle.setOnClickListener { if (Bus.running) stopJarvis() else startJarvis() }
        val size = (88 * d).toInt()
        val tlp = FrameLayout.LayoutParams(size, size)
        tlp.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        tlp.bottomMargin = (210 * d).toInt()
        root.addView(toggle, tlp)
        
        val gear = Button(this)
        gear.text = "Settings"
        gear.setOnClickListener { settings.visibility = View.VISIBLE }
        val glp = FrameLayout.LayoutParams(-2, -2)
        glp.gravity = Gravity.TOP or Gravity.END
        glp.topMargin = (36 * d).toInt()
        glp.rightMargin = (10 * d).toInt()
        root.addView(gear, glp)
        
        buildSettingsUI(root, d)
        setContentView(root)
        goFull()
    }

    private fun buildSettingsUI(root: FrameLayout, d: Float) {
        settings = LinearLayout(this)
        settings.orientation = LinearLayout.VERTICAL
        settings.setBackgroundColor(Color.argb(240, 20, 20, 20))
        settings.setPadding((20 * d).toInt(), (50 * d).toInt(), (20 * d).toInt(), (20 * d).toInt())
        settings.visibility = View.GONE
        
        val closeBtn = Button(this)
        closeBtn.text = "Close Settings"
        closeBtn.setOnClickListener { settings.visibility = View.GONE }
        settings.addView(closeBtn)

        val keyInput = EditText(this)
        keyInput.hint = "Groq API Key"
        keyInput.setText(getSharedPreferences("j", 0).getString("k", ""))
        settings.addView(keyInput)

        val pinInput = EditText(this)
        pinInput.hint = "Phone Unlock PIN (Shizuku)"
        pinInput.setText(getSharedPreferences("j", 0).getString("pin", ""))
        settings.addView(pinInput)

        val saveBtn = Button(this)
        saveBtn.text = "Save Keys"
        saveBtn.setOnClickListener {
            getSharedPreferences("j", 0).edit().putString("k", keyInput.text.toString()).putString("pin", pinInput.text.toString()).apply()
            Toast.makeText(this, "Saved!", Toast.LENGTH_SHORT).show()
        }
        settings.addView(saveBtn)

        val shizukuBtn = Button(this)
        shizukuBtn.text = "Grant Shizuku Permission"
        shizukuBtn.setOnClickListener {
            try {
                if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                    Shizuku.requestPermission(0)
                } else {
                    Toast.makeText(this, "Already Granted!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Error: Shizuku chalu hai?", Toast.LENGTH_SHORT).show()
            }
        }
        settings.addView(shizukuBtn)
        root.addView(settings, FrameLayout.LayoutParams(-1, -1))
    }

    private fun goFull() {
        window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
    }

    override fun onResume() { super.onResume(); Bus.appVisible = true; polling = true; ui.removeCallbacks(tick); ui.post(tick) }
    override fun onPause() { super.onPause(); Bus.appVisible = false; polling = false }

    private fun startJarvis() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS), 1)
            return
        }
        val i = Intent(this, JarvisService::class.java)
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
    }

    private fun stopJarvis() { stopService(Intent(this, JarvisService::class.java)) }
}
