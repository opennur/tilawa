## Fase 6 — Verifikasi End-to-End
Status: SELESAI DENGAN CATATAN (APK belum ada)
Yang dikerjakan: test_e2e.py — setA 5 benar, setB 5 salah (≥2 error), setC sintetik (silence/noise/sine) + empty. Confusion matrices di METRICS.md. README + LIMITATIONS + LICENSE_ANALYSIS lengkap.
Metrik: setA qalqalah benar 0.80 (4/5); setB terdeteksi-salah 0.60 (3/5); setC silence prob 0.000, noise 0.001, sine 1.000 (limitation tonal) tanpa crash.
Gerbang verifikasi: [2/6 gagal jujur] 3 set diuji YA; CM ada YA; README lisensi YA; LIMITATIONS YA; APK install/airplane-mode GAGAL (BLOCKER SDK, tak diklaim).
Keputusan teknis: D6.
Blocker: APK debug tidak ada → butuh mesin dengan Android SDK untuk assembleDebug + uji airplane sungguhan.
Langkah berikutnya: build di CI ber-SDK, integrasi ORT streaming, forced-alignment, latih multi-level CTC.
