package com.jarvis.app

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.text.InputType
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class Orb(c: Context) : View(c) {
    var mode = 0
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(cv: Canvas) {
        val t = System.currentTimeMillis() / 1000.0
        val cx = width / 2f
        val cy = height / 2f
        val base = Math.min(width, height) * 0.22f
        val speed = when (mode) {
            1 -> 3.0
            2 -> 6.0
            3 -> 9.0
            else -> 1.2
        }
        val amp = if (mode == 0) 0.05 else 0.14
        val col = when (mode) {
            1 -> Color.rgb(0, 200, 255)
            2 -> Color.rgb(255, 170, 0)
            3 -> Color.rgb(170, 120, 255)
            else -> Color.rgb(60, 120, 160)
        }
        for (i in 4 downTo 1) {
            val r = base * (1f + i * 0.28f) * (1f + (amp * Math.sin(t * speed + i)).toFloat())
            p.color = col
            p.alpha = 20 + (4 - i) * 14
            cv.drawCircle(cx, cy, r, p)
        }
        p.color = col
        p.alpha = 255
        cv.drawCircle(cx, cy, base, p)
        postInvalidateOnAnimation()
    }
}

class MainActivity : Activity(), RecognitionListener {
    private lateinit var orb: Orb
    private lateinit var log: TextView
    private lateinit var scroll: ScrollView
    private lateinit var btn: Button
    private lateinit var keyBox: EditText
    private var sr: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var running = false
    private var busy = false
    private var paused = false
    private val ui = Handler(Looper.getMainLooper())
    private val hist = JSONArray()
    private val wake = listOf("jarvis", "jarvish", "jarwis", "जार्विस", "जारविस", "जर्विस", "जार्विश", "1120", "11 20")
    private val stop = setOf("youtube", "yutube", "pe", "par", "per", "search", "karo", "kro", "kar", "ka", "ke", "ki", "open", "khol", "kholo", "dhundo", "play", "chalao", "mein", "me", "store", "app", "यूट्यूब", "पर", "पे", "सर्च", "करो", "खोलो", "चलाओ")
    private val SYS = "Tum Jarvis ho, mere apne dost jaise. Hamesha Hinglish (Roman letters) mein pyaar aur apnepan se baat karo. Jawab chhote rakho, 2 ya 3 vaakya. Meri feeling samjho. Coding poochu toh pehle saral shabdon mein samjhao, phir code do."

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Color.rgb(8, 12, 20))
        root.setPadding(24, 70, 24, 24)
        orb = Orb(this)
        root.addView(orb, LinearLayout.LayoutParams(-1, 0, 1.2f))
        log = TextView(this)
        log.setTextColor(Color.WHITE)
        log.textSize = 15f
        scroll = ScrollView(this)
        scroll.addView(log)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        keyBox = EditText(this)
        keyBox.hint = "Groq key yahan paste karo"
        keyBox.setHintTextColor(Color.GRAY)
        keyBox.setTextColor(Color.WHITE)
        keyBox.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        keyBox.setSingleLine(true)
        root.addView(keyBox, LinearLayout.LayoutParams(-1, -2))
        val save = Button(this)
        save.text = "Key save karo"
        save.setOnClickListener {
            val k = keyBox.text.toString().trim().replace(" ", "")
            if (k.length > 20) {
                getSharedPreferences("j", 0).edit().putString("k", k).apply()
                keyBox.setText("")
                toast("Key save ho gayi")
            } else {
                toast("Key adhoori lag rahi hai")
            }
        }
        root.addView(save, LinearLayout.LayoutParams(-1, -2))
        btn = Button(this)
        btn.text = "Jarvis shuru karo"
        btn.setOnClickListener {
            if (running) stopAll() else startAll()
        }
        root.addView(btn, LinearLayout.LayoutParams(-1, -2))
        setContentView(root)
        tts = TextToSpeech(this) { st ->
            if (st == TextToSpeech.SUCCESS) {
                val r = tts!!.setLanguage(Locale("hi", "IN"))
                if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts!!.setLanguage(Locale.getDefault())
                }
                tts!!.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String?) {}
                    override fun onError(id: String?) {
                        ui.post { afterSpeak() }
                    }
                    override fun onDone(id: String?) {
                        ui.post { afterSpeak() }
                    }
                })
                ttsReady = true
            }
        }
        show("Pehle Groq key save karo, phir Jarvis shuru karo dabao. Bolte waqt shuru mein Jarvis bolo.")
    }

    private fun toast(s: String) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
    }

    private fun show(s: String) {
        log.append(s + "\n\n")
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun startAll() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
            toast("Mic Allow karke Jarvis shuru dobara dabao")
            return
        }
        running = true
        btn.text = "Jarvis band karo"
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        speak("Namaste mere dost, main Jarvis hoon. Mujhe bulane ke liye Jarvis bolo")
    }

    private fun stopAll() {
        running = false
        btn.text = "Jarvis shuru karo"
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        stopListening()
        orb.mode = 0
    }

    private fun speak(t: String) {
        var s = t.replace("*", "")
        if (s.length > 3500) s = s.substring(0, 3500)
        show("Jarvis: " + s)
        stopListening()
        orb.mode = 2
        if (!ttsReady) {
            ui.postDelayed({ afterSpeak() }, 1500)
            return
        }
        tts!!.speak(s, TextToSpeech.QUEUE_FLUSH, null, "u" + System.currentTimeMillis())
    }

    private fun afterSpeak() {
        busy = false
        if (running) ui.postDelayed({ listen() }, 400)
    }

    private fun listen() {
        if (!running || busy || paused) return
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            toast("Is phone par awaaz pehchaan nahi mil rahi")
            return
        }
        stopListening()
        sr = SpeechRecognizer.createSpeechRecognizer(this)
        sr!!.setRecognitionListener(this)
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
        orb.mode = 1
        sr!!.startListening(i)
    }

    private fun stopListening() {
        try {
            sr?.cancel()
            sr?.destroy()
        } catch (e: Exception) {
        }
        sr = null
    }

    override fun onReadyForSpeech(p: Bundle?) {}
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(v: Float) {}
    override fun onBufferReceived(b: ByteArray?) {}
    override fun onEndOfSpeech() {}
    override fun onPartialResults(p: Bundle?) {}
    override fun onEvent(t: Int, p: Bundle?) {}

    override fun onError(e: Int) {
        if (running && !busy) ui.postDelayed({ listen() }, 700)
    }

    override fun onResults(r: Bundle?) {
        val l = r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val q = if (l != null && l.size > 0) l[0].lowercase() else ""
        handle(q)
    }

    private fun has(s: String, vararg k: String): Boolean {
        return k.any { s.contains(it) }
    }

    private fun clean(s: String): String {
        return s.split(" ").filter { it.isNotEmpty() && !stop.contains(it) }.joinToString(" ")
    }

    private fun open(u: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u)))
        } catch (e: Exception) {
            toast("App nahi khul payi")
        }
    }

    private fun handle(q: String) {
        if (q.isEmpty()) {
            ui.postDelayed({ listen() }, 300)
            return
        }
        show("Aapne kaha: " + q)
        if (wake.none { q.contains(it) }) {
            ui.postDelayed({ listen() }, 300)
            return
        }
        var w = q
        for (x in wake) w = w.replace(x, "")
        w = w.trim()
        busy = true
        when {
            has(w, "bye", "alvida", "बाय", "अलविदा") -> {
                speak("Alvida mere dost, apna khayal rakhna")
                stopAll()
            }
            has(w, "time", "samay", "टाइम", "समय", "कितने बजे") -> {
                speak("Ji dost, abhi samay hai " + SimpleDateFormat("hh mm a", Locale.getDefault()).format(Date()))
            }
            has(w, "battery", "बैटरी") -> {
                val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
                val pc = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                speak("Dost, battery " + pc + " percent hai")
            }
            has(w, "youtube", "यूट्यूब") -> {
                val s = clean(w)
                if (s.isEmpty() || !has(w, "search", "सर्च", "play", "chalao", "dhundo", "चलाओ")) {
                    speak("Ji dost, YouTube khol raha hoon")
                    open("https://www.youtube.com")
                } else {
                    speak("Ji dost, YouTube par " + s + " dhoondh raha hoon")
                    open("https://www.youtube.com/results?search_query=" + URLEncoder.encode(s, "UTF-8"))
                }
            }
            has(w, "instagram", "इंस्टाग्राम") -> {
                speak("Ji dost, Instagram khol raha hoon")
                open("https://www.instagram.com")
            }
            has(w, "whatsapp", "व्हाट्सएप") -> {
                speak("Ji dost, WhatsApp khol raha hoon")
                open("https://wa.me")
            }
            has(w, "play store", "प्ले स्टोर") -> {
                val s = clean(w)
                speak("Ji dost, Play Store par " + s + " dhoondh raha hoon")
                open("https://play.google.com/store/search?c=apps&q=" + URLEncoder.encode(s, "UTF-8"))
            }
            has(w, "search", "सर्च") -> {
                val s = clean(w)
                speak("Ji dost, " + s + " search kar raha hoon")
                open("https://www.google.com/search?q=" + URLEncoder.encode(s, "UTF-8"))
            }
            w.isEmpty() -> {
                speak("Ji mere dost, boliye")
            }
            else -> ai(w)
        }
    }

    private fun ai(q: String) {
        val key = getSharedPreferences("j", 0).getString("k", "") ?: ""
        if (key.isEmpty()) {
            speak("Dost, pehle Groq key save karo")
            return
        }
        orb.mode = 3
        hist.put(JSONObject().put("role", "user").put("content", q))
        Thread {
            var ans = ""
            try {
                val msgs = JSONArray()
                msgs.put(JSONObject().put("role", "system").put("content", SYS))
                val st = Math.max(0, hist.length() - 8)
                for (i in st until hist.length()) msgs.put(hist.get(i))
                val body = JSONObject()
                body.put("model", "openai/gpt-oss-120b")
                body.put("messages", msgs)
                body.put("max_tokens", 1000)
                body.put("reasoning_effort", "low")
                val c = URL("https://api.groq.com/openai/v1/chat/completions").openConnection() as HttpURLConnection
                c.requestMethod = "POST"
                c.connectTimeout = 20000
                c.readTimeout = 40000
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json")
                c.setRequestProperty("Authorization", "Bearer " + key)
                c.setRequestProperty("User-Agent", "Mozilla/5.0")
                c.outputStream.use { it.write(body.toString().toByteArray()) }
                val code = c.responseCode
                if (code != 200) {
                    ans = "Dost, Groq ne error diya " + code
                } else {
                    val txt = c.inputStream.bufferedReader().readText()
                    ans = JSONObject(txt).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content").trim()
                    hist.put(JSONObject().put("role", "assistant").put("content", ans))
                }
            } catch (e: Exception) {
                ans = "Dost, internet ya key mein dikkat hai"
            }
            runOnUiThread { reply(ans) }
        }.start()
    }

    private fun reply(a: String) {
        while (hist.length() > 12) hist.remove(0)
        val i = a.indexOf("\u0060\u0060\u0060")
        if (i >= 0) {
            show("Code:\n" + a)
            speak(a.substring(0, i).trim() + " Code screen par likha hai.")
        } else {
            speak(a)
        }
    }

    override fun onPause() {
        super.onPause()
        paused = true
        stopListening()
    }

    override fun onResume() {
        super.onResume()
        paused = false
        if (running && !busy) listen()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopListening()
        tts?.shutdown()
    }
}
