# model-assets/tokens.txt — atribusi & lisensi

Tabel 251 token fonem tajwid (id 0–250, blank = 250 `<blank>`), disalin verbatim
dari `@tilawa/core@0.4.0` (`dist/recitation/tokens.js`: `TOKENS`, `BLANK_ID = 250`,
`VOCAB_SIZE = 251`), diambil via jsDelivr tanpa autentikasi pada 2026-10-31.

- Kode `@tilawa/core`: MIT (yazinsai/tilawa).
- Tabel token ini adalah Derivative dari karya Quran-Lab
  (`Quran-Lab/zipformer_p-arabic-v3`, `tokens.txt`) dan didistribusikan di bawah
  **NPL-1.2** (non-komersial, share-alike). Lihat `app/src/main/assets/NPL-1.2.txt`.
- Alasan mirror: `tokens.txt` upstream di HuggingFace berada di repo **gated
  (manual approval)** sehingga unduhan anonim dari aplikasi selalu HTTP 401.
  Isi identik dengan tabel yang dibundel Tilawa untuk model
  `zipformer_a0w_ep1_a05.int8.onnx`, jadi ini mapping yang benar untuk model tersebut.
- Verifikasi: 251 baris, baris 1 = `ؙ`, baris 251 = `<blank>`.
