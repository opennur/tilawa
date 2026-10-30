package com.tahsinultimate.ui

/** Skor akhir + pewarnaan kata (hijau/merah/kuning). Logika murni agar bisa unit-test tanpa emulator. */
data class WordScore(val word: String, val color: String, val errors: List<String>)
data class FinalScore(val total: Int, val words: List<WordScore>, val errorList: List<String>)

object Scoring {
    fun score(words: List<String>, errorsByWord: Map<String, List<String>>): FinalScore {
        val ws = words.map { w ->
            val e = errorsByWord[w].orEmpty()
            val color = when { e.isEmpty() -> "hijau"; e.size == 1 -> "kuning"; else -> "merah" }
            WordScore(w, color, e)
        }
        val total = if (words.isEmpty()) 0 else ((ws.count { it.color == "hijau" } * 100) / words.size)
        val list = errorsByWord.flatMap { (w, es) -> es.map { "$w: $it" } }
        return FinalScore(total, ws, list)
    }
}
