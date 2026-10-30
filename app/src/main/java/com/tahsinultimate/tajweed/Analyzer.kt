package com.tahsinultimate.tajweed

import com.tahsinultimate.audio.AudioFeatures
import com.tahsinultimate.data.QuranRepo
import com.tahsinultimate.data.TajwidRule
import com.tahsinultimate.ui.FinalScore
import com.tahsinultimate.ui.Scoring

/**
 * Analisis rekaman ayat: VAD trim → segmen kata proporsional → fitur akustik →
 * classifier per aturan. Jujur: alignment proporsional (bukan forced-alignment
 * fonem); dekode fonem Zipformer penuh menyusul setelah model in-app stabil.
 */
object Analyzer {
    data class Result(
        val errors: List<TajweedError>,
        val score: FinalScore,
        val usedHeuristicAlignment: Boolean,
        val note: String,
    )

    private const val TH = 0.5f
    private val madd = MaddClassifier()
    private val ghunnah = GhunnahClassifier()
    private val ikhfa = IkhfaClassifier()
    private val qalqalah = QalqalahClassifier()

    fun analyze(words: List<String>, samples: FloatArray, sr: Int = 16000): Result {
        if (samples.size < sr / 5) {
            return Result(emptyList(), Scoring.score(words, emptyMap()), true,
                "Audio terlalu pendek (<0,2 dtk). Rekam minimal 1–2 detik.")
        }
        val trimmed = AudioFeatures.trimSilence(samples, sr)
        val weights = words.map { it.length.toFloat().coerceAtLeast(1f) }
        val segs = AudioFeatures.splitProportional(trimmed, sr, weights)
        val errors = mutableListOf<TajweedError>()
        val byWord = mutableMapOf<String, MutableList<String>>()

        fun add(word: String, e: TajweedError) {
            errors += e
            byWord.getOrPut(word) { mutableListOf() } += "${e.rule}: ${e.explanation}"
        }

        for (i in words.indices) {
            val w = words[i]
            val seg = segs.getOrNull(i) ?: continue
            val nextFirst = words.getOrNull(i + 1)?.firstOrNull()
            when (QuranRepo.ruleForWord(w, nextFirst)) {
                TajwidRule.MADD -> {
                    // madd letters butuh durasi vokal; ekspektasi thabii 2 harakat
                    val f = floatArrayOf(seg.voicedS)
                    if (madd.score(f) < TH) add(w, TajweedError("MADD", w, seg.startMs, seg.endMs,
                        madd.explain(w, f, TH), 1f - madd.score(f)))
                }
                TajwidRule.GHUNNAH -> {
                    val f = floatArrayOf(seg.durS, seg.lowRatio)
                    if (ghunnah.score(f) < TH) add(w, TajweedError("GHUNNAH", w, seg.startMs, seg.endMs,
                        ghunnah.explain(w, f, TH), 1f - ghunnah.score(f)))
                }
                TajwidRule.IKHFA -> {
                    // indeks ikhfa dari campuran lowRatio + durasi (proxy kasar, didokumentasikan)
                    val idx = (0.55f * seg.lowRatio + 0.45f * (seg.durS / 0.6f).coerceIn(0f, 1f))
                    val f = floatArrayOf(seg.durS, idx)
                    if (ikhfa.score(f) < TH) add(w, TajweedError("IKHFA", w, seg.startMs, seg.endMs,
                        ikhfa.explain(w, f, TH) + " (proxy akustik kasar; ambang bunyi nun jelas vs samar).", 1f - idx))
                }
                TajwidRule.QALQALAH -> {
                    val f = floatArrayOf(seg.durS, (seg.tailBurst / 1.5f).coerceIn(0f, 1f))
                    if (qalqalah.score(f) < TH) add(w, TajweedError("QALQALAH", w, seg.startMs, seg.endMs,
                        qalqalah.explain(w, f, TH), 1f - qalqalah.score(f)))
                }
                else -> Unit
            }
        }
        // Idgham/Iqlab: lokasi dari teks (partial) + cek durasi segmen dilebur
        for ((w, desc) in RuleBasedLocator.findIdgham(words)) {
            val idx = words.indexOf(w)
            val seg = segs.getOrNull(idx)
            val okDur = seg != null && seg.durS > 0.30f
            val rule = "IDGHAM"
            val exp = "$desc (deteksi lokasi dari teks — partial; verifikasi akustik penuh menyusul)." +
                if (!okDur && seg != null) " Segmen terdengar pendek (${"%.2f".format(seg.durS)} dtk), kemungkinan tidak dilebur." else ""
            if (!okDur) add(w, TajweedError(rule, w, seg?.startMs ?: 0, seg?.endMs ?: 0, exp, 0.5f))
        }
        for ((w, desc) in RuleBasedLocator.findIqlab(words)) {
            val idx = words.indexOf(w)
            val seg = segs.getOrNull(idx)
            add(w, TajweedError("IQLAB", w, seg?.startMs ?: 0, seg?.endMs ?: 0,
                "$desc (deteksi lokasi dari teks — partial).", 0.5f))
        }
        val score = Scoring.score(words, byWord)
        return Result(errors, score, true,
            "Alignment kata proporsional + classifier akustik (bukan forced-alignment fonem). " +
                "Rekaman ${"%.1f".format(trimmed.size.toFloat() / sr)} dtk dianalisis per kata.")
    }
}
