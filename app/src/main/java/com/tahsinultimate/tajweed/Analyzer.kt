package com.tahsinultimate.tajweed

import com.tahsinultimate.audio.AudioFeatures
import com.tahsinultimate.data.QuranRepo
import com.tahsinultimate.data.TajwidRule
import com.tahsinultimate.ui.FinalScore
import com.tahsinultimate.ui.Scoring

/**
 * Analisis rekaman ayat: VAD trim → segmen kata proporsional → fitur akustik →
 * classifier per aturan dengan **perbandingan relatif dalam ayat yang sama**
 * (kata bertanda vs kata lain) + gerbang hening. Ini mengontrol perbedaan
 * mikrofon/pembicara sehingga analisis benar-benar diskriminatif.
 *
 * Jujur: alignment proporsional (bukan forced-alignment fonem); dekode CTC
 * fonem penuh menyusul setelah model in-app stabil.
 */
object Analyzer {
    data class Result(
        val errors: List<TajweedError>,
        val score: FinalScore,
        val usedHeuristicAlignment: Boolean,
        val note: String,
        val debugLines: List<String> = emptyList(),
    )

    private const val TH = 0.5f
    private val madd = MaddClassifier()
    private val ghunnah = GhunnahClassifier()
    private val ikhfa = IkhfaClassifier()
    private val qalqalah = QalqalahClassifier()

    private fun median(xs: List<Float>): Float {
        if (xs.isEmpty()) return 0f
        val s = xs.sorted()
        return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2f
    }

    private fun maddChars(w: String): Int = w.count { it == 'ا' || it == 'و' || it == 'ي' || it == 'آ' || it == 'ى' }

