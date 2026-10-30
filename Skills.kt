package com.jarvis.app

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.speech.tts.TextToSpeech
import java.net.URL
import java.net.URLEncoder

class PermActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        requestPermissions(arrayOf(Manifest.permission.CALL_PHONE, Manifest.permission.READ_CONTACTS, Manifest.permission.READ_SMS), 5)
    }
    override fun onRequestPermissionsResult(rc: Int, p: Array<out String>, r: IntArray) {
        super.onRequestPermissionsResult(rc, p, r)
        finish()
    }
}

class JarvisNotifs : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null || sbn.packageName == packageName) return
        val e = sbn.notification.extras
        val t = e.getCharSequence("android.title")?.toString() ?: ""
        val x = e.getCharSequence("android.text")?.toString() ?: ""
        if (t.isEmpty() && x.isEmpty()) return
        val app = try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName, 0)).toString()
        } catch (ex: Exception) { sbn.packageName }
        Skills.notifs.add(0, app + ": " + t + ". " + x)
        while (Skills.notifs.size > 10) Skills.notifs.removeAt(Skills.notifs.size - 1)
    }
}

object Skills {
    val notifs = ArrayList<String>()
    private val stopw = setOf("youtube", "yutube", "instagram", "pe", "par", "per", "search", "karo", "kro", "kar", "ka", "ke", "ki", "ko", "open", "khol", "kholo", "dhundo", "play", "chalao", "lagao", "bajao", "mein", "me", "call", "phone", "dial", "यूट्यूब", "इंस्टाग्राम", "पर", "पे", "सर्च", "करो", "को", "कॉल", "फोन", "लगाओ", "चलाओ", "बजाओ", "ढूंढो", "खोजो")

    private fun has(s: String, vararg k: String): Boolean = k.any { s.contains(it) }
    private fun go(ctx: Context, i: Intent) { i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); ctx.startActivity(i) }

    fun handle(ctx: Context, w: String, tts: TextToSpeech?): String? {
        try {
            if (w.contains("yaad rakho") || w.contains("remember")) {
                val note = w.replace("yaad rakho", "").replace("remember", "").trim()
                if (note.isNotEmpty()) {
                    val prefs = ctx.getSharedPreferences("j_memory", 0)
                    val old = prefs.getString("notes", "") ?: ""
                    prefs.edit().putString("notes", old + "\n" + note).apply()
                    return "Theek hai dost, maine yaad rakh liya: $note"
                }
            }
            if (w.contains("kya yaad") || w.contains("what do you remember")) {
                val prefs = ctx.getSharedPreferences("j_memory", 0)
                val old = prefs.getString("notes", "") ?: ""
                if (old.isEmpty()) return "Abhi tak tumne mujhe kuch yaad nahi karaya."
                return "Mujhe yeh sab yaad hai: $old"
            }
            if (w.contains("photo lagao") || w.contains("photo change") || w.contains("background lagao")) {
                val query = w.replace("photo lagao", "").replace("photo change", "").replace("background lagao", "").trim()
                if (query.isNotEmpty()) {
                    Thread {
                        try {
                            val urlStr = "https://source.unsplash.com/1600x900/?" + URLEncoder.encode(query, "UTF-8")
                            val bmp = BitmapFactory.decodeStream(URL(urlStr).openStream())
                            if (bmp != null) {
                                Hud.updatePhoto(ctx, bmp)
                                tts?.speak("Ji dost, yeh rahi $query ki ekdum HD aur khoobsurat photo. Sach mein, yeh photo dekh kar dil khush ho gaya!", TextToSpeech.QUEUE_FLUSH, null, "")
                            } else tts?.speak("Dost, photo nahi mil payi.", TextToSpeech.QUEUE_FLUSH, null, "")
                        } catch (e: Exception) {
                            tts?.speak("Dost, internet mein dikkat hai, photo nahi la paya.", TextToSpeech.QUEUE_FLUSH, null, "")
                        }
                    }.start()
                    return "Photo dhoondh raha hoon..."
                }
            }
            if (w.contains("lock karo") || w.contains("phone lock")) {
                return if (ShizukuUtil.runCommand("input keyevent 26")) "Theek hai dost, phone lock kar diya." else "Dost, Shizuku chalu nahi hai."
            }
            if (w.contains("unlock karo") || w.contains("phone unlock")) {
                val pin = ctx.getSharedPreferences("j", 0).getString("pin", "") ?: ""
                if (pin.isEmpty()) return "Dost, settings mein apna PIN save karo pehle."
                Thread {
                    ShizukuUtil.runCommand("input keyevent 224")
                    Thread.sleep(500)
                    ShizukuUtil.runCommand("input swipe 500 1500 500 500")
                    Thread.sleep(500)
                    ShizukuUtil.runCommand("input text $pin")
                    Thread.sleep(200)
                    ShizukuUtil.runCommand("input keyevent 66")
                }.start()
                return "Ji dost, phone unlock kar raha hoon."
            }
            if (w.contains("phone band") || w.contains("phone off")) {
                return if (ShizukuUtil.runCommand("svc power shutdown")) "Theek hai dost, phone band kar raha hoon. Alvida!" else "Dost, Shizuku chalu nahi hai, power off nahi kar sakta."
            }
            if (has(w, "call", "कॉल")) return call(ctx, w)
            if (has(w, "time", "samay", "टाइम", "समय")) return "Abhi time hai " + java.text.SimpleDateFormat("hh mm a", java.util.Locale.getDefault()).format(java.util.Date())
            return calc(w)
        } catch (e: Exception) { return null }
    }

    private fun call(ctx: Context, w: String): String {
        val sb = StringBuilder()
        for (ch in w) { if (Character.isDigit(ch)) sb.append(Character.digit(ch, 10)) }
        if (sb.length >= 7) { go(ctx, Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + sb.toString()))); return "Call laga raha hoon" }
        return "Naam ya number bolo"
    }
    
    private fun calc(w: String): String? { return null }
}

object ShizukuUtil {
    fun runCommand(cmd: String): Boolean {
        return try {
            if (!rikka.shizuku.Shizuku.pingBinder()) return false
            rikka.shizuku.Shizuku.newProcess(arrayOf("sh", "-c", cmd), null, null).waitFor() == 0
        } catch (e: Exception) { false }
    }
}
