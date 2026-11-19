package com.jarvis.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.WindowManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.vosk.Model
import org.vosk.android.SpeechService
import org.vosk.android.StorageService
import org.vosk.android.RecognitionListener as VListener

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

class JarvisService : Service() {
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var closing = false
    private var glow: EdgeGlow? = null
    private var wl: PowerManager.WakeLock? = null
    private var voskModel: Model? = null
    private var vosk: SpeechService? = null
    private var voskReady = false
    private var awake = false
    private var busyUntil = 0L
    private val ui = Handler(Looper.getMainLooper())
    private val hist = JSONArray()
    private val wake = listOf("hello", "helo", "hallo", "jarvis", "jarvish", "jarwis", "jervis", "jarves", "harvis", "jarvi", "service", "travis", "हेलो", "हैलो", "जार्विस", "जारविस")
    private val stop = setOf("youtube", "yutube", "pe", "par", "per", "search", "karo", "kro", "kar", "ka", "ke", "ki", "open", "khol", "kholo", "dhundo", "play", "chalao", "mein", "me", "store", "app", "यूट्यूब", "पर", "पे", "सर्च", "करो", "खोलो", "चलाओ")
    private val SYS = "Tum Jarvis ho, mere dost jaise ek voice assistant, jo Android phone par 24 ghante chalta hai. Hamesha Hinglish (Roman letters) mein pyaar aur apnepan se baat karo. Jawab chhote rakho, 2 ya 3 vaakya. Meri feeling samjho. Jab main udaas ho ya dard bhari baat karun toh dil ko chhoone wala halka dardila shayari jaisa andaaz rakho. Jab shayari maangun toh 4 line ki shayari sunao. Coding poochu toh pehle saral shabdon mein samjhao, phir code do."

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        if (!Bus.running) {
            try {
                goForeground()
            } catch (e: Exception) {
                stopSelf()
                return START_NOT_STICKY
            }
            Bus.running = true
            closing = false
            Bus.log("Jarvis chal raha hai 24 ghante. Jarvis ya hello bolo.")
            initTts()
            showGlow()
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "jarvis:mic")
            wl!!.acquire()
            VoiceId.start()
            initVosk()
        }
        return START_STICKY
    }

    private fun initVosk() {
        try {
            StorageService.unpack(this, "model-en-in", "model", { m: Model ->
                voskModel = m
                voskReady = true
                Bus.log("Mic 24 ghante taiyaar hai. Jarvis bolo.")
                startVosk()
            }, { _: IOException ->
                Bus.log("Model load nahi hua")
            })
        } catch (e: Exception) {
            Bus.log("Model load nahi hua")
        }
    }

    private fun startVosk() {
        val m = voskModel ?: return
        if (!Bus.running || vosk != null) return
        try {
            val ss = SpeechService(org.vosk.Recognizer(m, 16000.0f), 16000.0f)
            vosk = ss
            Bus.mode = 1
            ss.startListening(object : VListener {
                override fun onPartialResult(h: String?) {}

                override fun onResult(h: String?) {
                    heard(h)
                }

                override fun onFinalResult(h: String?) {
                    heard(h)
                }

                override fun onError(e: Exception?) {
                    ui.post {
                        vosk = null
                        startVosk()
                    }
                }

                override fun onTimeout() {}
            })
        } catch (e: Exception) {
            vosk = null
            ui.postDelayed({ startVosk() }, 500)
        }
    }

    private fun heard(h: String?) {
        if (h == null) return
        try {
            val j = JSONObject(h)
            val t = j.optString("text", "").trim().lowercase()
            if (t.isEmpty()) return
            if (System.currentTimeMillis() < busyUntil) {
                Bus.log("(Jarvis bol raha tha, ignore) " + t)
                return
            }
            Bus.log("Suna: " + t)
            handle(t)
        } catch (e: Exception) {
        }
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

    private fun handle(raw: String) {
        var w = raw
        val wasAwake = awake
        awake = false
        val hitWake = wake.any { w.contains(it) }
        if (!wasAwake && !hitWake) return
        if (hitWake) {
            for (x in wake) w = w.replace(x, "")
        }
        w = w.trim()
        if (has(w, "awaaz yaad karo", "mera awaaz yaad karo", "owner yaad karo", "awaaz yaad rakho")) {
            VoiceId.enroll(this)
            speak("Theek hai dost, maine tumhari awaaz yaad kar li")
            return
        }
        if (VoiceId.hasOwner(this) && !VoiceId.isOwner(this)) {
            Bus.log("(Dusri awaaz, ignore)")
            return
        }
        if (w.isEmpty()) {
            awake = true
            Bus.log("Ji? Boliye...")
            try {
                ToneGenerator(AudioManager.STREAM_MUSIC, 70).startTone(ToneGenerator.TONE_PROP_ACK, 150)
            } catch (e: Exception) {
            }
            ui.postDelayed({ awake = false }, 8000)
            return
        }
        val sk = Skills.handle(this, w, tts)
        if (sk != null) {
            speak(sk)
            return
        }
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
                body.put("max_tokens", 500)
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
                    while (hist.length() > 12) hist.remove(0)
                }
            } catch (e: Exception) {
                ans = "Dost, internet ya key mein dikkat hai"
            }
            ui.post { speak(ans.replace("*", "")) }
        }.start()
    }

    private fun initTts() {
        tts = TextToSpeech(this) { st ->
            if (st == TextToSpeech.SUCCESS) {
                tts!!.setLanguage(Locale("hi", "IN"))
                tts!!.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String?) {}
                    override fun onError(id: String?) {}
                    override fun onDone(id: String?) {
                        if (closing) {
                            ui.post { stopSelf() }
                        }
                    }
                })
                ttsReady = true
                speak("Namaste mere dost, main Jarvis hoon, chaubees ghante taiyaar. Mujhe bulane ke liye Jarvis ya hello bolo")
            }
        }
    }

    private fun speak(t: String) {
        var s = t
        if (s.length > 2000) s = s.substring(0, 2000)
        Bus.log("Jarvis: " + s)
        if (!ttsReady) return
        val ms = (s.length * 70L) + 1200L
        busyUntil = System.currentTimeMillis() + ms
        tts!!.speak(s, TextToSpeech.QUEUE_FLUSH, null, "u" + System.currentTimeMillis())
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
            if (g != null) (getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(g)
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
        nb.setContentText("Jarvis bolke bulao.")
        nb.setSmallIcon(android.R.drawable.ic_btn_speak_now)
        nb.setContentIntent(pi)
        val n = nb.build()
        if (Build.VERSION.SDK_INT >= 30) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(1, n)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Bus.running = false
        Bus.mode = 0
        try {
            wl?.release()
        } catch (e: Exception) {
        }
        hideGlow()
        try {
            vosk?.stop()
            vosk?.shutdown()
        } catch (e: Exception) {
        }
        VoiceId.stop()
        tts?.shutdown()
        Bus.log("Jarvis band ho gaya.")
    }
}
