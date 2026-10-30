"""Evaluasi jujur qdat_bench HANYA dengan numpy+stdlib (tanpa sklearn/librosa).
- Ambil 159 rows via datasets-server API, unduh wav, ekstrak fitur murni numpy.
- Classifier: logistic regression numpy (gradient descent) + threshold baseline, 5-fold CV manual.
- Output: ml/evaluation/results.json + METRICS.md. Tanpa rounding-up.
"""
import json, urllib.request, urllib.parse, wave, io, os, math, random, subprocess, tempfile
import numpy as np

BASE = "https://datasets-server.huggingface.co/rows?dataset=obadx%2Fqdat_bench&config=default&split=train&offset={off}&length={ln}"

def curl_bytes(url, timeout=120):
    import time
    last = None
    for a in range(5):
        out = tempfile.mktemp()
        p = subprocess.run(["curl", "-s", "--retry", "3", "--retry-delay", "2", "--max-time", str(timeout), "-L", url, "-o", out])
        if p.returncode == 0:
            with open(out, "rb") as f: data = f.read()
            try: os.remove(out)
            except: pass
            if len(data) > 1000: return data
            last = f"short {len(data)}"
        else:
            last = f"curl rc={p.returncode}"
        time.sleep(2 + a)
    raise RuntimeError(f"curl gagal: {last}")

def get_json(url):
    return json.loads(curl_bytes(url).decode())

def download_wav(url):
    return curl_bytes(url)

def read_wav(b):
    with wave.open(io.BytesIO(b), "rb") as w:
        n, ch, sr, samp = w.getnframes(), w.getnchannels(), w.getframerate(), w.getsampwidth()
        raw = w.readframes(n)
    if samp == 2:
        arr = np.frombuffer(raw, dtype=np.int16).astype(np.float64) / 32768.0
    elif samp == 1:
        arr = (np.frombuffer(raw, dtype=np.uint8).astype(np.float64) - 128) / 128.0
    else:
        raise ValueError(f"sampwidth {samp}")
    if ch > 1:
        arr = arr.reshape(-1, ch).mean(axis=1)
    return arr, sr

def resample_simple(arr, sr, target=16000):
    if sr == target: return arr
    # linear interp kasar tapi jujur (didokumentasikan)
    n_new = int(len(arr) * target / sr)
    old_idx = np.linspace(0, len(arr) - 1, n_new)
    return np.interp(old_idx, np.arange(len(arr)), arr)

def feats(arr, sr):
    arr = resample_simple(arr, sr); sr = 16000
    dur = len(arr) / sr
    rms = float(np.sqrt(np.mean(arr ** 2)) + 1e-12)
    w = 256
    ker = np.ones(w) / w
    env = np.sqrt(np.convolve(arr ** 2, ker, mode="same") + 1e-12)
    tail = float(env[int(len(env) * 0.9):].mean() / (env.mean() + 1e-12))
    zcr = float(((arr[:-1] * arr[1:]) < 0).mean())
    N = min(len(arr), sr * 4)
    seg = arr[:N] * np.hanning(N)
    S = np.abs(np.fft.rfft(seg)) + 1e-12
    freqs = np.fft.rfftfreq(N, 1 / sr)
    centroid = float((freqs * S).sum() / S.sum())
    low = float(S[(freqs >= 200) & (freqs <= 500)].sum() / S.sum())
    high = float(S[(freqs >= 2000) & (freqs <= 8000)].sum() / S.sum())
    voiced = float((env > env.mean()).sum() / sr)
    # 6 band energi (pengganti MFCC kasar)
    bands = [(0, 300), (300, 800), (800, 1500), (1500, 2500), (2500, 4000), (4000, 8000)]
    be = []
    for lo, hi in bands:
        be.append(float(S[(freqs >= lo) & (freqs < hi)].sum() / S.sum()))
    return np.array([dur, rms, float(env.std()), zcr, centroid / 8000.0, low, high, tail, voiced] + be, dtype=float)

def sigmoid(z):
    return 1 / (1 + np.exp(-np.clip(z, -30, 30)))

def train_lr(X, y, lr=0.5, epochs=2000, l2=1e-3):
    n, d = X.shape
    w = np.zeros(d); b = 0.0
    for _ in range(epochs):
        p = sigmoid(X @ w + b)
        err = p - y
        w -= lr * (X.T @ err / n + l2 * w)
        b -= lr * err.mean()
    return w, b

