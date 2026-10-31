package com.tahsinultimate.audio

import java.io.File
import kotlin.math.abs
import kotlin.math.sqrt

/** Fitur akustik murni Kotlin (tanpa native): cermin logika ml/evaluation/evaluate_numpy.py. */
object AudioFeatures {
    data class Seg(
        val startMs: Long, val endMs: Long,
        val durS: Float, val rms: Float, val zcr: Float,
        val lowRatio: Float, val tailBurst: Float, val voicedS: Float,
    )

    /** Baca WAV PCM16 mono 16kHz (header 44 byte). */
    fun readWavMono16(f: File): FloatArray {
        val bytes = f.readBytes()
        require(bytes.size > 44) { "audio kosong: ${bytes.size} bytes" }
        val n = (bytes.size - 44) / 2
        val out = FloatArray(n)
        for (i in 0 until n) {
            val lo = bytes[44 + i * 2].toInt() and 0xFF
            val hi = bytes[44 + i * 2 + 1].toInt()
            out[i] = ((hi shl 8) or lo).toShort() / 32768f
        }
        return out
    }

    private fun frameRms(s: FloatArray, sr: Int, winMs: Int = 16): FloatArray {
        val w = (sr * winMs / 1000).coerceAtLeast(64)
        val env = FloatArray(s.size)
        var acc = 0.0
        for (i in s.indices) {
            acc += s[i] * s[i]
            if (i >= w) acc -= s[i - w] * s[i - w]
            env[i] = sqrt((acc / w).coerceAtLeast(1e-12)).toFloat()
        }
        return env
    }

    /** Rasio energi low-pass orde-1 (~500 Hz) terhadap energi total — proxy nasalitas. */
    private fun lowRatio(s: FloatArray): Float {
        val alpha = 0.18f // ~500 Hz @16kHz
        var low = 0f; var eLow = 0.0; var eTot = 0.0
        for (v in s) {
            low += alpha * (v - low)
            eLow += low * low; eTot += v * v
        }
        return if (eTot < 1e-12) 0f else (eLow / eTot).toFloat().coerceIn(0f, 1f)
    }

    fun analyze(s: FloatArray, sr: Int = 16000, startMs: Long = 0): Seg {
        if (s.isEmpty()) return Seg(startMs, startMs, 0f, 0f, 0f, 0f, 0f, 0f)
        val durS = s.size.toFloat() / sr
        var e = 0.0; var zc = 0
        for (i in s.indices) {
            e += s[i] * s[i]
            if (i > 0 && (s[i - 1] < 0) != (s[i] < 0)) zc++
        }
        val rms = sqrt(e / s.size).toFloat()
        val env = frameRms(s, sr)
        val mean = env.average().toFloat()
        val tailMean = if (env.size > 10) env.takeLast((env.size * 0.15).toInt().coerceAtLeast(1)).average().toFloat() else mean
        val tail = if (mean < 1e-9) 0f else (tailMean / mean).coerceIn(0f, 3f)
        val voicedFrames = env.count { it > mean }.toFloat() / sr * (sr * 0.016f)
        return Seg(startMs, startMs + (durS * 1000).toLong(), durS, rms,
            zc.toFloat() / s.size, lowRatio(s), tail, voicedFrames)
    }

    /** true jika tak ada ucapan: puncak terlalu kecil ATAU amplop datar (derau saja). */
    fun isSilent(s: FloatArray, sr: Int = 16000): Boolean {
        if (s.isEmpty()) return true
        val env = frameRms(s, sr, 32)
        val peak = env.maxOrNull() ?: 0f
        if (peak < 0.008f) return true // di bawah -42 dBFS: bukan suara manusia
        val sorted = env.sorted()
        val med = if (sorted.size % 2 == 1) sorted[sorted.size / 2]
            else (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2f
        // Ucapan punya semburan (puncak >> median); derau mic datar.
        return peak / (med + 1e-9f) < 2.0f
    }
    /** Bagi sampel ke segmen kata proporsional panjang huruf (ayat tunggal → alignment kasar yang jujur). */
    fun splitProportional(s: FloatArray, sr: Int, weights: List<Float>): List<Seg> {
        val total = weights.sum().coerceAtLeast(1e-9f)
        var offset = 0; val out = mutableListOf<Seg>()
        for ((i, w) in weights.withIndex()) {
            val len = ((w / total) * s.size).toInt().coerceAtLeast(0)
            val end = if (i == weights.lastIndex) s.size else (offset + len).coerceAtMost(s.size)
            val seg = if (end > offset) s.sliceArray(offset until end) else floatArrayOf()
            out += analyze(seg, sr, startMs = (offset * 1000L / sr))
            offset = end
        }
        return out
    }

    /** Pangkas hening awal/akhir via ambang energi — untuk timing kata yang lebih jujur. */
    fun trimSilence(s: FloatArray, sr: Int, thresh: Float = 0.02f): FloatArray {
        val env = frameRms(s, sr, 32)
        val peak = (env.maxOrNull() ?: 0f).coerceAtLeast(1e-9f)
        var a = 0; var b = s.size
        while (a < b && env[a] < peak * thresh) a++
        while (b > a && env[b - 1] < peak * thresh) b--
        return if (b - a < sr / 5) s else s.sliceArray(a until b)
    }
}
