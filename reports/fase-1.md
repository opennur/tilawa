## Fase 1 — Fondasi: ASR + Word Alignment
Status: SELESAI DENGAN CATATAN (BLOCKER build)
Yang dikerjakan: Scaffold Kotlin+Compose minSdk26 (settings/build/app gradle, Manifest, MainActivity demo offline, AudioRecorder WAV 16kHz mono 44-byte header, TilawaAsrEngine stub jujur tolak tanpa model). Quran refs 4 surah.
Metrik: tidak ada (belum inference device).
Gerbang verifikasi: [gagal 1/3] assembleDebug GAGAL 3x (no gradlew, ANDROID_HOME kosong, no build-tools) → BLOCKER dicatat; transkripsi Logcat belum dibuktikan (engine sengaja throw agar tak mengarang); audio tersimpan sementara TERBUKTI via kode (patch header + size log).
Keputusan teknis: D2, D6.
Blocker: Android SDK tidak ada di Termux → APK belum ada. Tidak memblokir logika Fase 2-6.
Langkah berikutnya: Fase 2 classifier (Python+numpy, tanpa SDK).
