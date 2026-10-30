package com.tahsinultimate.tajweed

/** Satu kesalahan tajwid yang bisa dijelaskan ke pengguna. */
data class TajweedError(
    val rule: String, // MADD, GHUNNAH, IKHFA, QALQALAH, IDGHAM, IQLAB
    val word: String,
    val startMs: Long,
    val endMs: Long,
    val explanation: String,
    val severity: Float // 0..1
)

/** Kontrak classifier biner per aturan (hybrid TajweedAI). */
interface RuleClassifier {
    val ruleName: String
    /** features: durasi segmen, energi, zero-crossing, nasal-energy ratio, dll. */
    fun score(features: FloatArray): Float // probabilitas benar (1=benar)
    fun explain(word: String, features: FloatArray, threshold: Float): String
}

private fun dur(features: FloatArray) = features.getOrElse(0) { 0f }

/** MADD: benar jika durasi vokal panjang >= ambang harakat. Fitur[0]=durasi detik. */
class MaddClassifier : RuleClassifier {
    override val ruleName = "MADD"
    override fun score(f: FloatArray): Float {
        val d = dur(f); // 1 harakat ~0.18-0.25 dtk; thabi'i=2 harakat ~0.4 dtk
        return ((d - 0.25f) / 0.45f).coerceIn(0f, 1f)
    }
    override fun explain(word: String, f: FloatArray, t: Float) =
        "Mad pada kata '$word' terdeteksi ${"%.2f".format(dur(f))} dtk (skor ${"%.2f".format(score(f))}, ambang $t). " +
        "Mad Thabi'i seharusnya ~2 harakat (~0.4 dtk); Mad Munfasil/'Aarid 4-6 harakat. Jika < ambang, panjangkan vokal."
}

/** GHUNNAH: benar jika energi nasal (rasio low 200-500Hz) tinggi + durasi >=2 harakat. Fitur[0]=dur, [1]=nasalRatio. */
class GhunnahClassifier : RuleClassifier {
    override val ruleName = "GHUNNAH"
    override fun score(f: FloatArray): Float {
        val d = f.getOrElse(0) { 0f }; val nasal = f.getOrElse(1) { 0f }
        return ((0.6f * nasal + 0.4f * ((d - 0.15f) / 0.35f).coerceIn(0f, 1f))).coerceIn(0f, 1f)
    }
    override fun explain(word: String, f: FloatArray, t: Float) =
        "Ghunnah pada '$word': dengung hidung seharusnya 2 harakat (~0.3-0.5 dtk). Rasio nasal=${"%.2f".format(f.getOrElse(1){0f})}, durasi=${"%.2f".format(f[0])} dtk. Jika rendah, tahan nun/mim dengan dengung jelas."
}

/** IKHFA: benar jika spektral antara nun jelas vs dengung (fitur[1]=ikhfaIndex 0=nun jelas,1=penuh). Paling sulit (baseline 0.453). */
class IkhfaClassifier : RuleClassifier {
    override val ruleName = "IKHFA"
    override fun score(f: FloatArray): Float = f.getOrElse(1) { 0f }.coerceIn(0f, 1f)
    override fun explain(word: String, f: FloatArray, t: Float) =
        "Ikhfa pada '$word': nun mati/tanwin dibaca samar antara izhar dan idgham dengan ghunnah. Indeks ikhfa=${"%.2f".format(f.getOrElse(1){0f})}. Nilai 0=nun jelas (salah), 2/2=penuh (benar untuk qdat_bench noon_mokhfah_len)."
}

/** QALQALAH: benar jika ada burst energi + transient pada huruf qalqalah (fitur[1]=burst). */
class QalqalahClassifier : RuleClassifier {
    override val ruleName = "QALQALAH"
    override fun score(f: FloatArray): Float = f.getOrElse(1) { 0f }.coerceIn(0f, 1f)
    override fun explain(word: String, f: FloatArray, t: Float) =
        "Qalqalah pada '$word' (huruf ق ط ب ج د sukun): harus ada pantulan/burst. Burst=${"%.2f".format(f.getOrElse(1){0f})}. Jika 0, huruf mati tanpa pantulan."
}

/** IDGHAM/IQLAB rule-based lokasi teks (partial, bukan klasifikasi akustik penuh). */
object RuleBasedLocator {
    private val IDGHAM_YARMALUN = setOf('ي','ر','م','ل','و','ن')
    fun findIdgham(words: List<String>): List<Pair<String,String>> {
        val out = mutableListOf<Pair<String,String>>()
        val joined = words.joinToString(" ")
        // pola nun-mati/tanwin + yarmalun (sederhana, untuk Al-Fatihah/Ikhlas/dll)
        for (i in words.indices) {
            val w = words[i]
            if (w.contains("نْ") || w.endsWith("ً") || w.endsWith("ٍ") || w.endsWith("ٌ")) {
                val next = words.getOrNull(i + 1)?.firstOrNull()
                if (next != null && IDGHAM_YARMALUN.contains(next)) out += w to "IDGHAM: nun mati/tanwin bertemu $next — leburkan dengan ghunnah (kecuali ل ر tanpa ghunnah)"
            }
        }
        return out
    }
    fun findIqlab(words: List<String>): List<Pair<String,String>> {
        val out = mutableListOf<Pair<String,String>>()
        for (i in words.indices) {
            val w = words[i]
            if (w.contains("نْ") || w.endsWith("ً") || w.endsWith("ٍ") || w.endsWith("ٌ")) {
                val next = words.getOrNull(i + 1)?.firstOrNull()
                if (next == 'ب') out += w to "IQLAB: nun mati/tanwin bertemu ب — ubah ke mim dengan ghunnah 2 harakat"
            }
        }
        return out
    }
}