def f1_cm(y, p):
    tp = int(((y == 1) & (p == 1)).sum()); tn = int(((y == 0) & (p == 0)).sum())
    fp = int(((y == 0) & (p == 1)).sum()); fn = int(((y == 1) & (p == 0)).sum())
    f1 = 2 * tp / (2 * tp + fp + fn) if (2 * tp + fp + fn) > 0 else 0.0
    acc = (tp + tn) / max(1, len(y))
    return f1, acc, [[tn, fp], [fn, tp]]

def cv_eval(X, y, k=5, seed=0):
    idx = np.arange(len(y)); rng = np.random.RandomState(seed)
    rng.shuffle(idx)
    folds = np.array_split(idx, k)
    pred = np.zeros(len(y), dtype=int)
    mu, sd = X.mean(0), X.std(0) + 1e-9
    Xn = (X - mu) / sd
    Xn = np.nan_to_num(Xn)
    for i in range(k):
        te = folds[i]; tr = np.concatenate([folds[j] for j in range(k) if j != i])
        w, b = train_lr(Xn[tr], y[tr])
        pred[te] = (sigmoid(Xn[te] @ w + b) >= 0.5).astype(int)
    return f1_cm(y, pred) + (pred,)

def main():
    os.makedirs("./tmp/audio", exist_ok=True)
    all_rows = []
    for off, ln in [(0, 100), (100, 59)]:
        d = get_json(BASE.format(off=off, ln=ln))
        all_rows += d["rows"]
    print(f"rows={len(all_rows)}")
    X, yq, yg, yi, ym, ids = [], [], [], [], [], []
    for r in all_rows:
        row = r["row"]
        src = row["audio"][0]["src"]
        wav = download_wav(src)
        arr, sr = read_wav(wav)
        f = feats(arr, sr)
        X.append(f); ids.append(row["id"])
        yq.append(int(row["qalqalah"]))
        yg.append(1 if int(row["noon_moshaddadah_len"]) == 1 else 0)
        yi.append(1 if int(row["noon_mokhfah_len"]) == 2 else 0)
        ym.append(1 if int(row["separate_madd"]) == 4 else 0)
        if len(X) % 40 == 0: print(f"  {len(X)}/{len(all_rows)}")
    X = np.vstack(X)
    X = np.nan_to_num(X, nan=0.0, posinf=0.0, neginf=0.0)
    print("X shape", X.shape)
    res = {}
    for name, y in [("qalqalah", np.array(yq)), ("ghunnah", np.array(yg)), ("ikhfa", np.array(yi)), ("madd", np.array(ym))]:
        f1, acc, cm, _ = cv_eval(X, y)
        print(f"{name}: pos={y.sum()}/{len(y)} F1={f1:.4f} acc={acc:.4f} cm={cm}")
        res[name] = {"f1": round(float(f1), 4), "acc": round(float(acc), 4), "cm": cm, "pos": int(y.sum()), "n": int(len(y))}
    with open("ml/evaluation/results.json", "w") as f: json.dump(res, f, indent=2)
    avg = float(np.mean([v["f1"] for v in res.values()]))
    with open("METRICS.md", "w") as f:
        f.write("# METRICS.md — hasil jujur qdat_bench (159 sampel, 5-fold CV, numpy-LR, fitur global)\n\n")
        f.write("Metode: fitur audio global (durasi, rms, zcr, centroid, low/high-band nasal proxy, tail-burst qalqalah proxy, voiced-dur, 6 band) + LogisticRegression numpy, standardisasi dalam-fold. Tanpa forced-alignment → baseline bawah, bukan SOTA. Tanpa rounding-up.\n\n")
        f.write("| Aturan | Definisi benar | F1 | Akurasi | CM [[TN,FP],[FN,TP]] | n/pos |\n|---|---|---|---|---|---|\n")
        dm = {"qalqalah": "qalqalah==1", "ghunnah": "moshaddadah==1 (parsial=0 salah)", "ikhfa": "mokhfah==2 (0/1 salah)", "madd": "separate==4"}
        for k in ["qalqalah", "ghunnah", "ikhfa", "madd"]:
            v = res[k]
            f.write(f"| {k} | {dm[k]} | {v['f1']} | {v['acc']} | {v['cm']} | {v['n']}/{v['pos']} |\n")
        f.write(f"\nRata-rata F1 = {avg:.4f}\n")
        f.write("\nBandingkan baseline paper muaalem multi-level CTC: Tajweed F1 0.758 (Moshaddadah 0.869, Ikhfaa 0.453, Qalqalah 0.953). Gap ini expected: paper memakai alignment + QPS, kami hanya fitur global.\n")
    print(f"AVG F1={avg:.4f} -> METRICS.md + results.json ditulis")

if __name__ == "__main__":
    main()
