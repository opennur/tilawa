package com.tahsinultimate.asr

/** Hasil ASR + word alignment dari Tilawa Zipformer2-CTC. */
data class WordTiming(val word: String, val startMs: Long, val endMs: Long, val confidence: Float)
data class AsrResult(val textAr: String, val phonemes: String, val words: List<WordTiming>)

/**
 * Engine Tilawa ONNX int8 (251 token tajwid).
 * Model TIDAK dibundel — diunduh first-launch (lihat LICENSE_ANALYSIS.md).
 * File wajib: zipformer_a0w_ep1_a05.int8.onnx, tokens.txt (blank=250), zipformer_quran.json.
 * Front-end: Kaldi fbank 80-dim Povey window, T=61 hop=48. BUKAN mel biasa.
 */
class TilawaAsrEngine(private val modelDir: java.io.File) {
    fun isModelReady(): Boolean =
        java.io.File(modelDir, "zipformer_a0w_ep1_a05.int8.onnx").exists() &&
        java.io.File(modelDir, "tokens.txt").exists()

    fun modelFilesStatus(): String {
        val m = java.io.File(modelDir, "zipformer_a0w_ep1_a05.int8.onnx")
        val t = java.io.File(modelDir, "tokens.txt")
        return "onnx=${m.exists()}(${m.length()}B) tokens=${t.exists()}(${t.length()}B) dir=${modelDir.absolutePath}"
    }

    /** Stub inference: panggil ORT session streaming CTC di device nyata. Diuji via Logcat. */
    fun transcribe(wav16kMono: java.io.File): AsrResult {
        android.util.Log.i("TahsinASR", "transcribe ${wav16kMono.absolutePath} size=${wav16kMono.length()} ${modelFilesStatus()}")
        require(wav16kMono.exists() && wav16kMono.length() > 44) { "audio kosong/bising: ${wav16kMono.length()} bytes" }
        // TODO(device): ORT InferenceSession streaming chunk 16/24, greedy CTC decode dengan tokens.txt.
        // Fallback jujur: tanpa model, lempar agar tidak mengarang transkripsi.
        if (!isModelReady()) throw IllegalStateException("Model belum diunduh. ${modelFilesStatus()}")
        // Placeholder hasil struktur — diisi decoder nyata di device.
        return AsrResult(textAr = "", phonemes = "", words = emptyList())
    }
}
