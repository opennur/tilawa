package com.tahsinultimate.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.tahsinultimate.data.QuranRepo
import com.tahsinultimate.data.TajwidRule

private fun ruleColor(rule: TajwidRule): Color = when (rule) {
    TajwidRule.MADD -> Color(0xFF1565C0)
    TajwidRule.GHUNNAH -> Color(0xFF2E7D32)
    TajwidRule.IKHFA -> Color(0xFFEF6C00)
    TajwidRule.QALQALAH -> Color(0xFF6A1B9A)
    TajwidRule.IDGHAM -> Color(0xFF00838F)
    TajwidRule.IQLAB -> Color(0xFFC62828)
    TajwidRule.NONE -> Color.Transparent
}

@Composable
fun ResultScreen(nav: NavController, vm: AppViewModel = viewModel()) {
    val a = vm.analysis
    if (a == null) {
        Column(Modifier.padding(16.dp)) {
            Text("Belum ada hasil. Rekam dan analisis dulu.")
            Button(onClick = { nav.popBackStack() }) { Text("Kembali") }
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text("Skor: ${a.score.total}", style = MaterialTheme.typography.headlineLarge)
            Text(a.note, style = MaterialTheme.typography.bodySmall)
            if (a.usedHeuristicAlignment)
                Text("Alignment kata proporsional (bukan forced-alignment fonem).",
                    style = MaterialTheme.typography.bodySmall)
        }
        item {
            FlowRowWords(a)
        }
        items(a.errors) { e ->
            ElevatedCard {
                Column(Modifier.padding(12.dp)) {
                    Text("${e.rule} • ${e.word}", style = MaterialTheme.typography.titleSmall)
                    Text(e.explanation, style = MaterialTheme.typography.bodyMedium)
                    Text("${e.startMs}–${e.endMs} ms", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { nav.popBackStack() }) { Text("Rekam lagi") }
                OutlinedButton(onClick = { nav.navigate("mushaf/${vm.surahIdx}") }) { Text("Mushaf") }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun FlowRowWords(a: com.tahsinultimate.tajweed.Analyzer.Result) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        a.score.words.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { w ->
                    val bg = when (w.color) {
                        "hijau" -> Color(0xFFC8E6C9)
                        "kuning" -> Color(0xFFFFF9C4)
                        else -> Color(0xFFFFCDD2)
                    }
                    Text(w.word, Modifier.background(bg).padding(horizontal = 8.dp, vertical = 4.dp),
                        style = LocalTextStyle.current.copy(textDirection = TextDirection.Rtl))
                }
            }
        }
    }
}

@Composable
fun MushafScreen(surahIdx: Int, nav: NavController, vm: AppViewModel = viewModel()) {
    val idx = surahIdx.coerceIn(0, vm.surahs.size - 1)
    val surah = vm.surahs[idx]
    var picked by remember { mutableStateOf<Triple<String, TajwidRule, String>?>(null) }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text("Mushaf — ${surah.name}", style = MaterialTheme.typography.headlineSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                vm.surahs.forEachIndexed { i, s ->
                    FilterChip(selected = i == idx, onClick = { nav.navigate("mushaf/$i") { popUpTo("mushaf/$idx") { inclusive = true } } },
                        label = { Text(s.name) })
                }
            }
            Text("Ketuk kata untuk melihat hukum tajwidnya.", style = MaterialTheme.typography.bodySmall)
        }
        items(surah.ayahs.size) { ai ->
            val words = surah.ayahs[ai].split(" ")
            Card {
                Column(Modifier.padding(10.dp)) {
                    Text("Ayat ${ai + 1}", style = MaterialTheme.typography.labelMedium)
                    RowWordsTajwid(words) { w, r -> picked = Triple(w, r, QuranRepo.explanation(r, w)) }
                    Button(onClick = { vm.surahIdx = idx; vm.ayahIdx = ai; vm.analysis = null; vm.lastWav = null; nav.navigate("record") },
                        modifier = Modifier.padding(top = 6.dp)) { Text("Latihan ayat ini") }
                }
            }
        }
    }
    picked?.let { (w, r, exp) ->
        AlertDialog(
            onDismissRequest = { picked = null },
            title = { Text("$w • ${r.label}") },
            text = { Text(exp) },
            confirmButton = { TextButton(onClick = { picked = null }) { Text("Tutup") } },
        )
    }
}

@Composable
private fun RowWordsTajwid(words: List<String>, onTap: (String, TajwidRule) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        words.chunked(5).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEachIndexed { k, w ->
                    val gi = words.indexOf(w)
                    val next = words.getOrNull(gi + 1)?.firstOrNull()
                    val rule = QuranRepo.ruleForWord(w, next)
                    Text(w,
                        Modifier.background(ruleColor(rule)).padding(horizontal = 6.dp, vertical = 2.dp)
                            .clickable { onTap(w, rule) },
                        color = if (rule == TajwidRule.NONE) Color.Unspecified else Color.White,
                        style = LocalTextStyle.current.copy(textDirection = TextDirection.Rtl))
                }
            }
        }
    }
}

@Composable
fun AboutScreen(nav: NavController, vm: AppViewModel = viewModel()) {
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text("Tentang & Lisensi", style = MaterialTheme.typography.headlineSmall)
            Text("Model Tilawa NPL-1.2: non-komersial, share-alike. Aplikasi gratis tanpa iklan/IAP pada fitur tajwid.",
                style = MaterialTheme.typography.bodyMedium)
            Text("Status model:", style = MaterialTheme.typography.titleSmall)
            vm.modelManager.fileStatus().forEach { (name, ok, size) ->
                Text("$name: ${if (ok) "✓" else "✗"} ${size / 1024} KB", style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.downloadModel() }) { Text("Unduh/perbaiki model") }
                OutlinedButton(onClick = { vm.deleteModel() }) { Text("Hapus model") }
            }
            if (vm.message.isNotEmpty()) Text(vm.message, style = MaterialTheme.typography.bodySmall)
            Text("Lisensi NPL-1.2:", style = MaterialTheme.typography.titleSmall)
            Card { Text(vm.nplText().take(3000), Modifier.padding(10.dp), style = MaterialTheme.typography.bodySmall) }
            Spacer(Modifier.height(8.dp))
            Button(onClick = { nav.popBackStack() }) { Text("Kembali") }
            Spacer(Modifier.height(16.dp))
        }
    }
}
