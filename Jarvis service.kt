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
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.WindowManager
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

object Bus {
    var running = false
    var mode = 0
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
    private var wl: PowerManager.WakeLock? = null
    private var lastListen = 0L
    
    private val dog = object : Runnable {
        override fun run() {
            if (!Bus.running) return
            if (!busy && System.currentTimeMillis() - lastListen > 25000) listen()
            ui.postDelayed(this, 10000)
        }
    }
    private val ui = Handler(Looper.getMainLooper())
    private val hist = JSONArray()
    private val wake = listOf("jarvis", "jarvish", "जार्विस", "जारविस", "जर्विस", "जार्विश")
    private val SYS = "Tum Jarvis ho. Hamesha Hinglish mein pyaar se baat karo. Jawab chhote rakho."

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        val say = i?.getStringExtra("say")
        if (say != null) { if (Bus.running && ttsReady) speak(say); return START_STICKY }
        if (!Bus.running) {
            try { goForeground() } catch (e: Exception) { stopSelf(); return START_NOT_STICKY }
            Bus.running = true; closing = false
            initTts(); showGlow(); startWake()
            sr = SpeechRecognizer.createSpeechRecognizer(this)
            sr!!.setRecognitionListener(this)
            ui.postDelayed(dog, 15000)
        }
        return START_STICKY
    }

    private fun startWake() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "jarvis:mic")
            wl!!.acquire()
        } catch (e: Exception) {}
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
        } catch (e: Exception) {}
    }

    private fun goForeground() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val pi = PendingIntent.getActivity(this, 0, Intent(this, JarvisActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val nb: Notification.Builder = if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel("jarvis", "Jarvis", NotificationManager.IMPORTANCE_LOW))
            Notification.Builder(this, "jarvis")
        } else Notification.Builder(this)
        nb.setContentTitle("Jarvis chal raha hai").setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentIntent(pi)
        if (Build.VERSION.SDK_INT >= 30) startForeground(1, nb.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        else startForeground(1, nb.build())
    }

    private fun initTts() {
        tts = TextToSpeech(this) { st ->
            if (st == TextToSpeech.SUCCESS) {
                tts!!.setLanguage(Locale("hi", "IN"))
                tts!!.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String?) {}
                    override fun onError(id: String?) { ui.post { afterSpeak() } }
                    override fun onDone(id: String?) { ui.post { afterSpeak() } }
                })
                ttsReady = true
                speak("Namaste dost, main Jarvis hoon.")
            }
        }
    }

    private fun speak(t: String) {
        var s = t.replace("*", "").take(3500)
        Bus.log("Jarvis: $s")
        stopListening(); Bus.mode = 2; busy = true
        if (!ttsReady) { ui.postDelayed({ afterSpeak() }, 1500); return }
        tts!!.speak(s, TextToSpeech.QUEUE_FLUSH, null, "u" + System.currentTimeMillis())
    }

    private fun afterSpeak() {
        busy = false
        if (closing) { stopSelf(); return }
        if (Bus.running) ui.postDelayed({ listen() }, 1500) else Bus.mode = 0
    }

    private fun listen() {
        if (!Bus.running || busy || !SpeechRecognizer.isRecognitionAvailable(this)) return
        try { sr?.cancel() } catch (e: Exception) {}
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
        i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000)
        i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 3000)
        Bus.mode = 1; lastListen = System.currentTimeMillis()
        try { sr!!.startListening(i) } catch (e: Exception) {}
    }

    private fun stopListening() { try { sr?.cancel() } catch (e: Exception) {} }

    override fun onReadyForSpeech(p: Bundle?) {}
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(v: Float) {}
    override fun onBufferReceived(b: ByteArray?) {}
    override fun onEndOfSpeech() {}
    override fun onPartialResults(p: Bundle?) {}
    override fun onEvent(t: Int, p: Bundle?) {}
    
    // MIC BLINK FIX (3 Sec Delay)
    override fun onError(e: Int) { if (Bus.running && !busy) ui.postDelayed({ listen() }, 3000) }
    
    override fun onResults(r: Bundle?) {
        val l = r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        handle(if (l != null && l.size > 0) l[0].lowercase() else "")
    }

    private fun handle(q: String) {
        if (q.isEmpty()) { ui.postDelayed({ listen() }, 1500); return }
        Bus.log("Aapne kaha: $q")
        var w = q
        if (wake.none { q.contains(it) }) { ui.postDelayed({ listen() }, 1500); return }
        for (x in wake) w = w.replace(x, "")
        w = w.trim(); busy = true
        val sk = Skills.handle(this, w, tts)
        if (sk != null) { speak(sk); return }
        ai(w)
    }

    private fun ai(q: String) {
        val key = getSharedPreferences("j", 0).getString("k", "") ?: ""
        if (key.isEmpty()) { speak("Dost, settings mein Groq key save karo"); return }
        Bus.mode = 3
        hist.put(JSONObject().put("role", "user").put("content", q))
        Thread {
            var ans = ""
            try {
                val msgs = JSONArray()
                msgs.put(JSONObject().put("role", "system").put("content", SYS))
                val mem = getSharedPreferences("j_memory", 0).getString("notes", "") ?: ""
                if (mem.isNotEmpty()) msgs.put(JSONObject().put("role", "system").put("content", "User ki yaadein: $mem"))
                val st = Math.max(0, hist.length() - 6)
                for (i in st until hist.length()) msgs.put(hist.get(i))
                val body = JSONObject().put("model", "llama3-8b-8192").put("messages", msgs)
                val c = URL("https://api.groq.com/openai/v1/chat/completions").openConnection() as HttpURLConnection
                c.requestMethod = "POST"; c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json")
                c.setRequestProperty("Authorization", "Bearer $key")
                c.outputStream.use { it.write(body.toString().toByteArray()) }
                if (c.responseCode == 200) {
                    val txt = c.inputStream.bufferedReader().readText()
                    ans = JSONObject(txt).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content").trim()
                    hist.put(JSONObject().put("role", "assistant").put("content", ans))
                } else ans = "Dost, Groq error de raha hai."
            } catch (e: Exception) { ans = "Dost, internet check karo." }
            ui.post { while (hist.length() > 10) hist.remove(0); speak(ans) }
        }.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        Bus.running = false; Bus.mode = 0; ui.removeCallbacks(dog)
        try { wl?.release() } catch (e: Exception) {}
        try { sr?.destroy() } catch (e: Exception) {}
        tts?.shutdown()
    }
}
