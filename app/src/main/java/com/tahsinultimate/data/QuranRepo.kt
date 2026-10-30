package com.tahsinultimate.data

import android.content.Context
import org.json.JSONObject

data class Surah(val id: Int, val name: String, val ayahs: List<String>)

/** Aturan tajwid per kata untuk pewarnaan mushaf + penjelasan. */
enum class TajwidRule(val label: String, val colorHex: Long) {
    NONE("—", 0x00000000),
    MADD("Mad", 0xFF1565C0),
    GHUNNAH("Ghunnah", 0xFF2E7D32),
    IKHFA("Ikhfa", 0xFFEF6C00),
    QALQALAH("Qalqalah", 0xFF6A1B9A),
    IDGHAM("Idgham", 0xFF00838F),
    IQLAB("Iqlab", 0xFFC62828),
}

object QuranRepo {
    private var cache: List<Surah>? = null

    fun load(ctx: Context): List<Surah> {
        cache?.let { return it }
        val json = ctx.assets.open("quran_refs.json").bufferedReader().use { it.readText() }
        val arr = JSONObject(json).getJSONArray("surahs")
        val out = mutableListOf<Surah>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val ayahs = mutableListOf<String>()
            val ja = o.getJSONArray("ayahs")
            for (j in 0 until ja.length()) ayahs += ja.getString(j)
            out += Surah(o.getInt("id"), o.getString("name"), ayahs)
        }
        cache = out
        return out
    }

    private val YARMALUN = setOf('ي', 'ر', 'م', 'ل', 'و', 'ن')
    private val IKHFA_SET = setOf('ت', 'ث', 'ج', 'د', 'ذ', 'ز', 'س', 'ش', 'ص', 'ض', 'ط', 'ظ', 'ف', 'ق', 'ك')
    private val QALQALAH_SET = setOf('ق', 'ط', 'ب', 'ج', 'د')

    fun hasNunSukunOrTanwin(w: String): Boolean =
        w.contains("نْ") || w.contains("مْ") || w.endsWith("ً") || w.endsWith("ٍ") || w.endsWith("ٌ")

    /** Aturan dominan satu kata (prioritas: iqlab > idgham > ikhfa > ghunnah > qalqalah > madd). */
    fun ruleForWord(word: String, nextFirst: Char?): TajwidRule {
        val nun = hasNunSukunOrTanwin(word)
        if (nun && nextFirst != null) {
            if (nextFirst == 'ب') return TajwidRule.IQLAB
            if (YARMALUN.contains(nextFirst)) return TajwidRule.IDGHAM
            if (IKHFA_SET.contains(nextFirst)) return TajwidRule.IKHFA
        }
        if (word.contains("نَّ") || word.contains("مَّ") || word.contains("نّ") || word.contains("مّ")) return TajwidRule.GHUNNAH
        val last = word.lastOrNull()
        if (last != null && QALQALAH_SET.contains(last)) return TajwidRule.QALQALAH
        for (i in word.indices) {
            if (QALQALAH_SET.contains(word[i]) && (word.getOrNull(i + 1) == 'ْ')) return TajwidRule.QALQALAH
        }
        if (word.contains("آ") || word.contains("ا") || word.contains("و") || word.contains("ي")) return TajwidRule.MADD
        return TajwidRule.NONE
    }

    fun explanation(rule: TajwidRule, word: String): String = when (rule) {
        TajwidRule.MADD -> "Mad pada '$word': vokal panjang wajib dipanjangkan (Thabi'i 2 harakat ≈0,4 dtk; Munfasil/'Arid 4–6 harakat). Aplikasi mengukur durasi vokal rekaman Anda."
        TajwidRule.GHUNNAH -> "Ghunnah pada '$word': nun/mim bertasydid ditahan dengan dengung hidung 2 harakat (≈0,3–0,5 dtk)."
        TajwidRule.IKHFA -> "Ikhfa pada '$word': nun mati/tanwin dibaca samar antara izhar dan idgham disertai ghunnah."
        TajwidRule.QALQALAH -> "Qalqalah pada '$word': huruf ق ط ب ج د yang sukun harus memantul (burst energi di akhir segmen)."
        TajwidRule.IDGHAM -> "Idgham pada '$word': nun mati/tanwin dilebur ke huruf berikutnya (dengan ghunnah, kecuali ل ر). Deteksi lokasi dari teks; verifikasi akustik penuh menyusul."
        TajwidRule.IQLAB -> "Iqlab pada '$word': nun mati/tanwin bertemu ب dibaca menjadi mim dengan ghunnah 2 harakat. Deteksi lokasi dari teks."
        TajwidRule.NONE -> "Tidak ada hukum tajwid khusus terdeteksi pada kata ini."
    }
}
