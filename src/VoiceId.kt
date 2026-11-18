package com.jarvis.app

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder

object VoiceId {
    private const val SR = 16000
    private const val RING_SEC = 3
    private const val RING_LEN = SR * RING_SEC
    private val ring = ShortArray(RING_LEN)
    private var head = 0
    @Volatile private var capturing = false
    @Volatile private var captured = false
    private var thread: Thread? = null

    fun start() {
        if (capturing) return
        capturing = true
        thread = Thread {
            try {
                val minBuf = AudioRecord.getMinBufferSize(SR, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                val bufSize = if (minBuf > 0) minBuf * 2 else SR
                val rec = AudioRecord(MediaRecorder.AudioSource.MIC, SR, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufSize)
                val buf = ShortArray(1600)
                rec.startRecording()
                while (capturing) {
                    val n = rec.read(buf, 0, buf.size)
                    if (n > 0) {
                        captured = true
                        synchronized(ring) {
                            for (i in 0 until n) {
                                ring[head] = buf[i]
                                head = (head + 1) % RING_LEN
                            }
                        }
                    }
                }
                rec.stop()
                rec.release()
            } catch (e: Exception) {
            }
        }
        thread!!.start()
    }

    fun stop() {
        capturing = false
    }

    private fun snapshot(seconds: Float): ShortArray {
        val want = (SR * seconds).toInt().coerceAtMost(RING_LEN)
        val out = ShortArray(want)
        synchronized(ring) {
            var idx = head - want
            for (i in 0 until want) {
                var p = idx + i
                p %= RING_LEN
                if (p < 0) p += RING_LEN
                out[i] = ring[p]
            }
        }
        return out
    }

    private fun features(samples: ShortArray): FloatArray {
        if (samples.isEmpty()) return FloatArray(9)
        var sumSq = 0.0
        for (s in samples) sumSq += s.toDouble() * s.toDouble()
        val rms = Math.sqrt(sumSq / samples.size).toFloat()
        val bands = FloatArray(8)
        val freqs = floatArrayOf(150f, 300f, 500f, 800f, 1200f, 1800f, 2500f, 3500f)
        val n = samples.size
        for (b in 0 until 8) {
            val w = 2.0 * Math.PI * freqs[b] / SR
            val coeff = 2.0 * Math.cos(w)
            var s1 = 0.0
            var s2 = 0.0
            for (i in 0 until n) {
                val s0 = samples[i].toDouble() + coeff * s1 - s2
                s2 = s1
                s1 = s0
            }
            val mag = Math.sqrt(s1 * s1 + s2 * s2 - coeff * s1 * s2)
            bands[b] = mag.toFloat()
        }
        val v = FloatArray(9)
        v[0] = rms
        for (i in 0 until 8) v[i + 1] = bands[i]
        var norm = 0.0
        for (x in v) norm += x.toDouble() * x.toDouble()
        norm = Math.sqrt(norm)
        if (norm > 0.0001) {
            for (i in v.indices) v[i] = (v[i] / norm).toFloat()
        }
        return v
    }

    private fun sim(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size) return 0f
        var dot = 0f
        for (i in a.indices) dot += a[i] * b[i]
        return dot
    }

    fun enroll(ctx: Context) {
        val s = snapshot(2.0f)
        val v = features(s)
        ctx.getSharedPreferences("j", 0).edit().putString("voiceprint", v.joinToString(",")).apply()
    }

    fun hasOwner(ctx: Context): Boolean {
        return ctx.getSharedPreferences("j", 0).getString("voiceprint", null) != null
    }

    fun isOwner(ctx: Context): Boolean {
        if (!captured) return true
        val stored = ctx.getSharedPreferences("j", 0).getString("voiceprint", null) ?: return true
        val parts = stored.split(",").map { it.toFloatOrNull() ?: 0f }
        if (parts.size != 9) return true
        val saved = parts.toFloatArray()
        val now = features(snapshot(1.5f))
        return sim(saved, now) >= 0.80f
    }
}
