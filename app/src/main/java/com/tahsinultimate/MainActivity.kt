package com.tahsinultimate

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tahsinultimate.tajweed.*
import com.tahsinultimate.ui.Scoring

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var log by remember { mutableStateOf("Model: belum diunduh (first-launch). Lihat LICENSE_ANALYSIS.") }
            var results by remember { mutableStateOf("") }
            Column(Modifier.padding(16.dp)) {
                Text("Tahsin Ultimate (MVP)")
                Spacer(Modifier.height(8.dp))
                Text(log)
                Spacer(Modifier.height(8.dp))
                Button(onClick = {
                    // Demo scoring offline tanpa model (aturan + penjelasan)
                    val words = listOf("قَالُوا", "لَا", "عِلْمَ", "لَنَا", "إِنَّكَ", "أَنْتَ", "عَلَّامُ", "الْغُيُوبِ")
                    val errs = mapOf(
                        "لَنَا" to listOf(MaddClassifier().explain("لَنَا", floatArrayOf(0.2f), 0.5f)),
                        "أَنْتَ" to listOf(IkhfaClassifier().explain("أَنْتَ", floatArrayOf(0.2f, 0.3f), 0.5f)),
                        "الْغُيُوبِ" to listOf(QalqalahClassifier().explain("الْغُيُوبِ", floatArrayOf(0.1f, 0.9f), 0.5f))
                    )
                    val s = Scoring.score(words, errs)
                    results = "Skor: ${s.total}\n" + s.errorList.joinToString("\n")
                }) { Text("Demo analisis (offline)") }
                Spacer(Modifier.height(8.dp))
                Text(results)
            }
        }
    }
}
