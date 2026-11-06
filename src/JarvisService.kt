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
import android.net.Uri
import android.media.AudioManager
import android.media.ToneGenerator
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
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.io.IOException
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
    private var busy = false
    private var closing = false
    private var glow: EdgeGlow? = null
    private var wl: PowerManager.WakeLock? = null
    private var voskModel: Model? = null
    private var vosk: SpeechService? = null
    private var voskReady = false
    private var awakePending = false
    private val ui = Handler(Looper.getMainLooper())
    private val hist = JSONArray()
    private val wake = listOf("jarvis", "jarvish", "jarwis", "jervis", "jarves", "harvis", "jarvi", "service", "travis")
    private val stop = setOf("youtube", "pe", "par", "search", "karo", "kar", "ka", "ke", "ki", "open", "khol", "kholo", "dhundo", "play", "chalao", "mein", "me")
    private val SYS = "Tum Jarvis ho, mere dost jaise. Hamesha Hinglish (Roman letters) mein pyaar aur apnepan se baat karo. Jawab chhote rakho, 2 ya 3 vaakya. Coding poochu toh pehle saral shabdon mein samjhao, phir code do."

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
            Bus.log("Jarvis chal raha hai. Shuru mein Jarvis bolo.")
            initTts()
            showGlow()
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "jarvis:mic")
            wl!!.acquire()
            initVosk()
        }
        return START_STICKY
    }

    private fun initVosk() {
        try {
            StorageService.unpack(this, "model-en-in", "model", { m: Model ->
                voskModel = m
                voskReady = true
                Bus.log("Offline mic taiyaar hai. Jarvis bolo.")
                startVosk()
            }, { _: IOException ->
                Bus.log("Offline mic model load nahi hua")
            })
        } catch (e: Exception) {
            Bus.log("Offline mic model load nahi hua")
        }
    }

    private fun startVosk() {
        val m = voskModel ?: return
        if (!Bus.running || vosk != null || busy) return
        try {
            val ss = SpeechService(org.vosk.Recognizer(m, 16000.0f), 16000.0f)
            vosk = ss
            Bus.mode = 1
            ss.startListening(object : VListener {
                override fun onPartialResult(h: String?) {
                    check(h)
                }
                override fun onResult(h: String?) {
                    check(h)
                }
                override fun onFinalResult(h: String?) {}
                override fun onError(e: Exception?) {
                    ui.post {
                        stopVosk()
                        ui.postDelayed({ startVosk() }, 1500)
                    }
                }
                override fun onTimeout() {}
            })
        } catch (e: Exception) {
            vosk = null
            Bus.log("Offline mic shuru nahi hua")
        }
    }

    private fun stopVosk() {
        try {
            vosk?.stop()
            vosk?.shutdown()
        } catch (e: Exception) {
        }
        vosk = null
    }

    private fun check(h: String?) {
        if (h == null || busy || awakePending) return
        try {
            val j = JSONObject(h)
            val t = (j.optString("partial", "") + " " + j.optString("text", "")).lowercase()
            if (t.isNotBlank()) Bus.log("Suna: " + t.trim())
            if (wake.any { t.contains(it) }) {
                awakePending = true
                stopVosk()
                Bus.log("Ji? Boliye...")
                ui.postDelayed({
                    awakePending = false
                    startVosk()
                }, 2500)
            }
        } catch (e: Exception) {
        }
    }

    private fun initTts() {
        tts = TextToSpeech(this) { st ->
            if (st == TextToSpeech.SUCCESS) {
                tts!!.setLanguage(Locale("hi", "IN"))
                tts!!.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String?) {}
                    override fun onError(id: String?) {}
                    override fun onDone(id: String?) {}
                })
                ttsReady = true
                speak("Namaste mere dost, main Jarvis hoon. Mujhe bulane ke liye Jarvis bolo")
            }
        }
    }

    private fun speak(t: String) {
        Bus.log("Jarvis: " + t)
        if (!ttsReady) return
        tts!!.speak(t, TextToSpeech.QUEUE_FLUSH, null, "u" + System.currentTimeMillis())
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
        stopVosk()
        tts?.shutdown()
        Bus.log("Jarvis band ho gaya.")
    }
}
