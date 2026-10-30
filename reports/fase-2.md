## Fase 2 — Satu Aturan Tajwid (Qalqalah)
Status: SELESAI
Yang dikerjakan: QalqalahClassifier (burst akhir) + evaluasi 5-fold CV numpy-LR pada 159 audio nyata qdat_bench (fitur: tail-burst, rms, band).
Metrik: Qalqalah F1=0.8342, acc=0.7925, CM=[[43,15],[18,83]], pos=101/159.
Gerbang verifikasi: [terpenuhi] F1 dilaporkan apa adanya; tooltip penjelasan ada di RuleClassifiers.explain + demo UI; audio kosong/bising/sine tanpa crash (test_e2e).
Keputusan teknis: D3.
Blocker: tidak ada.
Langkah berikutnya: Fase 3 perluas 3 aturan.
