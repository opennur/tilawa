package com.tahsinultimate.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
            Text("${a.score.total} / 100", style = MaterialTheme.typography.headlineLarge)
            Text(a.note, style = MaterialTheme.typography.bodySmall)
            if (a.usedHeuristicAlignment)
                Text("Alignment kata proporsional (bukan forced-alignment fonem).",
                    style = MaterialTheme.typography.bodySmall)
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MushafPageBg)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    a.score.words.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()) {
                            row.forEach { w ->
                                val bg = when (w.color) {
                                    "hijau" -> Color(0xFFC8E6C9)
                                    "kuning" -> Color(0xFFFFF9C4)
                                    else -> Color(0xFFFFCDD2)
                                }
                                Text(w.word, Modifier.background(bg, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                    style = mushafStyle(22))
                            }
                        }
                    }
                }
            }
        }
        item {
            var showDetail by remember { mutableStateOf(false) }
            TextButton(onClick = { showDetail = !showDetail }) {
                Text(if (showDetail) "Sembunyikan detail pengukuran" else "Lihat detail pengukuran per kata")
            }
            if (showDetail) {
                Card {
                    Column(Modifier.padding(10.dp)) {
                        a.debugLines.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }
        items(a.errors.size) { idx ->
            val e = a.errors[idx]
            ElevatedCard {
                Column(Modifier.padding(12.dp)) {
                    Text("${e.rule} • ", style = MaterialTheme.typography.titleSmall)
                    Text(e.word, style = mushafStyle(20))
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MushafScreen(surahIdx: Int, nav: NavController, vm: AppViewModel = viewModel()) {
    val idx = surahIdx.coerceIn(0, vm.surahs.size - 1)
    val surah = vm.surahs[idx]
    var picked by remember { mutableStateOf<Triple<String, TajwidRule, String>?>(null) }
    LazyColumn(Modifier.fillMaxSize().background(MushafPageBg).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
        item {
            // Kepala surah ala mushaf + basmalah (kecuali Al-Fatihah yang basmalahnya ayat 1)
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1))) {
                Column(Modifier.fillMaxWidth().padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("سُورَةُ ${surah.name}", style = mushafStyle(24))
                    if (surah.id != 1) Text("بِسْمِ ٱللَّهِ ٱلرَّحْمَـٰنِ ٱلرَّحِيمِ", style = mushafStyle(22))
                }
            }
        }
        items(surah.ayahs.size) { ai ->
            val words = surah.ayahs[ai].split(" ")
            Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(12.dp)) {
                    FlowRow(modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalArrangement = Arrangement.Center) {
                        words.forEachIndexed { k, w ->
                            val next = words.getOrNull(k + 1)?.firstOrNull()
                            val rule = QuranRepo.ruleForWord(w, next)
                            Text(w,
                                Modifier.background(ruleColor(rule), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                                    .clickable { picked = Triple(w, rule, QuranRepo.explanation(rule, w)) },
                                color = if (rule == TajwidRule.NONE) MushafInk else Color.White,
                                style = mushafStyle(24))
                            Text(" ", style = mushafStyle(24))
                        }
                        // Tanda akhir ayat ۝ + nomor
                        Text("۝", style = mushafStyle(24).copy(color = MushafGold))
                        Text(toArabicDigits(ai + 1), style = mushafStyle(20).copy(color = MushafGold))
                    }
                    Button(onClick = { vm.surahIdx = idx; vm.ayahIdx = ai; vm.analysis = null; vm.lastWav = null; nav.navigate("record") },
                        modifier = Modifier.padding(top = 6.dp)) { Text("Latihan ayat ini") }
                }
            }
        }
    }
    picked?.let { (w, r, exp) ->
        AlertDialog(
            onDismissRequest = { picked = null },
            title = { Text("${r.label}") },
            text = { Column { Text(w, style = mushafStyle(22)); Text(exp) } },
            confirmButton = { TextButton(onClick = { picked = null }) { Text("Tutup") } },
        )
    }
}

@Composable
fun AboutScreen(nav: NavController, vm: AppViewModel = viewModel()) {
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text("Tentang & Lisensi", style = MaterialTheme.typography.headlineSmall)
            Text("Model Tilawa NPL-1.2: non-komersial, share-alike. Aplikasi gratis tanpa iklan/IAP pada fitur tajwid.",
                style = MaterialTheme.typography.bodyMedium)
            Text("Font Amiri Quran (SIL Open Font License 1.1) via Google Fonts untuk tampilan mushaf.",
                style = MaterialTheme.typography.bodySmall)
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
