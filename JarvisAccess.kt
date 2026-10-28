package com.jarvis.app

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.hardware.camera2.CameraManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.ContactsContract
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.OutputStream
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
    var paused = false
    val logs = StringBuilder()
    fun log(s: String) {
        logs.append(s).append("\n\n")
        if (logs.length > 6000) logs.delete(0, logs.length - 4000)
        version++
    }
}

class JarvisAccess : AccessibilityService() {
    companion object {
        var inst: JarvisAccess? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        inst = this
        val i = AccessibilityServiceInfo()
        i.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        i.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        i.notificationTimeout = 200
        serviceInfo = i
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onUnbind(i: Intent?): Boolean {
        inst = null
        return super.onUnbind(i)
    }

    fun lock() {
        if (Build.VERSION.SDK_INT >= 28) performGlobalAction(8)
    }

    fun home() {
        performGlobalAction(2)
    }

    fun back() {
        performGlobalAction(1)
    }
}

class JarvisService : Service(), RecognitionListener {
    private var sr: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    @Volatile private var busy = false
    @Volatile private var recRun = false
    @Volatile private var quietUntil = 0L
    private var closing = false
    private var glow: EdgeGlow? = null
    private val ui = Handler(Looper.getMainLooper())
    private val hist = JSONArray()
    private var sinceOn = System.currentTimeMillis()
    private val wake = listOf("jarvis", "jarvish", "jarwis", "jarvas", "jarvice", "जार्विस", "जारविस", "जार्विश", "जर्विस", "जारविश", "चार्विस", "1120", "11 20")
    private val stop = setOf("youtube", "yutube", "play", "chalao", "chala", "laga", "lagao", "do", "dijiye", "pe", "par", "per", "search", "karo", "kro", "kar", "ka", "ke", "ki", "ko", "se", "mein", "me", "mujhe", "video", "dhundo", "kholo", "khol", "open", "यूट्यूब", "चलाओ", "करो", "पर", "में", "सर्च", "खोलो", "ढूंढो", "प्ले", "गाना", "gaana", "gana", "song")
    private val callStop = setOf("call", "karo", "kar", "kro", "lagao", "laga", "do", "ko", "ka", "phone", "mobile", "dial", "mujhe", "कॉल", "करो", "को", "फोन", "लगाओ", "लगा", "दो", "number", "नंबर")
    private val SYS = "Tum Jarvis ho, ek Hinglish voice assistant jo Android phone par chalta hai. Jawab chhota rakho (1 se 3 vaakya), seedha aur dostana. User jis tarah bole (Hindi ya English) usi tarah jawab do. Phone ke steps Android ke hisaab se batao, Windows ya computer ke nahi. Code tabhi likho jab user maange, aur code triple backtick ke andar likho."

