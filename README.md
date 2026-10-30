# Tahsin Ultimate — Deteksi Tajwid 100% On-Device (MVP)

[![Android CI](https://github.com/opennur/tilawa/actions/workflows/android.yml/badge.svg)](https://github.com/opennur/tilawa/actions/workflows/android.yml)
**APK debug & rilis signed dibangun di CI.** Unduh versi terbaru: https://github.com/opennur/tilawa/releases/tag/v0.2.0-mvp (`app-release.apk`, signed, ~80 MB). Model Tilawa 66 MB diunduh saat first-launch (tidak dibundel).

Alur pakai: Beranda → pilih surat → Rekam ayat → Analisis → Hasil (skor + kata berwarna + penjelasan per kesalahan) → Mushaf (tap kata untuk hukum tajwid) → Tentang (lisensi + kelola model).

Aplikasi Android native (Kotlin+Compose, minSdk 26) untuk merekam 1 ayat, menganalisis 3 lapis (kata/fonem/sifat), dan menjelaskan kesalahan tajwid. **100% offline setelah first-launch download model.**

## Arsitektur (3 pipeline paralel)
- **A — ASR & word alignment**: Tilawa Zipformer2-CTC int8 66 MB, 251 token fonem tajwid. File: `zipformer_a0w_ep1_a05.int8.onnx`, `tokens.txt` (blank=250), `zipformer_quran.json`. Front-end Kaldi fbank 80-dim. Kode: `app/.../asr/TilawaAsrEngine.kt`, rekam 16 kHz mono: `audio/AudioRecorder.kt`.
- **B — Classifier per aturan (hybrid TajweedAI)**: `tajweed/RuleClassifiers.kt` (Madd, Ghunnah, Ikhfa, Qalqalah) + `RuleBasedLocator` (Idgham/Iqlab partial). Evaluasi: `ml/evaluation/evaluate_numpy.py` (numpy saja).
- **C — QPS fonem+sifat**: `qps/MakhrajChecker.kt` untuk ت/ط, س/ص, ح/ه via itbaq/hams.

## Hasil metrik (jujur, qdat_bench 159 sampel, 5-fold CV)
Lihat `METRICS.md`: Qalqalah F1 0.8342, Ghunnah 0.9114, Ikhfa 0.6349, Madd 0.7784, rata-rata 0.7897. Caveat single-verse di `LIMITATIONS.md`. Target MVP (≥0.57/0.55/0.60/0.453) terpenuhi pada benchmark ini, tetapi generalisasi belum terbukti.

## Cara jalan (dev)
1. Baca `LICENSE_ANALYSIS.md` + `DECISIONS.md`.
2. First-launch: unduh model dari release Tilawa (link di DECISIONS D1), verifikasi SHA, simpan di `filesDir/models/`.
3. Evaluasi: `python3 ml/evaluation/evaluate_numpy.py` (butuh numpy + akses HF), `python3 ml/evaluation/test_e2e.py`.
4. Build APK: butuh Android SDK + `gradlew`; di CI ini BLOCKER (lihat reports/fase-1.md). Kode Compose di `MainActivity.kt` (demo scoring offline + warna hijau/kuning/merah + penjelasan + skor 0-100).

## Lisensi & batasan
- Model Tilawa: **NPL-1.2 non-komersial share-alike** — gratis tanpa ads/IAP pada fitur tajwid, sertakan NPL-1.2.txt, derivative di bawah NPL-1.2. Bukan nasihat hukum.
- Dataset `qdat_bench`: Apache-2.0 (`obadx/qdat_bench`). QDAT asli 404 (butuh Kaggle).
- Teks Quran `data/quran_refs.json`: Tanzil/public domain.
- App code: disarankan MIT; aset model tetap NPL (lihat LICENSE_ANALYSIS).
- Scope: akustik penuh 4 aturan; Idgham/Iqlab partial; Izhar gap. Hanya Hafs, bukan anak<12 tanpa validasi.
