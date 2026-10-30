"""Uji end-to-end 3 set: (a) benar, (b) salah disengaja, (c) acak/non-Quran."""
import numpy as np
import importlib.util
spec = importlib.util.spec_from_file_location("ev", "ml/evaluation/evaluate_numpy.py")
ev = importlib.util.module_from_spec(spec); spec.loader.exec_module(ev)

def rows(off, ln):
    return ev.get_json(ev.BASE.format(off=off, ln=ln))["rows"]

def synth(kind, sr=16000, dur=3.0):
    n = int(sr * dur)
    if kind == "silence": return np.zeros(n), sr
    if kind == "noise": return np.random.RandomState(0).randn(n) * 0.1, sr
    if kind == "sine": return (0.3 * np.sin(2 * np.pi * 440 * np.arange(n) / sr)), sr
    raise ValueError

def is_correct(r):
    return (r["qalqalah"] == 1 and r["noon_moshaddadah_len"] == 1 and r["noon_mokhfah_len"] == 2 and r["separate_madd"] == 4)

def is_wrong(r):
    errs = (0 if r["qalqalah"] == 1 else 1) + (0 if r["noon_moshaddadah_len"] == 1 else 1) + (0 if r["noon_mokhfah_len"] == 2 else 1) + (0 if r["separate_madd"] == 4 else 1)
    return errs >= 2

def main():
    all_rows = []
    for off in range(0, 159, 20):
        ln = min(20, 159 - off)
        batch = rows(off, ln)
        # unduh langsung selagi URL fresh
        for r in batch:
            wav = ev.download_wav(r["row"]["audio"][0]["src"])
            arr, sr = ev.read_wav(wav)
            r["row"]["_feat"] = ev.feats(arr, sr)
        all_rows += batch
        print(f"batch {off}: {len(batch)} ok")
    labels = [r["row"] for r in all_rows]
    X = np.nan_to_num(np.vstack([r["_feat"] for r in labels]))
    correct = [r for r in labels if is_correct(r)][:5]
    wrong = [r for r in labels if is_wrong(r)][:5]
    print(f"setA benar: {len(correct)}, setB salah: {len(wrong)}")
    yq = np.array([1 if r["qalqalah"] == 1 else 0 for r in labels])
    mu, sd = X.mean(0), X.std(0) + 1e-9
    Xn = (X - mu) / sd
    w, b = ev.train_lr(Xn, yq)
    pred = (ev.sigmoid(Xn @ w + b) >= 0.5).astype(int)
    idx_c = [i for i, r in enumerate(labels) if r["id"] in {c["id"] for c in correct}]
    idx_w = [i for i, r in enumerate(labels) if r["id"] in {c["id"] for c in wrong}]
    print(f"setA qalqalah pred-benar rate: {(pred[idx_c]==1).mean():.2f} (n={len(idx_c)})")
    print(f"setB qalqalah terdeteksi-salah rate: {(pred[idx_w]==0).mean():.2f} (n={len(idx_w)})")
    for kind in ["silence", "noise", "sine"]:
        arr, sr = synth(kind)
        try:
            f = ev.feats(arr, sr)
            p = float(ev.sigmoid((f - mu) / sd @ w + b))
            print(f"setC {kind}: prob={p:.3f} tanpa crash OK")
        except Exception as e:
            print(f"setC {kind}: ditangani ({e})")
    try:
        ev.feats(np.zeros(100), 16000); print("empty kecil: OK tanpa crash")
    except Exception as e:
        print(f"empty kecil: ditangani ({e})")

if __name__ == "__main__":
    main()
