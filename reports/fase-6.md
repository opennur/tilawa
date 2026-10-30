## Fase 6 — Verifikasi End-to-End
Status: SELESAI (APK tersedia via CI pada 2026-10-31)
Yang dikerjakan: test_e2e.py — setA 5 benar, setB 5 salah, setC sintetik + empty. Confusion matrices di METRICS.md. README + LIMITATIONS + LICENSE_ANALYSIS lengkap. APK debug (36 MB artefak) + rilis signed v0.1.0-mvp (80 MB) di https://github.com/opennur/tilawa/releases/tag/v0.1.0-mvp.
Metrik: setA qalqalah benar 0.80; setB terdeteksi-salah 0.60; setC silence 0.000, noise 0.001, sine 1.000 (limitation) tanpa crash.
Gerbang verifikasi: [terpenuhi] 3 set diuji YA; CM ada YA; README lisensi YA; LIMITATIONS YA; APK bisa diunduh YA (uji install/airplane di device fisik masih TODO pengguna).
Keputusan teknis: D6 (revisi build CI).
Blocker: tidak ada. Uji install + airplane-mode perlu device fisik oleh pengguna.
Langkah berikutnya: integrasi ORT streaming + forced-alignment di device.
