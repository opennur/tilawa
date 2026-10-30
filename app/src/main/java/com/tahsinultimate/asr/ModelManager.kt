package com.tahsinultimate.asr

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class ModelFile(val name: String, val url: String, val minBytes: Long)

object TilawaFiles {
    val ONNX = ModelFile(
        "zipformer_a0w_ep1_a05.int8.onnx",
        "https://github.com/yazinsai/tilawa/releases/download/zipformer-a0w-ep1-a0.5/zipformer_a0w_ep1_a05.int8.onnx",
        50_000_000,
    )
    val TOKENS = ModelFile(
        "tokens.txt",
        // Mirror di repo sendiri: upstream HF (Quran-Lab/zipformer_p-arabic-v3) gated
        // sehingga unduhan anonim selalu HTTP 401. Isi = tabel Tilawa @tilawa/core
        // 0.4.0 (251 token, blank 250), NPL-1.2 — lihat model-assets/ATTRIBUTION.md.
        "https://raw.githubusercontent.com/opennur/tilawa/main/model-assets/tokens.txt",
        1_000,
    )
    val CORPUS = ModelFile(
        "zipformer_quran.json",
        "https://github.com/yazinsai/tilawa/releases/download/v0.3.0/zipformer_quran.json",
        1_000_000,
    )
    val ALL = listOf(ONNX, TOKENS, CORPUS)
}

sealed interface DownloadState {
    data object Idle : DownloadState
    data class Running(val file: String, val done: Long, val total: Long) : DownloadState
    data class Done(val bytes: Long) : DownloadState
    data class Failed(val message: String) : DownloadState
}

/** Unduh model Tilawa saat first-launch ke filesDir/models (lihat LICENSE_ANALYSIS.md). */
class ModelManager(ctx: Context) {
    private val dir = File(ctx.filesDir, "models").apply { mkdirs() }
    private val _state = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val state: StateFlow<DownloadState> = _state

    fun fileStatus(): List<Triple<String, Boolean, Long>> =
        TilawaFiles.ALL.map { Triple(it.name, isValid(it), File(dir, it.name).let { f -> if (f.exists()) f.length() else 0 }) }

    fun isValid(f: ModelFile): Boolean {
        val file = File(dir, f.name)
        return file.exists() && file.length() >= f.minBytes
    }

    fun isModelReady(): Boolean = isValid(TilawaFiles.ONNX)

    /** Dekode fonem penuh butuh onnx + tabel token. */
    fun isDecodeReady(): Boolean = isValid(TilawaFiles.ONNX) && isValid(TilawaFiles.TOKENS)

    fun modelDir(): File = dir

    suspend fun downloadAll() = withContext(Dispatchers.IO) {
        val failures = mutableListOf<String>()
        for (f in TilawaFiles.ALL) {
            if (isValid(f)) continue
            var lastErr = ""
            for (attempt in 1..3) {
                try {
                    downloadOne(f) { done, total ->
                        _state.value = DownloadState.Running("${f.name} (upaya $attempt)", done, total)
                    }
                    lastErr = ""
                    break
                } catch (e: Exception) {
                    lastErr = "${f.name}: ${e.message}"
                    Thread.sleep(1000L * attempt)
                }
            }
            if (lastErr.isNotEmpty()) failures += lastErr
        }
        val totalDone = TilawaFiles.ALL.sumOf { File(dir, it.name).let { f -> if (f.exists()) f.length() else 0 } }
        _state.value = if (isValid(TilawaFiles.ONNX) && failures.isEmpty()) {
            DownloadState.Done(totalDone)
        } else if (isValid(TilawaFiles.ONNX)) {
            // onnx inti ada → analisis heuristik bisa jalan; laporkan file pendamping yang gagal
            DownloadState.Failed("Model dasar siap, tetapi: ${failures.joinToString("; ")}")
        } else {
            DownloadState.Failed(failures.joinToString("; ").ifEmpty { "unduh gagal" })
        }
    }

    private fun downloadOne(f: ModelFile, onProgress: (Long, Long) -> Unit) {
        val tmp = File(dir, f.name + ".part")
        val url = URL(f.url)
        (url.openConnection() as HttpURLConnection).run {
            connectTimeout = 30_000; readTimeout = 60_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "TahsinUltimate/1.0 (Android; +https://github.com/opennur/tilawa)")
            connect()
            if (responseCode !in 200..299) {
                val body = try { errorStream?.bufferedReader()?.readText()?.take(200) } catch (_: Exception) { null }
                throw IllegalStateException("HTTP $responseCode untuk ${f.name}${body?.let { " — $it" } ?: ""}")
            }
            val total = contentLengthLong.takeIf { it > 0 } ?: f.minBytes
            inputStream.use { inp ->
                FileOutputStream(tmp).use { out ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = inp.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n); done += n
                        onProgress(done, total)
                    }
                }
            }
        }
        if (tmp.length() < f.minBytes) throw IllegalStateException("${f.name} tidak lengkap: ${tmp.length()} bytes")
        if (!tmp.renameTo(File(dir, f.name))) throw IllegalStateException("gagal menyimpan ${f.name}")
    }

    fun deleteAll() {
        TilawaFiles.ALL.forEach { File(dir, it.name).delete() }
        _state.value = DownloadState.Idle
    }
}
