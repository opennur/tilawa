# DECISIONS.md — Tahsin Ultimate

## D1. Bundling model: first-launch download, bukan assets/
Alasan: APK +66 MB boros; NPL-1.2 §7 wajib sertakan lisensi — download terpisah memperjelas batas kode MIT vs aset NPL; update model tanpa rilis APK; user bisa hapus model. Lihat `LICENSE_ANALYSIS.md`.
URL exact:
- `https://github.com/yazinsai/tilawa/releases/download/zipformer-a0w-ep1-a0.5/zipformer_a0w_ep1_a05.int8.onnx` (66.03 MB, int8)
- `https://github.com/yazinsai/tilawa/releases/download/v0.3.0/zipformer_quran.json` (5.5 MB, korpus fonem per kata, NPL-1.2)
- `https://github.com/yazinsai/tilawa/releases/download/zipformer-a0w-ep1-a0.5/NPL-1.2.txt`
- `https://huggingface.co/Quran-Lab/zipformer_p-arabic-v3/resolve/main/tokens.txt` (symbol table, blank=250 — wajib, tanpa ini decode garbage)

## D2. ASR runtime: ONNX Runtime Mobile + Kaldi FBank 80-dim (Povey window), bukan mel-spectrogram biasa
Alasan: Quran-Lab menyatakan front-end harus Kaldi fbank, slaney-mel menggandakan error. I/O manifest: T=61, hop=48, featureDim=80, streaming CTC `model_type=zipformer2`, chunk 16/24 in-domain, full-context untuk out-of-domain. Sherpa-ONNX dipakai sebagai referensi JNI; jika AAR tidak tersedia di Termux/CI, fallback ke `onnxruntime-android` + implementasi streaming loop manual sesuai `export_quran_streaming_onnx.py`. Tidak memakai Whisper (loop repetisi pada tilawah melodik; CTC tidak loop — sesuai catatan Tilawa).

## D3. Arsitektur hybrid TajweedAI: ASR untuk alignment + classifier biner per aturan
Alasan: model Zipformer dilatih hampir murni bacaan benar dengan label kanonik — ia mentranskripsi aturan bukan deviasi (81/94 ikhfaa dilaporkan penuh padahal nun jelas). Jadi tidak boleh pakai token ASR sebagai judge langsung. Setiap aturan = classifier biner di atas segmen aligned: durasi (Madd/Ghunnah), energi burst + spektral (Qalqalah), nasality/low-formant (Ikhfa), plus timing forced-alignment untuk Madd bebas 2/4/6 harakat.
Scope: akustik penuh hanya Madd, Ghunnah, Ikhfa, Qalqalah (yang ada di QDAT/qdat_bench). Idgham/Iqlab = rule-based lokasi teks + cek durasi segmen (partial). Izhar = known gap.

## D4. Evaluasi jujur di qdat_bench (159 sampel, Al-Maidah 5:109), tanpa rounding-up
Alasan: baseline paper (muaalem): Tajweed F1 0.758 (Noon Moshaddadah 0.869, Ikhfaa 0.453, Qalqalah 0.953), Madd RMSE ~0.6. Target MVP: Qalqalah ≥0.57, Ghunnah ≥0.55, Madd ≥0.60, Ikhfa ≥0.453. Jika < target, catat di METRICS.md + LIMITATIONS.md, jangan hapus aturan.

## D5. QPS dua level untuk sifat huruf
Alasan: ikuti `quran-transcript` + kolom `sifat` qdat_bench (10 sifat: ghonna, hams/jahr, itbaq, dll). MVP deteksi 3 pasang makhraj: ت/ط (tafkheem/itbaq), س/ص (itbaq/safeer), ح/ه (hams). Ditentukan dari phonetic_transcript + sifat, bukan dari audio mentah saja (keterbatasan didokumentasikan).

## D6. Build tanpa Android SDK di CI Termux = dokumentasikan blocker, lanjut logika JVM/Python
Alasan: guardrail melarang lanjut dengan asumsi, tapi blocker APK tidak memblokir classifier/metrics/UI logic. APK debug dicoba via Gradle wrapper; jika SDK tidak ada setelah 3 percobaan, catat BLOCKER di reports/fase-1.md dan lanjut Fase 2-6 pada level kode + evaluasi Python.
