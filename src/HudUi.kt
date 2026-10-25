package com.jarvis.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import kotlin.math.cos
import kotlin.math.sin

class Hud(c: Context) : View(c) {
    var mode = 0
    var theme = 0
    var ringsOn = false
    var photo: Bitmap? = null
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val src = Rect()
    private val dst = Rect()
    private val themes = arrayOf(intArrayOf(0, 200, 255), intArrayOf(255, 140, 20), intArrayOf(40, 230, 120), intArrayOf(255, 60, 70))

    override fun onDraw(cv: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        cv.drawColor(Color.rgb(3, 6, 12))
        val ph = photo
        if (ph != null) {
            val sw = ph.width
            val sh = ph.height
            val scale = Math.max(w / sw, h / sh)
            val cw = (w / scale).toInt()
            val ch = (h / scale).toInt()
            src.set((sw - cw) / 2, (sh - ch) / 2, (sw + cw) / 2, (sh + ch) / 2)
            dst.set(0, 0, width, height)
            p.style = Paint.Style.FILL
            p.alpha = 255
            cv.drawBitmap(ph, src, dst, p)
            p.color = Color.argb(45, 0, 0, 0)
            cv.drawRect(0f, 0f, w, h, p)
        }
        if (ph != null && !ringsOn) {
            postInvalidateDelayed(500)
            return
        }
        val t = System.currentTimeMillis() / 1000.0
        val cx = w / 2f
        val cy = h * 0.40f
        val rad = Math.min(w, h) * 0.30f
        val th = themes[theme]
        val ring = Color.rgb(th[0], th[1], th[2])
        val spd = when (mode) {
            1 -> 1.6
            2 -> 2.4
            3 -> 3.6
            else -> 0.7
        }
        p.style = Paint.Style.STROKE
        for (i in 0 until 5) {
            val r = rad * (0.55f + i * 0.17f)
            p.strokeWidth = if (i % 2 == 0) 3f else 1.5f
            p.color = ring
            p.alpha = 220 - i * 25
            rect.set(cx - r, cy - r, cx + r, cy + r)
            val a0 = (t * spd * (if (i % 2 == 0) 40 else -55) + i * 70).toFloat()
            cv.drawArc(rect, a0, 70f + i * 20f, false, p)
            cv.drawArc(rect, a0 + 180f, 40f + i * 10f, false, p)
        }
        p.strokeWidth = 2f
        p.color = ring
        p.alpha = 150
        val tr = rad * 1.5f
        for (k in 0 until 60) {
            val ang = Math.toRadians(k * 6.0 + t * spd * 8)
            val l = if (k % 5 == 0) 16f else 7f
            cv.drawLine((cx + cos(ang) * tr).toFloat(), (cy + sin(ang) * tr).toFloat(), (cx + cos(ang) * (tr + l)).toFloat(), (cy + sin(ang) * (tr + l)).toFloat(), p)
        }
        val core = when (mode) {
            1 -> Color.rgb(120, 240, 255)
            2 -> Color.rgb(255, 140, 20)
            3 -> Color.rgb(180, 120, 255)
            else -> ring
        }
        p.style = Paint.Style.FILL
        val pulse = (1.0 + 0.10 * sin(t * (if (mode == 0) 1.5 else 6.0))).toFloat()
        for (j in 4 downTo 1) {
            p.color = core
            p.alpha = 18 + (4 - j) * 16
            cv.drawCircle(cx, cy, rad * 0.26f * (1f + j * 0.35f) * pulse, p)
        }
        p.color = core
        p.alpha = 255
        cv.drawCircle(cx, cy, rad * 0.26f * pulse, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 4f
        p.color = ring
        p.alpha = 170
        for (b in 0 until 24) {
            val bh = (10 + 24 * Math.abs(sin(t * (if (mode == 0) 1.0 else 5.0) + b * 0.5))).toFloat()
            val bx = w * 0.12f + b * (w * 0.76f / 24f)
            val by = h * 0.70f
            cv.drawLine(bx, by, bx, by - bh, p)
        }
        p.style = Paint.Style.FILL
        for (d in 0 until 3) {
            val ang = t * spd * (0.8 + d * 0.5) + d * 2.1
            val rr = rad * (1.2f + d * 0.22f)
            p.color = if (d == 1) Color.rgb(255, 190, 40) else ring
            p.alpha = 255
            cv.drawCircle((cx + cos(ang) * rr).toFloat(), (cy + sin(ang) * rr).toFloat(), 7f, p)
        }
        postInvalidateOnAnimation()
    }
}

class EdgeGlow(c: Context, private val inApp: Boolean) : View(c) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private var sTop: LinearGradient? = null
    private var sBottom: LinearGradient? = null
    private var sLeft: LinearGradient? = null
    private var sRight: LinearGradient? = null
    private var th = 0f

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        th = resources.displayMetrics.density * 34f
        val a = 0xFFFFFFFF.toInt()
        val z = 0x00FFFFFF
        sTop = LinearGradient(0f, 0f, 0f, th, a, z, Shader.TileMode.CLAMP)
        sBottom = LinearGradient(0f, h.toFloat(), 0f, h - th, a, z, Shader.TileMode.CLAMP)
        sLeft = LinearGradient(0f, 0f, th, 0f, a, z, Shader.TileMode.CLAMP)
        sRight = LinearGradient(w.toFloat(), 0f, w - th, 0f, a, z, Shader.TileMode.CLAMP)
    }

    override fun onDraw(cv: Canvas) {
        if (!inApp && !context.getSharedPreferences("j", 0).getBoolean("edge", true)) {
            postInvalidateDelayed(500)
            return
        }
        val t = System.currentTimeMillis() / 1000.0
        var r = 0
        var g = 200
        var b = 255
        var k = 0.0
        when (Bus.mode) {
            1 -> {
                k = 0.55
            }
            2 -> {
                r = 255
                g = 20
                b = 20
                k = 0.75 + 0.25 * sin(t * 9)
            }
            3 -> {
                r = 170
                g = 110
                b = 255
                k = 0.6 + 0.3 * sin(t * 5)
            }
            else -> {
                if (inApp) k = 0.3
            }
        }
        if (k <= 0.0) {
            postInvalidateDelayed(250)
            return
        }
        p.colorFilter = PorterDuffColorFilter(Color.rgb(r, g, b), PorterDuff.Mode.SRC_IN)
        p.alpha = (255 * k).toInt().coerceIn(0, 255)
        val w = width.toFloat()
        val h = height.toFloat()
        p.shader = sTop
        cv.drawRect(0f, 0f, w, th, p)
        p.shader = sBottom
        cv.drawRect(0f, h - th, w, h, p)
        p.shader = sLeft
        cv.drawRect(0f, 0f, th, h, p)
        p.shader = sRight
        cv.drawRect(w - th, 0f, w, h, p)
        postInvalidateOnAnimation()
    }
}

