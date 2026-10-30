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
        "https://huggingface.co/Quran-Lab/zipformer_p-arabic-v3/resolve/main/tokens.txt",
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

    fun isModelReady(): Boolean = isValid(TilawaFiles.ONNX) && isValid(TilawaFiles.TOKENS)

    fun modelDir(): File = dir

    suspend fun downloadAll() = withContext(Dispatchers.IO) {
        try {
            var totalDone = 0L
            for (f in TilawaFiles.ALL) {
                if (isValid(f)) continue
                downloadOne(f) { done, total ->
                    _state.value = DownloadState.Running(f.name, done, total)
                }
                totalDone += File(dir, f.name).length()
            }
            _state.value = DownloadState.Done(totalDone)
        } catch (e: Exception) {
            _state.value = DownloadState.Failed(e.message ?: "unduh gagal")
        }
    }

    private fun downloadOne(f: ModelFile, onProgress: (Long, Long) -> Unit) {
        val tmp = File(dir, f.name + ".part")
        val url = URL(f.url)
        (url.openConnection() as HttpURLConnection).run {
            connectTimeout = 30_000; readTimeout = 60_000
            instanceFollowRedirects = true
            connect()
            if (responseCode !in 200..299) throw IllegalStateException("HTTP $responseCode untuk ${f.name}")
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
