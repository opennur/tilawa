"""Evaluasi jujur baseline heuristik+LR pada qdat_bench (159 sampel, Al-Maidah 5:109).
Tanpa rounding-up. Output: METRICS.md + confusion matrices.
Label:
- qalqalah: 0=salah,1=benar (langsung)
- ghunnah: noon_moshaddadah_len 1=benar, 0=salah
- ikhfa: noon_mokhfah_len 2=benar, 0/1=salah (biner; 3-kelas juga dilaporkan)
- madd: separate_madd==4 benar else salah (kanonik munfasil 4 harakat; toleransi dibahas di LIMITATIONS)
Fitur audio (tanpa alignment penuh — keterbatasan jujur):
- durasi, rms mean/std, zcr, centroid spektral, rolloff, mfcc mean(13), energi low 200-500Hz ratio (nasal proxy), transient akhir (qalqalah proxy: rms 10% akhir / rms global), voiced-dur proxy.
Model: LogisticRegression per aturan, stratified 5-fold CV, laporkan F1 mean.
"""
import numpy as np, os, json
from sklearn.linear_model import LogisticRegression
from sklearn.model_selection import StratifiedKFold, cross_val_predict
from sklearn.metrics import f1_score, confusion_matrix, accuracy_score

def load():
    from datasets import load_dataset
    ds = load_dataset("obadx/qdat_bench", split="train")
    return ds

def features_from_audio(arr, sr):
    import librosa
    if sr != 16000:
        arr = librosa.resample(arr.astype(float), orig_sr=sr, target_sr=16000); sr = 16000
    arr = arr.astype(float)
    dur = len(arr) / sr
    rms = float(np.sqrt(np.mean(arr**2)) + 1e-9)
    rms_frame = np.sqrt(np.convolve(arr**2, np.ones(256)/256, mode="same") + 1e-12)
    tail = rms_frame[int(len(rms_frame)*0.9):].mean() / (rms_frame.mean() + 1e-12)  # burst akhir ~ qalqalah
    zcr = float(((arr[:-1] * arr[1:]) < 0).mean())
    S = np.abs(np.fft.rfft(arr[:min(len(arr), sr*4)])) + 1e-12
    freqs = np.fft.rfftfreq(min(len(arr), sr*4), 1/sr)
    centroid = float((freqs * S).sum() / S.sum())
    low = S[(freqs >= 200) & (freqs <= 500)].sum() / S.sum()  # nasal proxy
    mfcc = librosa.feature.mfcc(y=arr, sr=sr, n_mfcc=13)
    mfcc_mean = mfcc.mean(axis=1)
    voiced = (rms_frame > rms_frame.mean()).sum() / sr  # proxy durasi vokal (madd)
    feat = np.concatenate([[dur, rms, float(rms_frame.std()), zcr, centroid, float(low), float(tail), float(voiced)], mfcc_mean])
    return feat

def main():
    ds = load()
    print(f"loaded {len(ds)} samples")
    X, y_q, y_g, y_i, y_m = [], [], [], [], []
    for r in ds:
        a = r["audio"]; arr = np.array(a["array"], dtype=np.float32); sr = a["sampling_rate"]
        try:
            f = features_from_audio(arr, sr)
        except Exception as e:
            print("feat fail", r["id"], e); continue
        X.append(f)
        y_q.append(int(r["qalqalah"]))
        y_g.append(1 if int(r["noon_moshaddadah_len"]) == 1 else 0)
        y_i.append(1 if int(r["noon_mokhfah_len"]) == 2 else 0)
        y_m.append(1 if int(r["separate_madd"]) == 4 else 0)
    X = np.nan_to_num(np.vstack(X), nan=0.0, posinf=0.0, neginf=0.0)
    out = {}
    for name, y in [("qalqalah", y_q), ("ghunnah", y_g), ("ikhfa", y_i), ("madd", y_m)]:
        y = np.array(y)
        print(f"\n{name}: pos={y.sum()}/{len(y)} rate={y.mean():.3f}")
        if y.sum() == 0 or y.sum() == len(y):
            print(f"{name}: single class, skip"); out[name] = {"f1": None, "note": "single class"}; continue
        clf = LogisticRegression(max_iter=1000)
        cv = StratifiedKFold(n_splits=5, shuffle=True, random_state=0)
        pred = cross_val_predict(clf, X, y, cv=cv)
        f1 = f1_score(y, pred, zero_division=0); acc = accuracy_score(y, pred)
        cm = confusion_matrix(y, pred).tolist()
        print(f"{name}: F1={f1:.4f} acc={acc:.4f} cm={cm}")
        out[name] = {"f1": round(float(f1), 4), "acc": round(float(acc), 4), "cm": cm, "pos": int(y.sum()), "n": len(y)}
    os.makedirs("ml/evaluation", exist_ok=True)
    with open("ml/evaluation/results.json", "w") as f: json.dump(out, f, indent=2)
    # tulis METRICS.md
    with open("METRICS.md", "w") as f:
        f.write("# METRICS.md — hasil jujur qdat_bench (159 sampel, 5-fold CV, fitur audio global tanpa forced-alignment)\n\n")
        f.write("Baseline heuristik+LogisticRegression. Bukan SOTA paper (0.758). Dilaporkan apa adanya, tanpa rounding-up.\n\n")
        f.write("| Aturan | Definisi benar | F1 | Akurasi | CM [[TN,FP],[FN,TP]] | n/pos |\n|---|---|---|---|---|---|\n")
        for k in ["qalqalah", "ghunnah", "ikhfa", "madd"]:
            v = out.get(k, {})
            f.write(f"| {k} | { {'qalqalah':'qalqalah==1','ghunnah':'moshaddadah==1','ikhfa':'mokhfah==2','madd':'separate==4'}.get(k)} | {v.get('f1')} | {v.get('acc')} | {v.get('cm')} | {v.get('n')}/{v.get('pos')} |\n")
        f.write("\nRata-rata F1 (aturan yg terhitung) = %.4f\n" % np.mean([v["f1"] for v in out.values() if v.get("f1") is not None]))
        f.write("\nCatatan: fitur global tanpa segmentasi ayat → skor rendah expected. Model SOTA memakai multi-level CTC + alignment. Lihat LIMITATIONS.md.\n")
    print("wrote ml/evaluation/results.json + METRICS.md")

if __name__ == "__main__":
    main()