class SettingsView(private val act: JarvisActivity) : ScrollView(act) {
    private fun prefs() = act.getSharedPreferences("j", 0)

    private fun label(t: String): TextView {
        val v = TextView(act)
        v.text = t
        v.setTextColor(Color.rgb(0, 220, 255))
        v.textSize = 16f
        v.setPadding(0, 30, 0, 6)
        return v
    }

    private fun btn(t: String, f: () -> Unit): Button {
        val b = Button(act)
        b.text = t
        b.setOnClickListener { f() }
        return b
    }

    private fun seek(start: Int, max: Int, f: (Int) -> Unit): SeekBar {
        val s = SeekBar(act)
        s.max = max
        s.progress = start
        s.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, v: Int, user: Boolean) {
                if (user) f(v)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
        return s
    }

    private fun sw(t: String, key: String, def: Boolean): Switch {
        val s = Switch(act)
        s.text = t
        s.setTextColor(Color.WHITE)
        s.isChecked = prefs().getBoolean(key, def)
        s.setOnCheckedChangeListener { _, b ->
            prefs().edit().putBoolean(key, b).apply()
            act.applySettings()
        }
        return s
    }

    init {
        setBackgroundColor(Color.argb(240, 4, 8, 16))
        val col = LinearLayout(act)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(36, 90, 36, 80)
        addView(col)
        val title = TextView(act)
        title.text = "Jarvis Settings"
        title.setTextColor(Color.WHITE)
        title.textSize = 24f
        col.addView(title)

        col.addView(label("Groq key"))
        val keyBox = EditText(act)
        keyBox.hint = "Nayi key yahan paste karo"
        keyBox.setHintTextColor(Color.GRAY)
        keyBox.setTextColor(Color.WHITE)
        keyBox.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        keyBox.setSingleLine(true)
        col.addView(keyBox)
        col.addView(btn("Key save karo") {
            val k = keyBox.text.toString().trim().replace(" ", "")
            if (k.length > 20) {
                prefs().edit().putString("k", k).apply()
                keyBox.setText("")
                act.toast("Key save ho gayi")
            } else {
                act.toast("Key adhoori lag rahi hai")
            }
        })

        col.addView(label("Background photo"))
        col.addView(btn("Gallery se photo chuno") { act.pickPhoto() })
        col.addView(btn("Photo hatao") { act.clearPhoto() })

        col.addView(label("HUD ka rang"))
        val row = LinearLayout(act)
        row.orientation = LinearLayout.HORIZONTAL
        val names = arrayOf("Neela", "Narangi", "Hara", "Laal")
        for (i in 0 until 4) {
            val b = btn(names[i]) {
                prefs().edit().putInt("theme", i).apply()
                act.applySettings()
            }
            row.addView(b, LinearLayout.LayoutParams(0, -2, 1f))
        }
        col.addView(row)

        col.addView(label("Awaaz ki speed"))
        col.addView(seek(prefs().getInt("rate", 5), 20) { v ->
            prefs().edit().putInt("rate", v).apply()
            act.applySettings()
        })
        col.addView(label("Awaaz ki pitch"))
        col.addView(seek(prefs().getInt("pitch", 5), 20) { v ->
            prefs().edit().putInt("pitch", v).apply()
            act.applySettings()
        })

        col.addView(label("Baaki settings"))
        col.addView(sw("Sirf Jarvis bolne par jawab do", "wake", true))
        col.addView(sw("Hindi sunna (band karo toh English)", "hi", true))
        col.addView(sw("Photo ke upar rings bhi dikhao", "rings", false))
        col.addView(sw("Screen ke kinare light (doosri apps par bhi)", "edge", true))
        col.addView(btn("Awaaz test karo") { act.testVoice() })
        col.addView(btn("Settings band karo") { act.closeSettings() })
    }
}