    fun analyze(words: List<String>, samples: FloatArray, sr: Int = 16000): Result {
        // Rekaman tidak valid → skor NOL eksplisit (bukan 100 dari daftar kosong).
        fun invalid(reason: String): Result {
            val errs = words.map { w ->
                TajweedError("REKAMAN", w, 0, 0, "$reason Kata '$w' tidak bisa dinilai.", 1f)
            }
            val byWord = words.associateWith { listOf("$reason Tidak ada penilaian untuk kata ini.") }
            return Result(errs, Scoring.score(words, byWord), true, reason)
        }
        if (samples.size < sr / 5) {
            return invalid("Audio terlalu pendek (<0,2 dtk). Rekam minimal 1–2 detik dengan suara jelas.")
        }
        if (AudioFeatures.isSilent(samples, sr)) {
            return invalid("Tidak ada suara terdeteksi — hanya hening/derau. Periksa mikrofon, dekatkan HP, dan baca ayat dengan suara jelas.")
        }
        val trimmed = AudioFeatures.trimSilence(samples, sr)
        if (trimmed.size < sr / 2 || AudioFeatures.isSilent(trimmed, sr)) {
            return invalid("Setelah dipangkas, tersisa ${"%.1f".format(trimmed.size.toFloat() / sr)} dtk suara valid. Baca seluruh ayat dengan suara jelas.")
        }
        val weights = words.map { it.length.toFloat().coerceAtLeast(1f) }
        val segs = AudioFeatures.splitProportional(trimmed, sr, weights)
        val peak = segs.maxOfOrNull { it.rms } ?: 0f
        if (peak < 1e-4f) {
            return Result(emptyList(), Scoring.score(words, emptyMap()), true,
                "Rekaman senyap (tidak ada sinyal). Periksa mikrofon dan ulangi.")
        }
        // Gerbang hening: segmen jauh di bawah puncak = kata tak terdengar.
        val audible = segs.map { it.rms > peak * 0.08f && it.durS > 0.05f }

        val errors = mutableListOf<TajweedError>()
        val byWord = mutableMapOf<String, MutableList<String>>()
        val debug = mutableListOf<String>()

        fun add(word: String, e: TajweedError) {
            errors += e
            byWord.getOrPut(word) { mutableListOf() } += "${e.rule}: ${e.explanation}"
        }

        // Kata yang tak terdengar = kesalahan nyata (dilewati / mic mati).
        for (i in words.indices) {
            if (!audible.getOrElse(i) { false }) {
                val seg = segs.getOrNull(i)
                add(words[i], TajweedError("TERLEWAT", words[i], seg?.startMs ?: 0, seg?.endMs ?: 0,
                    "Kata '${words[i]}' tidak terdengar pada segmen waktunya — mungkin dilewati atau suara terlalu pelan.",
                    1f))
            }
        }

        val audIdx = words.indices.filter { audible.getOrElse(it) { false } }
        if (audIdx.size < 2) {
            val score = Scoring.score(words, byWord)
            return Result(errors, score, true,
                "Hanya ${audIdx.size} kata terdengar jelas. Dekatkan mic dan baca seluruh ayat.", debug)
        }

        // Acuan relatif dalam ayat yang sama.
        val voicedPerChar = audIdx.map { segs[it].voicedS / words[it].length.toFloat().coerceAtLeast(1f) }
        val medVpc = median(voicedPerChar)
        val durPerChar = audIdx.map { segs[it].durS / words[it].length.toFloat().coerceAtLeast(1f) }
        val medDpc = median(durPerChar)
        val lowRatios = audIdx.map { segs[it].lowRatio }
        val medLow = median(lowRatios)
        val tails = audIdx.map { segs[it].tailBurst }
        val medTail = median(tails)

        for (i in audIdx) {
            val w = words[i]
            val seg = segs[i]
            val nextFirst = words.getOrNull(i + 1)?.firstOrNull()
            debug += "$w: ${"%.2f".format(seg.durS)} dtk, vokal ${"%.2f".format(seg.voicedS)} dtk, nasal ${"%.2f".format(seg.lowRatio)}, burst ${"%.2f".format(seg.tailBurst)}"
            when (QuranRepo.ruleForWord(w, nextFirst)) {
                TajwidRule.MADD -> {
                    val vpc = seg.voicedS / w.length.toFloat().coerceAtLeast(1f)
                    val refVpc = median(audIdx.filter { maddChars(words[it]) == 0 }.map { segs[it].voicedS / words[it].length.toFloat() }.ifEmpty { listOf(medVpc) })
                    val f = floatArrayOf(seg.voicedS)
                    val relFail = vpc < 0.7f * refVpc && maddChars(w) > 0
                    val absFail = madd.score(f) < TH * 0.6f
                    if (relFail || absFail) add(w, TajweedError("MADD", w, seg.startMs, seg.endMs,
                        "Mad pada '$w' terlalu pendek: vokal ${"%.2f".format(seg.voicedS)} dtk " +
                            "(rata-rata kata lain ${"%.2f".format(refVpc * w.length)} dtk). " +
                            "Mad Thabi'i ≈2 harakat (±0,4 dtk); Munfasil/'Arid 4–6 harakat — panjangkan vokal.", 0.7f))
                }
                TajwidRule.GHUNNAH -> {
                    val f = floatArrayOf(seg.durS, seg.lowRatio)
                    val refLow = median(audIdx.filter { QuranRepo.ruleForWord(words[it], null) != TajwidRule.GHUNNAH }.map { segs[it].lowRatio }.ifEmpty { listOf(medLow) })
                    if (seg.lowRatio <= refLow || ghunnah.score(f) < TH * 0.6f) add(w, TajweedError("GHUNNAH", w, seg.startMs, seg.endMs,
                        "Ghunnah pada '$w' kurang dengung: nasal ${"%.2f".format(seg.lowRatio)} " +
                            "(rata-rata kata lain ${"%.2f".format(refLow)}). Tahan nun/mim 2 harakat dengan dengung hidung jelas.", 0.7f))
                }
                TajwidRule.IKHFA -> {
                    val idx = (0.55f * seg.lowRatio + 0.45f * (seg.durS / 0.6f).coerceIn(0f, 1f))
                    val f = floatArrayOf(seg.durS, idx)
                    val refLow = median(audIdx.filter { QuranRepo.ruleForWord(words[it], null) != TajwidRule.IKHFA }.map { segs[it].lowRatio }.ifEmpty { listOf(medLow) })
                    if ((seg.lowRatio <= refLow && idx < 0.75f) || ikhfa.score(f) < TH * 0.6f) add(w, TajweedError("IKHFA", w, seg.startMs, seg.endMs,
                        ikhfa.explain(w, f, TH) + " Pada rekaman ini samar ${"%.2f".format(idx)} — dekatkan ke dengung, bukan nun jelas.", 1f - idx))
                }
                TajwidRule.QALQALAH -> {
                    val burst = (seg.tailBurst / 1.5f).coerceIn(0f, 1f)
                    val f = floatArrayOf(seg.durS, burst)
                    val refTail = median(audIdx.filter { QuranRepo.ruleForWord(words[it], null) != TajwidRule.QALQALAH }.map { segs[it].tailBurst }.ifEmpty { listOf(medTail) })
                    if (seg.tailBurst < refTail * 0.9f || qalqalah.score(f) < TH * 0.6f) add(w, TajweedError("QALQALAH", w, seg.startMs, seg.endMs,
                        "Qalqalah pada '$w' kurang memantul: burst ${"%.2f".format(seg.tailBurst)} " +
                            "(rata-rata kata lain ${"%.2f".format(refTail)}). Pantulkan huruf ق ط ب ج د yang sukun.", 0.7f))
                }
                else -> Unit
            }
        }
        // Idgham: nun+huruf berikutnya harus dilebur → segmen relatif panjang + nasal.
        for ((w, desc) in RuleBasedLocator.findIdgham(words)) {
            val idx = words.indexOf(w)
            if (idx < 0 || !audible.getOrElse(idx) { false }) continue
            val seg = segs[idx]
            val dpc = seg.durS / w.length.toFloat().coerceAtLeast(1f)
            if (dpc < 0.6f * medDpc || seg.lowRatio < medLow) {
                add(w, TajweedError("IDGHAM", w, seg.startMs, seg.endMs,
                    "$desc Segmen terdengar pendek/kurang dengung (${"%.2f".format(seg.durS)} dtk).", 0.6f))
            }
        }
        // Iqlab: harus ada dengung mim.
        for ((w, desc) in RuleBasedLocator.findIqlab(words)) {
            val idx = words.indexOf(w)
            if (idx < 0 || !audible.getOrElse(idx) { false }) continue
            val seg = segs[idx]
            if (seg.lowRatio < medLow) {
                add(w, TajweedError("IQLAB", w, seg.startMs, seg.endMs,
                    "$desc Dengung pada rekaman kurang (${"%.2f".format(seg.lowRatio)} vs rata-rata ${"%.2f".format(medLow)}).", 0.6f))
            }
        }
        val score = Scoring.score(words, byWord)
        return Result(errors, score, true,
            "Rekaman ${"%.1f".format(trimmed.size.toFloat() / sr)} dtk dianalisis per kata " +
                "(acuan relatif dalam ayat + ambang absolut). ${errors.size} masalah ditemukan.", debug)
    }
}
