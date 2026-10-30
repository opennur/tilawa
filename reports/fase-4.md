## Fase 4 — Analisis Sifat Huruf (QPS Level)
Status: SELESAI DENGAN CATATAN (partial, label-based)
Yang dikerjakan: MakhrajChecker.kt untuk 3 pasang (ت/ط itbaq, س/ص itbaq+safeer, ح/ه hams/jahr). Diuji pada 5 sampel manual qdat_bench (sifat_n=27 per sampel, contoh قَ/تَ terverifikasi dari API).
Metrik: 3/3 jenis terdeteksi pada level struktur anotasi; penjelasan per pasang ada.
Gerbang verifikasi: [terpenuhi parsial] 3 jenis + pasangan tertukar ada; diuji 5 sampel manual. Catatan: memakai label sifat, bukan klasifikasi waveform mentah (lihat LIMITATIONS #7).
Keputusan teknis: D5.
Blocker: model multi-level CTC belum dilatih (butuh 848 jam data) → di luar MVP.
Langkah berikutnya: Fase 5 feedback.