    private val rcv = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            sinceOn = System.currentTimeMillis()
        }
    }

    private fun prefs(): SharedPreferences = getSharedPreferences("j", 0)

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
            Bus.paused = false
            closing = false
            sinceOn = System.currentTimeMillis()
            Bus.log("Jarvis chal raha hai. Shuru mein Jarvis bolo.")
            try {
                registerReceiver(rcv, IntentFilter(Intent.ACTION_USER_PRESENT))
            } catch (e: Exception) {
            }
            initTts()
            showGlow()
            if (useWhisper()) {
                Bus.mode = 1
                startRecorder()
            }
        }
        return START_STICKY
    }

    private fun useWhisper(): Boolean {
        val p = prefs()
        val k = p.getString("k", "") ?: ""
        return p.getBoolean("whisper", true) && k.isNotEmpty()
    }

    private fun showGlow() {
        try {
            if (glow != null || !Settings.canDrawOverlays(this)) return
            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
            val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            val lp = WindowManager.LayoutParams(-1, -1, type, flags, PixelFormat.TRANSLUCENT)
            lp.gravity = Gravity.TOP or Gravity.START
            lp.x = 0
            lp.y = 0
            if (Build.VERSION.SDK_INT >= 28) {
                lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
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
        if (!recRun) stopListening()
        Bus.mode = 2
        busy = true
        if (!ttsReady) {
            ui.postDelayed({ afterSpeak() }, 1500)
            return
        }
        val p = prefs()
        tts!!.setSpeechRate(0.5f + p.getInt("rate", 5) * 0.1f)
        tts!!.setPitch(0.5f + p.getInt("pitch", 5) * 0.1f)
        tts!!.speak(s, TextToSpeech.QUEUE_FLUSH, null, "u" + System.currentTimeMillis())
    }

    private fun afterSpeak() {
        busy = false
        quietUntil = SystemClock.elapsedRealtime() + 700
        if (closing) {
            stopSelf()
            return
        }
        if (Bus.running) {
            Bus.mode = 1
            if (!recRun) ui.postDelayed({ listen() }, 400)
        } else {
            Bus.mode = 0
        }
    }

    private fun resume(d: Long) {
        if (!Bus.running) return
        if (recRun) {
            busy = false
        } else {
            ui.postDelayed({ listen() }, d)
        }
    }

    // ---------- Purana mic (phone ka SpeechRecognizer) ----------

    private fun listen() {
        if (!Bus.running || busy) return
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Bus.log("Is phone par awaaz pehchaan nahi mil rahi")
            return
        }
        stopListening()
        sr = SpeechRecognizer.createSpeechRecognizer(this)
        sr!!.setRecognitionListener(this)
        val hindi = prefs().getBoolean("hi", true)
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
        if (Bus.running && !busy && !recRun) ui.postDelayed({ listen() }, 700)
    }

    override fun onResults(r: Bundle?) {
        val l = r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val q = if (l != null && l.size > 0) l[0].lowercase() else ""
        handle(q)
    }

    // ---------- Naya mic: hamesha chalu, Groq Whisper se text ----------

    private fun startRecorder() {
        if (recRun) return
        recRun = true
        Thread { recordLoop() }.start()
    }

    private fun putShorts(o: ByteArrayOutputStream, a: ShortArray) {
        for (v in a) {
            val x = v.toInt()
            o.write(x and 255)
            o.write((x shr 8) and 255)
        }
    }

    private fun recordLoop() {
        val rate = 16000
        val minBuf = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        var made: AudioRecord? = null
        try {
            val t = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, Math.max(minBuf, 16000))
            if (t.state != AudioRecord.STATE_INITIALIZED) {
                t.release()
                throw Exception("init")
            }
            t.startRecording()
            made = t
        } catch (e: Exception) {
        }
        val rr = made
        if (rr == null) {
            Bus.log("Naya mic shuru nahi hua, purana mic chala raha hoon")
            recRun = false
            ui.post { if (Bus.running && !busy) listen() }
            return
        }
        val chunk = ShortArray(800)
        val seg = ByteArrayOutputStream()
        val pre = ArrayList<ShortArray>()
        var inSpeech = false
        var silent = 0
        var speechN = 0
        var noise = 250.0
        try {
            while (recRun && Bus.running) {
                val n = rr.read(chunk, 0, chunk.size)
                if (n <= 0) {
                    Thread.sleep(30)
                    continue
                }
                var sum = 0.0
                for (i in 0 until n) {
                    val v = chunk[i].toDouble()
                    sum += v * v
                }
                val rms = Math.sqrt(sum / n)
                if (busy || Bus.paused || SystemClock.elapsedRealtime() < quietUntil) {
                    inSpeech = false
                    seg.reset()
                    pre.clear()
                    silent = 0
                    speechN = 0
                    continue
                }
                val thr = Math.max(600.0, noise * 3.0)
                val loud = rms > thr
                if (!inSpeech) {
                    noise = noise * 0.97 + rms * 0.03
                    pre.add(chunk.copyOf(n))
                    if (pre.size > 6) pre.removeAt(0)
                    if (loud) {
                        inSpeech = true
                        seg.reset()
                        for (c in pre) putShorts(seg, c)
                        pre.clear()
                        silent = 0
                        speechN = 1
                    }
                } else {
                    putShorts(seg, chunk.copyOf(n))
                    if (loud) {
                        silent = 0
                        speechN++
                    } else {
                        silent++
                    }
                    if (silent >= 14 || seg.size() > 16000 * 2 * 14) {
                        inSpeech = false
                        if (speechN >= 5) {
                            busy = true
                            val data = seg.toByteArray()
                            Thread { sendVoice(data) }.start()
                        }
                        seg.reset()
                        silent = 0
                        speechN = 0
                    }
                }
            }
        } catch (e: Exception) {
            Bus.log("Mic mein dikkat: " + e.message)
        } finally {
            try {
                rr.stop()
            } catch (x: Exception) {
            }
            try {
                rr.release()
            } catch (x: Exception) {
            }
            recRun = false
        }
    }

    private fun sendVoice(pcm: ByteArray) {
        var text = ""
        try {
            text = whisper(pcm)
        } catch (e: Exception) {
            Bus.log("Awaaz bhejne mein dikkat (internet ya key check karo)")
        }
        ui.post {
            val q = text.trim().lowercase()
            if (q.length < 2) {
                busy = false
            } else {
                handle(q)
            }
        }
    }

    private fun wavOf(pcm: ByteArray): ByteArray {
        val n = pcm.size
        val h = ByteArray(44)
        fun w4(o: Int, v: Int) {
            h[o] = (v and 255).toByte()
            h[o + 1] = ((v shr 8) and 255).toByte()
            h[o + 2] = ((v shr 16) and 255).toByte()
            h[o + 3] = ((v shr 24) and 255).toByte()
        }
        System.arraycopy("RIFF".toByteArray(Charsets.US_ASCII), 0, h, 0, 4)
        w4(4, 36 + n)
        System.arraycopy("WAVEfmt ".toByteArray(Charsets.US_ASCII), 0, h, 8, 8)
        w4(16, 16)
        h[20] = 1
        h[22] = 1
        w4(24, 16000)
        w4(28, 32000)
        h[32] = 2
        h[34] = 16
        System.arraycopy("data".toByteArray(Charsets.US_ASCII), 0, h, 36, 4)
        w4(40, n)
        val out = ByteArray(44 + n)
        System.arraycopy(h, 0, out, 0, 44)
        System.arraycopy(pcm, 0, out, 44, n)
        return out
    }

    private fun mpField(o: OutputStream, b: String, name: String, v: String) {
        o.write(("--" + b + "\r\nContent-Disposition: form-data; name=\"" + name + "\"\r\n\r\n" + v + "\r\n").toByteArray())
    }

    private fun whisper(pcm: ByteArray): String {
        val key = prefs().getString("k", "") ?: ""
        val b = "JarvisBoundary" + System.currentTimeMillis()
        val body = ByteArrayOutputStream()
        mpField(body, b, "model", "whisper-large-v3-turbo")
        mpField(body, b, "language", if (prefs().getBoolean("hi", true)) "hi" else "en")
        mpField(body, b, "temperature", "0")
        mpField(body, b, "prompt", "Jarvis, Jarvis. Hinglish baatcheet.")
        body.write(("--" + b + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"a.wav\"\r\nContent-Type: audio/wav\r\n\r\n").toByteArray())
        body.write(wavOf(pcm))
        body.write(("\r\n--" + b + "--\r\n").toByteArray())
        val c = URL("https://api.groq.com/openai/v1/audio/transcriptions").openConnection() as HttpURLConnection
        c.requestMethod = "POST"
        c.connectTimeout = 15000
        c.readTimeout = 30000
        c.doOutput = true
        c.setRequestProperty("Authorization", "Bearer " + key)
        c.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + b)
        c.setRequestProperty("User-Agent", "Mozilla/5.0")
        c.outputStream.use { it.write(body.toByteArray()) }
        val code = c.responseCode
        if (code != 200) {
            Bus.log("Mic ka Groq error " + code + " (limit ya key check karo)")
            return ""
        }
        val txt = c.inputStream.bufferedReader().readText()
        return JSONObject(txt).optString("text", "")
    }

    // ---------- Commands ----------

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

    private fun openApp(n: String): Boolean {
        if (n.length < 3) return false
        try {
            val pm = packageManager
            val li = Intent(Intent.ACTION_MAIN)
            li.addCategory(Intent.CATEGORY_LAUNCHER)
            val list = pm.queryIntentActivities(li, 0)
            for (r in list) {
                val label = r.loadLabel(pm).to
