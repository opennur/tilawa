package com.tahsinultimate.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.tahsinultimate.asr.DownloadState
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(nav: NavController, vm: AppViewModel = viewModel()) {
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
            Text("Model Tilawa: " + when {
                vm.modelManager.isDecodeReady() -> "siap penuh ✓ (decode fonem aktif)"
                vm.modelManager.isModelReady() -> "siap dasar ✓ (tabel token menyusul)"
                else -> "belum diunduh"
            }, style = MaterialTheme.typography.bodySmall)
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
            if (!vm.modelManager.isDecodeReady()) {
                ElevatedCard {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Model belum lengkap (66 MB, sekali saja)", style = MaterialTheme.typography.titleSmall)
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
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.startRecording() else vm.message = "Izin mikrofon ditolak. Aktifkan di Pengaturan."
    }
    val surah = vm.surahs.getOrNull(vm.surahIdx)
    val ayah = surah?.ayahs?.getOrNull(vm.ayahIdx) ?: ""
    val st = vm.modelManager.state.collectAsState().value

    var elapsed by remember { mutableStateOf("00:00") }
    var level by remember { mutableStateOf(0f) }
    LaunchedEffect(vm.isRecording) {
        while (vm.isRecording) {
            val s = (System.currentTimeMillis() - vm.recStartMs) / 1000
            elapsed = "%02d:%02d".format(s / 60, s % 60)
            level = vm.level()
            delay(120)
        }
    }

    fun toggle() {
        if (vm.busy) return
        if (vm.isRecording) {
            vm.stopAndAnalyze()
            nav.navigate("result")
        } else {
            val granted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
            if (!granted) permLauncher.launch(Manifest.permission.RECORD_AUDIO)
            else vm.startRecording()
        }
    }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        item {
            Text("${surah?.name ?: ""} • Ayat ${vm.ayahIdx + 1}",
                style = MaterialTheme.typography.titleMedium)
            Card(colors = CardDefaults.cardColors(containerColor = MushafPageBg)) {
                Text(ayah, Modifier.padding(14.dp), style = mushafStyle(26))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = { if (vm.ayahIdx > 0) { vm.ayahIdx--; vm.analysis = null; vm.lastWav = null } },
                    enabled = vm.ayahIdx > 0) { Text("‹ Ayat sebelumnya") }
                OutlinedButton(onClick = {
                    if (surah != null && vm.ayahIdx < surah.ayahs.size - 1) { vm.ayahIdx++; vm.analysis = null; vm.lastWav = null }
                }, enabled = surah != null && vm.ayahIdx < surah.ayahs.size - 1) { Text("Ayat berikut ›") }
            }
            if (!vm.modelManager.isDecodeReady()) {
                AssistChip(onClick = { vm.downloadModel() }, label = {
                    Text(if (st is DownloadState.Running) "Mengunduh ${st.file}…" else "Unduh model (66 MB)")
                })
            }
        }
        item {
            // Tombol rekam toggle ala aplikasi tilawah
            val bg = if (vm.isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            IconButton(onClick = { toggle() }, modifier = Modifier.size(88.dp).clip(CircleShape)
                .background(if (vm.isRecording) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer)) {
                Icon(if (vm.isRecording) Icons.Filled.Stop else Icons.Filled.Mic, contentDescription = "Rekam",
                    tint = bg, modifier = Modifier.size(44.dp))
            }
            Spacer(Modifier.height(4.dp))
            Text(if (vm.isRecording) "Merekam $elapsed — ketuk untuk berhenti & analisis" else "Ketuk untuk mulai merekam",
                style = MaterialTheme.typography.bodyMedium)
            if (vm.isRecording) {
                LinearProgressIndicator(progress = { level.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth(0.7f))
            }
            if (vm.message.isNotEmpty()) Text(vm.message, style = MaterialTheme.typography.bodySmall)
            if (vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (st is DownloadState.Failed) Text(st.message, color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { nav.navigate("mushaf/${vm.surahIdx}") }) { Text("Mushaf") }
                OutlinedButton(onClick = { nav.popBackStack() }) { Text("Beranda") }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
