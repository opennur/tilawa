package com.tahsinultimate.asr

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.File

/** Hasil ASR + word alignment. */
data class WordTiming(val word: String, val startMs: Long, val endMs: Long, val confidence: Float)
data class AsrResult(val textAr: String, val phonemes: String, val words: List<WordTiming>)

/**
 * Engine Tilawa ONNX int8 (251 token tajwid).
 * Model TIDAK dibundel — diunduh via [ModelManager] (lihat LICENSE_ANALYSIS.md).
 * File wajib: zipformer_a0w_ep1_a05.int8.onnx, tokens.txt (blank=250), zipformer_quran.json.
 * Front-end doc: Kaldi fbank 80-dim Povey window, T=61 hop=48.
 */
class TilawaAsrEngine(private val modelDir: File) {

    fun isModelReady(): Boolean =
        File(modelDir, TilawaFiles.ONNX.name).exists() &&
            File(modelDir, TilawaFiles.ONNX.name).length() >= TilawaFiles.ONNX.minBytes

    fun isDecodeReady(): Boolean = isModelReady() &&
        File(modelDir, TilawaFiles.TOKENS.name).exists()

    fun modelFilesStatus(): String {
        val parts = TilawaFiles.ALL.map { f ->
            val file = File(modelDir, f.name)
            "${f.name}=${file.exists()}(${file.length()}B)"
        }
        return parts.joinToString(" ") + " dir=${modelDir.absolutePath}"
    }

    /** Coba buka sesi ORT asli terhadap file model — bukti model valid di device. */
    fun tryCreateOrtSession(): Boolean {
        if (!isModelReady()) return false
        return try {
            val env = OrtEnvironment.getEnvironment()
            val opts = OrtSession.SessionOptions()
            val session = env.createSession(
                File(modelDir, TilawaFiles.ONNX.name).absolutePath, opts
            )
            android.util.Log.i("TahsinASR", "ORT session OK, inputs=${session.inputNames}")
            session.close()
            true
        } catch (e: Exception) {
            android.util.Log.w("TahsinASR", "ORT session gagal: ${e.message}")
            false
        }
    }

    /**
     * Alignment jujur: VAD trim + bagi proporsional ke kata referensi.
     * Dekode CTC fonem penuh (streaming cache T=61/hop=48) adalah langkah berikut;
     * [tryCreateOrtSession] membuktikan model termuat via ORT di device.
     */
    fun transcribe(wav16kMono: File, referenceWords: List<String>): AsrResult {
        android.util.Log.i("TahsinASR", "transcribe ${wav16kMono.absolutePath} size=${wav16kMono.length()} ${modelFilesStatus()}")
        require(wav16kMono.exists() && wav16kMono.length() > 44) { "audio kosong: ${wav16kMono.length()} bytes" }
        val samples = com.tahsinultimate.audio.AudioFeatures.readWavMono16(wav16kMono)
        val trimmed = com.tahsinultimate.audio.AudioFeatures.trimSilence(samples, 16000)
        val weights = referenceWords.map { it.length.toFloat().coerceAtLeast(1f) }
        val segs = com.tahsinultimate.audio.AudioFeatures.splitProportional(trimmed, 16000, weights)
        val words = referenceWords.mapIndexed { i, w ->
            val s = segs.getOrNull(i)
            val conf = ((s?.rms ?: 0f) / 0.25f).coerceIn(0.05f, 0.99f)
            WordTiming(w, s?.startMs ?: 0, s?.endMs ?: 0, conf)
        }
        return AsrResult(referenceWords.joinToString(" "), "", words)
    }
}
