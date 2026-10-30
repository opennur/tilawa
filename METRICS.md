# METRICS.md — hasil jujur qdat_bench (159 sampel, 5-fold CV, numpy-LR, fitur global)

Metode: fitur audio global (durasi, rms, zcr, centroid, low/high-band nasal proxy, tail-burst qalqalah proxy, voiced-dur, 6 band) + LogisticRegression numpy, standardisasi dalam-fold. Tanpa forced-alignment → baseline bawah, bukan SOTA. Tanpa rounding-up.

| Aturan | Definisi benar | F1 | Akurasi | CM [[TN,FP],[FN,TP]] | n/pos |
|---|---|---|---|---|---|
| qalqalah | qalqalah==1 | 0.8342 | 0.7925 | [[43, 15], [18, 83]] | 159/101 |
| ghunnah | moshaddadah==1 (parsial=0 salah) | 0.9114 | 0.8679 | [[30, 11], [10, 108]] | 159/118 |
| ikhfa | mokhfah==2 (0/1 salah) | 0.6349 | 0.7107 | [[73, 22], [24, 40]] | 159/64 |
| madd | separate==4 | 0.7784 | 0.7421 | [[46, 24], [17, 72]] | 159/89 |

Rata-rata F1 = 0.7897

Bandingkan baseline paper muaalem multi-level CTC: Tajweed F1 0.758 (Moshaddadah 0.869, Ikhfaa 0.453, Qalqalah 0.953). Gap ini expected: paper memakai alignment + QPS, kami hanya fitur global.
