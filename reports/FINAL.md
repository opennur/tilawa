# FINAL — Laporan Konsolidasi Tahsin Ultimate

## Status fase
- SELESAI: 0, 2, 3.
- SELESAI DENGAN CATATAN: 1 (SDK blocker), 4 (QPS label-based), 5 (UI tanpa device), 6 (tanpa APK).
- TERHENTI: tidak ada total; blocker APK tidak memblokir logika.

## Metrik kunci (apa adanya, qdat_bench 159, 5-fold CV numpy-LR fitur global)
- Qalqalah F1 0.8342 (target ≥0.57 OK)
- Ghunnah F1 0.9114 (target ≥0.55 OK)
- Ikhfa F1 0.6349 (target ≥0.453 OK, tapi waspadai leakage single-verse)
- Madd F1 0.7784 (target ≥0.60 OK, definisi separate==4)
- Rata-rata 0.7897. E2E: setA 0.80, setB 0.60, setC tanpa crash.
- Paper SOTA 0.758 (konteks alignment+CTC) — angka kami bukan perbandingan langsung.

## Batasan utama
QDAT 404; APK belum ada (no SDK); ASR belum inference device; Madd biner; Idgham/Iqlab partial; Izhar gap; QPS label-based; sine false-positive; hanya 1 ayat + Hafs; NPL non-komersial.

## File yang dihasilkan
LICENSE_ANALYSIS.md, DECISIONS.md, METRICS.md, LIMITATIONS.md, README.md, data/quran_refs.json, app/(gradle,Manifest,MainActivity,AudioRecorder,TilawaAsrEngine,RuleClassifiers,MakhrajChecker,Scoring), ml/evaluation/(evaluate_qdat_bench.py,evaluate_numpy.py,test_e2e.py,results.json), tmp/(NPL-1.2.txt,tilawa_io.json,row0.json), reports/fase-0..6.md.

## Rekomendasi lanjutan
1. Build di runner ber-SDK: `./gradlew assembleDebug`, integrasi onnxruntime-android streaming + Kaldi fbank, Logcat transkripsi.
2. Forced-alignment (CTC segmentation) + RMSE harakat Madd + VAD.
3. Latih/evaluasi multi-level CTC QPS; kumpulkan data 4 surah target + anak.
4. Uji airplane + install sungguhan; tambah skor fluency; review guru tajwid.
