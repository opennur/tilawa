package com.tahsinultimate.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.tahsinultimate.asr.DownloadState

@Composable
fun HomeScreen(nav: NavController, vm: AppViewModel = viewModel()) {
    val ctx = LocalContext.current
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text("Tahsin Ultimate", style = MaterialTheme.typography.headlineMedium)
            Text("Pilih surat & ayat, rekam, dapatkan koreksi tajwid — 100% offline setelah unduh model.",
                style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { nav.navigate("mushaf/0") }) { Text("Mushaf") }
                OutlinedButton(onClick = { nav.navigate("about") }) { Text("Tentang") }
            }
            Spacer(Modifier.height(8.dp))
            Text("Model Tilawa: " + if (vm.modelManager.isModelReady()) "siap ✓" else "belum diunduh",
                style = MaterialTheme.typography.bodySmall)
        }
        items(vm.surahs.size) { si ->
            val s = vm.surahs[si]
            ElevatedCard(onClick = { vm.surahIdx = si; vm.ayahIdx = 0; nav.navigate("record") }) {
                Column(Modifier.padding(12.dp)) {
                    Text("${s.id}. ${s.name}", style = MaterialTheme.typography.titleMedium)
                    Text("${s.ayahs.size} ayat • ketuk untuk latihan", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            val st = vm.modelManager.state.collectAsState().value
            if (!vm.modelManager.isModelReady()) {
                ElevatedCard {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Model belum diunduh (66 MB, sekali saja)", style = MaterialTheme.typography.titleSmall)
                        if (st is DownloadState.Running) {
                            LinearProgressIndicator(
                                progress = { (st.done.toFloat() / st.total.coerceAtLeast(1)).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text("${st.file}: ${st.done / 1024 / 1024} / ${st.total / 1024 / 1024} MB",
                                style = MaterialTheme.typography.bodySmall)
                        }
                        Button(onClick = { vm.downloadModel() }, enabled = st !is DownloadState.Running) {
                            Text("Unduh model sekarang")
                        }
                        if (st is DownloadState.Failed) Text("Gagal: ${st.message}", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun RecordScreen(nav: NavController, vm: AppViewModel = viewModel()) {
    val ctx = LocalContext.current
    var needPerm by remember { mutableStateOf(false) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        needPerm = !granted
        if (granted) vm.startRecording()
    }
    val surah = vm.surahs.getOrNull(vm.surahIdx)
    val ayah = surah?.ayahs?.getOrNull(vm.ayahIdx) ?: ""
    val st = vm.modelManager.state.collectAsState().value

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("${surah?.name ?: ""} • Ayat ${vm.ayahIdx + 1}", style = MaterialTheme.typography.titleMedium)
            Card {
                Text(ayah, Modifier.padding(12.dp),
                    style = LocalTextStyle.current.copy(textDirection = TextDirection.Rtl))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(onClick = { if (vm.ayahIdx > 0) { vm.ayahIdx--; vm.analysis = null; vm.lastWav = null } },
                    enabled = vm.ayahIdx > 0) { Text("‹") }
                Button(onClick = {
                    if (surah != null && vm.ayahIdx < surah.ayahs.size - 1) { vm.ayahIdx++; vm.analysis = null; vm.lastWav = null }
                }, enabled = surah != null && vm.ayahIdx < surah.ayahs.size - 1) { Text("›") }
            }
            if (!vm.modelManager.isModelReady()) {
                AssistChip(onClick = { vm.downloadModel() }, label = {
                    Text(if (st is DownloadState.Running) "Mengunduh ${st.file}…" else "Unduh model (66 MB)")
                })
                if (st is DownloadState.Running) LinearProgressIndicator(
                    progress = { (st.done.toFloat() / st.total.coerceAtLeast(1)).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth())
                Text("Mode heuristik aktif — analisis akustik tetap jalan; dekode fonem penuh perlu model.",
                    style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    val granted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) ==
                        PackageManager.PERMISSION_GRANTED
                    if (!granted) permLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    else if (vm.isRecording) vm.stopRecording() else vm.startRecording()
                }) { Text(if (vm.isRecording) "Berhenti" else "Rekam") }
                Button(onClick = { vm.analyze() }, enabled = !vm.busy && vm.lastWav != null && !vm.isRecording) {
                    Text("Analisis")
                }
            }
            if (needPerm) Text("Izin mikrofon dibutuhkan untuk merekam.", color = MaterialTheme.colorScheme.error)
            if (vm.message.isNotEmpty()) Text(vm.message, style = MaterialTheme.typography.bodySmall)
            if (vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        item {
            vm.analysis?.let { a ->
                Button(onClick = { nav.navigate("result") }) { Text("Lihat hasil (skor ${a.score.total}) →") }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { nav.navigate("mushaf/${vm.surahIdx}") }) { Text("Mushaf") }
                OutlinedButton(onClick = { nav.popBackStack() }) { Text("Beranda") }
            }
        }
    }
}
