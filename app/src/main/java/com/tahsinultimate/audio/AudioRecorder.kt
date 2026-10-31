package com.tahsinultimate.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.File

/** Rekam 16 kHz mono PCM16 — syarat Tilawa Zipformer. Disimpan sementara, bisa diverifikasi. */
class AudioRecorder(private val outFile: File) {
    private var rec: AudioRecord? = null
    private var thread: Thread? = null
    @Volatile private var running = false
    /** Level suara 0..1 untuk meteran UI (diperbarui tiap blok baca). */
    @Volatile var level01: Float = 0f

    fun start() {
        val minBuf = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        rec = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, 16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minBuf * 2)
        rec?.startRecording()
        running = true
        thread = Thread {
            outFile.outputStream().use { out ->
                // tulis header WAV minimal 44 byte
                out.write(wavHeader(0))
                var total = 0
                val buf = ShortArray(1600)
                while (running) {
                    val n = rec?.read(buf, 0, buf.size) ?: -1
                    if (n > 0) {
                        var peak = 0
                        for (i in 0 until n) peak = maxOf(peak, kotlin.math.abs(buf[i].toInt()))
                        level01 = (peak / 32768f).coerceIn(0f, 1f)
                        val bytes = ByteArray(n * 2)
                        for (i in 0 until n) {
                            bytes[i * 2] = (buf[i].toInt() and 0xFF).toByte()
                            bytes[i * 2 + 1] = ((buf[i].toInt() shr 8) and 0xFF).toByte()
                        }
                        out.write(bytes); total += bytes.size
                    }
                }
            }
            patchWavHeader(outFile)
        }.also { it.start() }
        android.util.Log.i("TahsinASR", "recording started -> ${outFile.absolutePath}")
    }

    fun stop(): File {
        running = false
        thread?.join(2000)
        rec?.stop(); rec?.release(); rec = null
        android.util.Log.i("TahsinASR", "recording stopped, size=${outFile.length()} bytes")
        return outFile
    }

    private fun wavHeader(dataLen: Int): ByteArray {
        val h = ByteArray(44); val sr = 16000
        fun putStr(o: Int, s: String) { s.forEachIndexed { i, c -> h[o + i] = c.code.toByte() } }
        fun putInt(o: Int, v: Int) { for (i in 0..3) h[o + i] = ((v shr (8 * i)) and 0xFF).toByte() }
        fun putShort(o: Int, v: Int) { h[o] = (v and 0xFF).toByte(); h[o + 1] = ((v shr 8) and 0xFF).toByte() }
        putStr(0, "RIFF"); putInt(4, 36 + dataLen); putStr(8, "WAVE"); putStr(12, "fmt ")
        putInt(16, 16); putShort(20, 1); putShort(22, 1); putInt(24, sr)
        putInt(28, sr * 2); putShort(32, 2); putShort(34, 16); putStr(36, "data"); putInt(40, dataLen)
        return h
    }

    private fun patchWavHeader(f: File) {
        val dataLen = (f.length() - 44).toInt().coerceAtLeast(0)
        val raf = java.io.RandomAccessFile(f, "rw")
        raf.seek(4); raf.write(intLE(36 + dataLen)); raf.seek(40); raf.write(intLE(dataLen)); raf.close()
    }
    private fun intLE(v: Int) = byteArrayOf((v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte(), ((v shr 16) and 0xFF).toByte(), ((v shr 24) and 0xFF).toByte())
}
