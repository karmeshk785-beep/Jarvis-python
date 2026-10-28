package com.jarvis.ap
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.ttgressListener
import android.view.WindowManager
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Bus {
    var running = false
    var mode = 0
    var appVisible = false
    var version = 0
    val logs = StringBuilder()
    fun log(s: String) {
        logs.append(s).append("\n\n")
        if (logs.length > 6000) logs.delete(0, logs.length - 4000)
        version++
    }
}

class JarvisService : Service(), RecognitionListener {
    private var sr: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var busy = false
    private var closing = false
    private var glow: EdgeGlow? = null
    private val ui = Handler(Looper.getMainLooper())
    private val hist = JSONArray()
    private val wake = listOf("jarvis", "jarvish", "jarwis", "जार्विस", "जारविस", "जर्विस", "जार्विश", "1120", "11 20")
    private val stop = setOf("youtube", "yutube", "pe", "par", "per", "search", "karo", "kro", "kar", "ka", "ke", "ki", "open", "khol", "kholo", "dhundo", "play", "chalao", "mein", "me", "store", "app", "यूट्यूब", "पर", "पे", "सर्च", "करो", "खोलो", "चलाओ")
    private val SYS = "Tum Jarvis ho, ek voice assistant jo Android phone par chalta hai. Computer ya Windows ke steps kabhi mat batao. Phone ki settings ke liye sirf Android ke steps batao. Hamesha Hinglish (Roman letters) mein pyaar aur apnepan se baat karo. Jawab chhote rakho, 2 ya 3 vaakya. Meri feeling samjho. Coding poochu toh pehle saral shabdon mein samjhao, phir code do."

    override fun onBind(i: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        val say = i?.getStringExtra("say")
        if (say != null) {
            if (Bus.running && ttsReady) speak(say)
            return START_STICKY
        }
        if (!Bus.running) {
            try {
                goForeground()
            } catch (e: Exception) {
                Bus.log("Background mein chalne ki ijazat nahi mili")
                stopSelf()
                return START_NOT_STICKY
            }
            Bus.running = true
            closing = false
            Bus.log("Jarvis chal raha hai. Shuru mein Jarvis bolo.")
            initTts()
            showGlow()
        }
        return START_STICKY
    }

    private fun showGlow() {
        try {
            if (glow != null || !Settings.canDrawOverlays(this)) return
            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
            val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            val lp = WindowManager.LayoutParams(-1, -1, type, flags, PixelFormat.TRANSLUCENT)
            val g = EdgeGlow(this, false)
            wm.addView(g, lp)
            glow = g
        } catch (e: Exception) {
        }
    }

    private fun hideGlow() {
        try {
            val g = glow
            if (g != null) {
                (getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(g)
            }
        } catch (e: Exception) {
        }
        glow = null
    }

    private fun goForeground() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val pi = PendingIntent.getActivity(this, 0, Intent(this, JarvisActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val nb: Notification.Builder
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel("jarvis", "Jarvis", NotificationManager.IMPORTANCE_LOW))
            nb = Notification.Builder(this, "jarvis")
        } else {
            nb = Notification.Builder(this)
        }
        nb.setContentTitle("Jarvis chal raha hai")
        nb.setContentText("Jarvis bolke bulao. Kholne ke liye dabao.")
        nb.setSmallIcon(android.R.drawable.ic_btn_speak_now)
        nb.setContentIntent(pi)
        val n = nb.build()
        if (Build.VERSION.SDK_INT >= 30) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(1, n)
        }
    }

    private fun initTts() {
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
                speak("Namaste mere dost, main Jarvis hoon. Mujhe bulane ke liye Jarvis bolo")
            }
        }
    }

    private fun speak(t: String) {
        var s = t.replace("*", "")
        if (s.length > 3500) s = s.substring(0, 3500)
        Bus.log("Jarvis: " + s)
        stopListening()
        Bus.mode = 2
        busy = true
        if (!ttsReady) {
            ui.postDelayed({ afterSpeak() }, 1500)
            return
        }
        val p = getSharedPreferences("j", 0)
        tts!!.setSpeechRate(0.5f + p.getInt("rate", 5) * 0.1f)
        tts!!.setPitch(0.5f + p.getInt("pitch", 5) * 0.1f)
        tts!!.speak(s, TextToSpeech.QUEUE_FLUSH, null, "u" + System.currentTimeMillis())
    }

    private fun afterSpeak() {
        busy = false
        if (closing) {
            stopSelf()
            return
        }
        if (Bus.running) {
            ui.postDelayed({ listen() }, 400)
        } else {
            Bus.mode = 0
        }
    }

    private fun listen() {
        if (!Bus.running || busy) return
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Bus.log("Is phone par awaaz pehchaan nahi mil rahi")
            return
        }
        stopListening()
        sr = SpeechRecognizer.createSpeechRecognizer(this)
        sr!!.setRecognitionListener(this)
        val hindi = getSharedPreferences("j", 0).getBoolean("hi", true)
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (hindi) "hi-IN" else "en-IN")
        Bus.mode = 1
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
        if (Bus.running && !busy) ui.postDelayed({ listen() }, 700)
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
            val i = Intent(Intent.ACTION_VIEW, Uri.parse(u))
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(i)
        } catch (e: Exception) {
            Bus.log("App nahi khul payi")
        }
    }

    private fun handle(q: String) {
        if (q.isEmpty()) {
            ui.postDelayed({ listen() }, 300)
            return
        }
        Bus.log("Aapne kaha: " + q)
        var w = q
        if (getSharedPreferences("j", 0).getBoolean("wake", true)) {
            if (wake.none { q.contains(it) }) {
                ui.postDelayed({ listen() }, 300)
                return
            }
            for (x in wake) w = w.replace(x, "")
        }
        w = w.trim()
        busy = true
        when {
            has(w, "bye", "alvida", "बाय", "अलविदा") -> {
                closing = true
                speak("Alvida mere dost, apna khayal rakhna")
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
            speak("Dost, pehle settings mein Groq key save karo")
            return
        }
        Bus.mode = 3
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
                c.setRequestProperty("Content-Type", "application/json)
    }
}
