package com.tahsinultimate.ui

import android.app.Application
import android.media.AudioFormat
import android.media.AudioRecord
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tahsinultimate.asr.DownloadState
import com.tahsinultimate.asr.ModelManager
import com.tahsinultimate.asr.TilawaAsrEngine
import com.tahsinultimate.audio.AudioFeatures
import com.tahsinultimate.audio.AudioRecorder
import com.tahsinultimate.data.QuranRepo
import com.tahsinultimate.data.Surah
import com.tahsinultimate.tajweed.Analyzer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class AppViewModel(app: Application) : AndroidViewModel(app) {
    val surahs: List<Surah> = QuranRepo.load(app)
    val modelManager = ModelManager(app)
    val asrEngine = TilawaAsrEngine(modelManager.modelDir())

    var surahIdx by mutableStateOf(0)
    var ayahIdx by mutableStateOf(0)
    var isRecording by mutableStateOf(false)
    var recStartMs by mutableStateOf(0L)
    var lastWav: File? by mutableStateOf(null)
    var analysis: Analyzer.Result? by mutableStateOf(null)
    var busy by mutableStateOf(false)
    var message by mutableStateOf("")

    private var recorder: AudioRecorder? = null

    val refWords: List<String>
        get() = surahs.getOrNull(surahIdx)?.ayahs?.getOrNull(ayahIdx)?.split(" ").orEmpty()

    fun startRecording() {
        val out = File(getApplication<Application>().cacheDir, "rec_${System.currentTimeMillis()}.wav")
        recorder = AudioRecorder(out)
        try {
            recorder?.start()
            recStartMs = System.currentTimeMillis()
            isRecording = true
            message = "Merekam… ketuk tombol untuk berhenti."
        } catch (e: SecurityException) {
            message = "Izin mikrofon ditolak. Aktifkan di Pengaturan."
        }
    }

    fun level(): Float = recorder?.level01 ?: 0f

    fun stopAndAnalyze() {
        stopRecording()
        analyze()
    }

    fun stopRecording() {
        recorder?.let {
            lastWav = it.stop()
            analysis = null
            message = "Rekaman tersimpan (${lastWav?.length()} bytes). Tekan Analisis."
        }
        recorder = null
        isRecording = false
    }

    fun downloadModel() {
        viewModelScope.launch {
            busy = true
            message = "Mengunduh model…"
            modelManager.downloadAll()
            val st = modelManager.state.value
            message = when (st) {
                is DownloadState.Done -> "Model siap (${st.bytes} bytes). Bisa rekam + analisis."
                is DownloadState.Failed -> "Unduh gagal: ${st.message}"
                else -> message
            }
            busy = false
        }
    }

    fun analyze() {
        val wav = lastWav ?: run { message = "Belum ada rekaman."; return }
        viewModelScope.launch {
            busy = true
            message = "Menganalisis…"
            try {
                val res = withContext(Dispatchers.Default) {
                    val samples = AudioFeatures.readWavMono16(wav)
                    val out = Analyzer.analyze(refWords, samples)
                    // buktikan model termuat via ORT bila sudah diunduh (non-blokir hasil)
                    val ortOk = try { asrEngine.tryCreateOrtSession() } catch (_: Exception) { false }
                    android.util.Log.i("TahsinASR", "analysis done errors=${out.errors.size} ort=$ortOk")
                    out
                }
                analysis = res
                message = if (res.errors.isEmpty()) "Bagus! Tidak ada kesalahan terdeteksi." else "${res.errors.size} masalah ditemukan."
            } catch (e: Exception) {
                message = "Analisis gagal: ${e.message}"
            }
            busy = false
        }
    }

    fun deleteModel() {
        modelManager.deleteAll()
        message = "Model dihapus. Unduh ulang untuk fitur penuh."
    }

    fun nplText(): String = try {
        getApplication<Application>().assets.open("NPL-1.2.txt").bufferedReader().use { it.readText() }
    } catch (_: Exception) { "NPL-1.2 tidak ditemukan." }

    @Suppress("unused")
    fun checkMic(): Boolean {
        val minBuf = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        return minBuf > 0
    }
}
