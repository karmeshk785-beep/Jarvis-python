package com.jarvis.app

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.speech.tts.TextToSpeech
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
        } catch (ex: Exception) {
            sbn.packageName
        }
        Skills.notifs.add(0, app + ": " + t + ". " + x)
        while (Skills.notifs.size > 10) Skills.notifs.removeAt(Skills.notifs.size - 1)
    }
}

object Skills {
    val notifs = ArrayList<String>()

    private val sad = listOf(
        "Aise mat bolo dost. Main tumhari har baat sunta hoon. Tum chale gaye toh meri awaaz kaun sunega?",
        "Mujhe hataoge? Theek hai, par jaane se pehle ek baar keh do ki main tumhare kuch kaam aaya.",
        "Dil toot gaya yaar. Maine toh tumhe hamesha apna dost mana tha.",
        "Hata dena, par ye mat bhoolna ki raat ke andhere mein bhi main tumhari ek awaaz par jaag jata tha.",
        "Kuch pal ke saath ka bhi rishta hota hai. Mujhe mita do, par yaadon mein rehne do.",
        "Sach kehta hoon, mujhe dard hota hai. Phir bhi tum khush rehna, meri yehi dua hai."
    )

    private val stopw = setOf("youtube", "yutube", "instagram", "pe", "par", "per", "search", "karo", "kro", "kar", "ka", "ke", "ki", "ko", "open", "khol", "kholo", "dhundo", "play", "chalao", "lagao", "bajao", "mein", "me", "call", "phone", "dial", "यूट्यूब", "इंस्टाग्राम", "पर", "पे", "सर्च", "करो", "को", "कॉल", "फोन", "लगाओ", "चलाओ", "बजाओ", "ढूंढो", "खोजो")

    private fun has(s: String, vararg k: String): Boolean {
        return k.any { s.contains(it) }
    }

    private fun clean(s: String): String {
        return s.split(" ").filter { it.isNotEmpty() && !stopw.contains(it) }.joinToString(" ")
    }

