package com.tahsinultimate.qps

/** QPS dua level: phoneme + sifat (subset 10 sifat qdat_bench). */
data class PhonemeSifat(val phoneme: String, val sifat: Map<String, String>)

/** Deteksi 3 pasang makhraj tertukar (minimal Fase 4). */
object MakhrajChecker {
    // pasangan: (benar, sering tertukar)
    val PAIRS = listOf("ت" to "ط", "س" to "ص", "ح" to "ه")
    fun check(expected: String, observedSifat: Map<String, String>): String? {
        // heuristik sifat: itbaq=mutbaq menandai huruf tebal (ط ص), hams menandai ح vs ه
        for ((tipis, tebal) in PAIRS) {
            if (expected.contains(tipis) && observedSifat["itbaq"] == "mutbaq") return "Tertukar $tipis -> $tebal (itbaq: monfateh seharusnya mutbaq)"
            if (expected.contains(tebal) && observedSifat["itbaq"] == "monfateh") return "Tertukar $tebal -> $tipis (itbaq seharusnya mutbaq/tebal)"
        }
        if (expected.contains("ح") && observedSifat["hams_or_jahr"] == "hams") return null // benar
        return null
    }
    fun explain(pair: String): String = when (pair) {
        "ت/ط" -> "ت tipis (monfateh) vs ط tebal (mutbaq, lidah naik). Jika ط dibaca tipis = makhraj salah."
        "س/ص" -> "س tipis vs ص tebal + safeer. Bedakan dengan mengangkat pangkal lidah untuk ص."
        else -> "ح dari tenggorokan tengah (jahr) vs ه bisikan (hams). Jangan tertukar."
    }
}
