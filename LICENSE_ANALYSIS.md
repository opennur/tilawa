# LICENSE_ANALYSIS.md — Tahsin Ultimate

Tanggal: 2026-10-31. Sumber diverifikasi: `./tmp/NPL-1.2.txt` (7041 bytes, dari release `zipformer-a0w-ep1-a0.5`), `zipformer_a0w_ep1_a05.io.json` (26867 bytes, vocabSize 251).

## 1. Apa yang dilisensikan NPL-1.2?

Model `zipformer_a0w_ep1_a05.int8.onnx` + `zipformer_quran.json` + turunan Quran-Lab `zipformer_p-arabic-v3` = "Work" di bawah NPL-1.2. Bukan MIT. Kode `@tilawa/core` MIT, tetapi model + korpus fonem tetap NPL-1.2 (lihat NOTICE.md upstream).

Definisi kunci (verbatim diringkas):
- `Derivative` = modifikasi, port, konversi format, re-export, dataset/lexicon/label dari Work, **model ML yang dilatih/fine-tuned/evaluasi memakai Work atau outputnya**.
- `Charge` = meminta/menerima pembayaran apapun untuk Work, akses ke Work, atau **fitur apapun yang Work-nya powers** (termasuk sale, subscription, paywall, trial berbayar, iklan pada fitur itu).
- `Local Use` = jalan di device sendiri / untuk murid langsung, tanpa network ke orang lain — selalu permitted, bukan Charge.
- `Share-Alike` (§7): setiap Derivative wajib didistribusikan di bawah NPL-1.2 yang sama, tanpa terms tambahan. Wajib sertakan file lisensi saat distribusi.
- `No commercial license` (§9): tidak ada opsi beli lisensi komersial.

## 2. Tiga pertanyaan wajib

### Q1: APK berisi model boleh di-upload Play Store gratis?
**Jawaban: Ya, dengan syarat ketat.**
- Play Store gratis (tanpa harga install, tanpa IAP, tanpa subscription, tanpa ads — khususnya tanpa ads pada layar skor/tajweed) = bukan `Charge` menurut §3-§4.
- Tetap wajib: (a) sertakan `NPL-1.2.txt` di APK + layar About, (b) APK/feature skor tajweed yang "powered by" model dianggap Derivative → wajib share-alike NPL-1.2, tidak boleh relicense ke MIT/Apache saja, (c) tidak boleh ada paywall/trial.
- Risiko: jika ada AdMob banner di layar hasil, itu = `advertising revenue earned on a feature the Work powers` → pelanggaran → lisensi terminasi otomatis (§10). Maka build MVP: **zero ads, zero IAP, zero paywall**.
- Catatan: ini bukan nasihat hukum (§12). Untuk kepastian, minta review ke penasihat hukum sebelum rilis publik.

### Q2: Pengguna mengekstrak model dari APK melanggar share-alike?
**Jawaban: Tidak melanggar — justru diwajibkan memfasilitasi.**
- §7 mewajibkan Derivative tersedia di bawah NPL-1.2 yang sama. Mengekstrak untuk dipelajari/didistribusi ulang di bawah NPL-1.2 = sesuai lisensi.
- Yang melanggar adalah mengekstrak lalu menjual / menutup source / relicense ke lisensi lain.
- Implikasi: jangan implementasi anti-extract yang menghalangi compliance; cukup obfuscation standar, bukan enkripsi untuk menyembunyikan kewajiban lisensi.

### Q3: Strategi "model diunduh terpisah" menghindari distribusi?
**Jawaban: Tidak menghindari kewajiban, tapi direkomendasikan.**
- Alasan: selama fitur skor tajweed "depends on it", kewajiban §3 + §7 tetap menempel walau file ONNX tidak dibundel di APK. Download terpisah bukan loophole.
- Tetap direkomendasikan karena: (1) APK hemat 66 MB, (2) pemisahan jelas kode MIT vs aset NPL, (3) update model tanpa update APK, (4) user yang menolak NPL bisa pakai app tanpa fitur ASR (degraded mode), (5) transparan: user download langsung dari release upstream + checksum.
- Implementasi: first-launch download dari `https://github.com/yazinsai/tilawa/releases/download/zipformer-a0w-ep1-a0.5/zipformer_a0w_ep1_a05.int8.onnx`, verifikasi SHA256, simpan di `filesDir/models/`, tampilkan lisensi + tombol "Hapus model".

## 3. Rekomendasi distribusi (dipakai di DECISIONS.md)

1. Kode app: MIT (atau Apache-2.0), aset model + `zipformer_quran.json` + `tokens.txt`: NPL-1.2.
2. First-launch download, bukan `assets/`. Fallback offline manual via file picker.
3. Play Store listing: label "Gratis, tanpa iklan/IAP, model NPL-1.2 non-komersial share-alike", link ke repo model + `qdat_bench` (Apache-2.0) + teks Quran (Tanzil public domain).
4. Layar About wajib: kutip NPL §3 + link NPL-1.2.txt + credit Quran-Lab, Tilawa, obadx/qdat_bench, itqan (MIT).
5. `LIMITATIONS.md` wajib catat: tidak untuk qiraat selain Hafs 'an 'Asim, tidak untuk anak <12 tahun (PER 2-3x lebih buruk), Madd bebas 2/4/6 harakat harus via timing bukan token.