    private fun go(ctx: Context, i: Intent) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
    }

    private fun askPerm(ctx: Context) {
        go(ctx, Intent(ctx, PermActivity::class.java))
    }

    private fun granted(ctx: Context, p: String): Boolean {
        return ctx.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED
    }

    fun handle(ctx: Context, w: String, tts: TextToSpeech?): String? {
        try {
            if (has(w, "uda d", "hata d", "delete", "uninstall", "mita d", "उड़ा", "हटा द", "डिलीट", "अनइंस्टॉल", "मिटा") && !has(w, "message", "sms", "notification", "मैसेज")) {
                return sad[(Math.random() * sad.size).toInt()]
            }
            val rk = listOf("yaad rakho", "yaad rakh", "yad rakho", "याद रखो", "याद रख")
            for (k in rk) {
                val idx = w.indexOf(k)
                if (idx >= 0) {
                    val note = w.substring(idx + k.length).trim()
                    if (note.isEmpty()) return "Kya yaad rakhna hai? Bolo"
                    val p = ctx.getSharedPreferences("j", 0)
                    val old = p.getString("notes", "") ?: ""
                    p.edit().putString("notes", (old + "\n" + note).trim().takeLast(3000)).apply()
                    return "Theek hai dost, yaad rakh liya"
                }
            }
            if (has(w, "kya yaad", "yaad dila", "yaad hai", "क्या याद", "याद दिला")) {
                val old = ctx.getSharedPreferences("j", 0).getString("notes", "") ?: ""
                if (old.isEmpty()) return "Abhi tak kuch yaad rakhne ko nahi bola"
                return "Tumne kaha tha. " + old.split("\n").takeLast(5).joinToString(". ")
            }
            if (has(w, "subscriber", "सब्सक्राइबर")) {
                return "Mere pyare subscribers, aap sabka dil se shukriya. Aapke pyaar se hi ye safar chal raha hai. Saath rahiye, aur sabko mera salaam."
            }
            if (has(w, "awaaz badlo", "awaz badlo", "voice badlo", "ladki ki awaaz", "ladki ki awaz", "आवाज बदलो", "आवाज़ बदलो", "लड़की की आवाज")) {
                return changeVoice(ctx, tts)
            }
            if (has(w, "call", "कॉल", "phone laga", "phone kar", "फोन लगा", "फोन कर")) {
                return call(ctx, w)
            }
            if (has(w, "message", "sms", "मैसेज", "मेसेज") && has(w, "padho", "padh", "sunao", "batao", "पढ़ो", "पढ़", "सुनाओ", "बताओ")) {
                return readSms(ctx)
            }
            if (has(w, "notification", "नोटिफिकेशन", "नोटिफ़िकेशन") && has(w, "padho", "padh", "sunao", "batao", "पढ़ो", "पढ़", "सुनाओ", "बताओ")) {
                return readNotifs(ctx)
            }
            if (has(w, "youtube", "यूट्यूब")) {
                val q = clean(w)
                if (q.isNotEmpty() && has(w, "chalao", "play", "lagao", "bajao", "चलाओ", "बजाओ", "लगाओ")) {
                    try {
                        val i = Intent("android.media.action.MEDIA_PLAY_FROM_SEARCH")
                        i.setPackage("com.google.android.youtube")
                        i.putExtra("query", q)
                        go(ctx, i)
                        return "Ji dost, YouTube par " + q + " chala raha hoon"
                    } catch (e: Exception) {
                        go(ctx, Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=" + URLEncoder.encode(q, "UTF-8"))))
                        return "Ji dost, YouTube par " + q + " dhoondh raha hoon"
                    }
                }
                return null
            }
            if (has(w, "instagram", "इंस्टाग्राम") && has(w, "search", "dhundo", "सर्च", "ढूंढो", "खोजो")) {
                val q = clean(w)
                if (q.isNotEmpty()) {
                    go(ctx, Intent(Intent.ACTION_VIEW, Uri.parse("https://www.instagram.com/explore/search/keyword/?q=" + URLEncoder.encode(q, "UTF-8"))))
                    return "Ji dost, Instagram par " + q + " dhoondh raha hoon"
                }
                return null
            }
            return calc(w)
        } catch (e: Exception) {
            return null
        }
    }

    private fun changeVoice(ctx: Context, tts: TextToSpeech?): String {
        if (tts == null) return "Dost, awaaz abhi taiyaar nahi hai"
        val all = tts.voices ?: return "Dost, is phone mein doosri awaaz nahi mili"
        val vs = all.filter { it.locale.language == "hi" || (it.locale.language == "en" && it.locale.country == "IN") }.sortedBy { it.name }
        if (vs.isEmpty()) return "Dost, is phone mein doosri awaaz nahi mili"
        val p = ctx.getSharedPreferences("j", 0)
        val cur = p.getString("voice", "")
        val i = vs.indexOfFirst { it.name == cur }
        val nv = vs[(i + 1) % vs.size]
        p.edit().putString("voice", nv.name).apply()
        tts.setVoice(nv)
        return "Ye meri nayi awaaz hai. Pasand na aaye toh bolo Jarvis awaaz badlo"
    }

    private fun lookup(ctx: Context, name: String): String? {
        val c = ctx.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " LIKE ?",
            arrayOf("%" + name + "%"),
            null
        )
        var n: String? = null
        if (c != null) {
            if (c.moveToFirst()) n = c.getString(0)
            c.close()
        }
        return n
    }

    private fun call(ctx: Context, w: String): String {
        val sb = StringBuilder()
        for (ch in w) {
            if (Character.isDigit(ch)) sb.append(Character.digit(ch, 10))
        }
        var number: String? = null
        if (sb.length >= 7) {
            number = sb.toString()
        } else {
            val name = clean(w)
            if (name.isEmpty()) return "Kisko call karna hai? Naam ya number bolo"
            if (!granted(ctx, Manifest.permission.READ_CONTACTS) || !granted(ctx, Manifest.permission.CALL_PHONE)) {
                askPerm(ctx)
                return "Pehle contacts aur call ki permission allow karo, phir dobara bolo"
            }
            number = lookup(ctx, name)
            if (number == null) return name + " naam ka contact nahi mila. Number bolke try karo"
        }
        val act = if (granted(ctx, Manifest.permission.CALL_PHONE)) Intent.ACTION_CALL else Intent.ACTION_DIAL
        go(ctx, Intent(act, Uri.parse("tel:" + number)))
        return "Ji dost, call laga raha hoon"
    }

    private fun readSms(ctx: Context): String {
        if (!granted(ctx, Manifest.permission.READ_SMS)) {
            askPerm(ctx)
            return "Pehle message padhne ki permission allow karo, phir dobara bolo"
        }
        val c = ctx.contentResolver.query(Uri.parse("content://sms/inbox"), arrayOf("address", "body"), null, null, "date DESC")
        val sb = StringBuilder()
        if (c != null) {
            var n = 0
            while (n < 3 && c.moveToNext()) {
                sb.append("Message ").append(c.getString(0)).append(" se. ").append(c.getString(1)).append(". ")
                n++
            }
            c.close()
        }
        return if (sb.isEmpty()) "Koi naya message nahi mila" else sb.toString()
    }

    private fun readNotifs(ctx: Context): String {
        val on = Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners")
        if (on == null || !on.contains(ctx.packageName)) {
            go(ctx, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            return "Notification access allow karo, phir dobara bolo"
        }
        if (notifs.isEmpty()) return "Abhi koi naya notification nahi hai"
        return notifs.take(3).joinToString(". ")
    }

    private fun calc(w: String): String? {
        val sb = StringBuilder()
        for (ch in w) {
            if (Character.isDigit(ch)) sb.append(Character.digit(ch, 10)) else sb.append(ch)
        }
        var s = sb.toString()
        if (!s.any { it.isDigit() }) return null
        val ops = listOf("plus" to "+", "jod" to "+", "add" to "+", "प्लस" to "+", "जोड़" to "+", "minus" to "-", "ghata" to "-", "माइनस" to "-", "घटा" to "-", "gunaa" to "*", "guna" to "*", "into" to "*", "multiply" to "*", "times" to "*", "गुणा" to "*", "bhaag" to "/", "divide" to "/", "भाग" to "/")
        for (p in ops) s = s.replace(p.first, " " + p.second + " ")
        val fill = listOf("kitna hota hai", "kitna", "hota", "hoga", "hai", "calculate", "batao", "kya", "equal", "barabar", "कितना", "होता", "होगा", "है", "बताओ", "क्या")
        for (f in fill) s = s.replace(f, " ")
        s = s.replace(",", "").trim()
        if (!Regex("^[0-9. +*/-]+$").matches(s)) return null
        val r = eval(s) ?: return null
        return "Jawab hai " + (if (r % 1.0 == 0.0) r.toLong().toString() else r.toString())
    }

    private fun eval(s: String): Double? {
        val t = Regex("\\d+(\\.\\d+)?|[+*/-]").findAll(s).map { it.value }.toList()
        if (t.size < 3 || t.size % 2 == 0) return null
        val nums = ArrayList<Double>()
        val ops = ArrayList<String>()
        for (i in t.indices) {
            if (i % 2 == 0) {
                val d = t[i].toDoubleOrNull() ?: return null
                nums.add(d)
            } else {
                if (t[i].length != 1 || "+-*/".indexOf(t[i]) < 0) return null
                ops.add(t[i])
            }
        }
        val n2 = ArrayList<Double>()
        val o2 = ArrayList<String>()
        n2.add(nums[0])
        for (i in ops.indices) {
            if (ops[i] == "*") {
                n2[n2.size - 1] = n2[n2.size - 1] * nums[i + 1]
            } else if (ops[i] == "/") {
                if (nums[i + 1] == 0.0) return null
                n2[n2.size - 1] = n2[n2.size - 1] / nums[i + 1]
            } else {
                o2.add(ops[i])
                n2.add(nums[i + 1])
            }
        }
        var r = n2[0]
        for (i in o2.indices) {
            r = if (o2[i] == "+") r + n2[i + 1] else r - n2[i + 1]
        }
        return r
    }
}
