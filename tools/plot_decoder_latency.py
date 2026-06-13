import csv
import os
import re
import sys

import matplotlib
import matplotlib.pyplot as plt
import pandas as pd

matplotlib.rcParams.update({
    "font.family": "serif",
    "font.size": 9,
    "axes.labelsize": 9,
    "axes.titlesize": 10,
    "legend.fontsize": 8,
    "xtick.labelsize": 8,
    "ytick.labelsize": 8,
    "figure.dpi": 150,
    "pdf.fonttype": 42,
    "ps.fonttype": 42,
})

SCHEMES = ["UEG", "ECB", "Huffman", "HuffmanPos"]
COLORS = {"UEG": "#1f77b4", "ECB": "#ff7f0e", "Huffman": "#2ca02c", "HuffmanPos": "#9467bd"}
MARKERS = {"UEG": "o", "ECB": "s", "Huffman": "^", "HuffmanPos": "D"}


def parse_scheme(name):
    prefix = name.split("(")[0]
    return prefix if prefix in SCHEMES else None


def parse_source(source):
    m = re.match(r"(dct|kodak)_Q(\d+)", source)
    if m:
        return m.group(1), int(m.group(2))
    return None, None


def load_data(csv_path):
    rows = []
    with open(csv_path, newline="") as f:
        reader = csv.reader(f)
        next(reader)
        for parts in reader:
            if len(parts) == 10:
                fields = parts
            elif len(parts) > 10:
                # legacy unquoted CSV
                source = parts[0]
                tail = parts[-8:]
                scheme = ",".join(parts[1:-8])
                fields = [source, scheme] + tail
            else:
                continue
            source, scheme, _, _, _, _, _, dec_ms, _, _ = fields
            rows.append({
                "source": source,
                "scheme": scheme,
                "dec_ms": float(dec_ms),
            })
    df = pd.DataFrame(rows)
    df["scheme_short"] = df["scheme"].apply(parse_scheme)
    parsed = df["source"].apply(parse_source)
    df["image_type"] = parsed.apply(lambda x: x[0])
    df["Q"] = parsed.apply(lambda x: x[1])
    df = df[df["image_type"].isin(["dct", "kodak"])]
    df = df.dropna(subset=["Q", "scheme_short"])

    m_per_cell = {}
    for _, row in df[df["scheme_short"] == "ECB"].iterrows():
        match = re.search(r"m=(\d+)", row["scheme"])
        if match:
            m_per_cell[(row["image_type"], int(row["Q"]))] = int(match.group(1))
    df["m"] = df.apply(lambda r: m_per_cell.get((r["image_type"], int(r["Q"]))), axis=1)
    df = df.dropna(subset=["m"])
    df["m"] = df["m"].astype(int)
    df["Q"] = df["Q"].astype(int)
    return df


def plot(df, output_path):
    fig, axes = plt.subplots(1, 2, figsize=(7.0, 3.2), sharey=False)

    panels = [
        ("dct", "Procedural ($N{=}65{,}536$ per trial)"),
        ("kodak", "Kodak ($N{=}393{,}216$ per trial)"),
    ]

    for ax, (image_type, title) in zip(axes, panels):
        sub = df[df["image_type"] == image_type].sort_values("m")
        for scheme in SCHEMES:
            s = sub[sub["scheme_short"] == scheme]
            ax.plot(s["m"], s["dec_ms"],
                    color=COLORS[scheme], marker=MARKERS[scheme],
                    label=scheme, linewidth=1.5, markersize=5)
        ax.set_xscale("log")
        ax.set_yscale("log")
        ax.set_xlabel(r"Alphabet size $m$")
        ax.set_ylabel(r"Decoder time per trial (ms)")
        ax.set_title(title)
        ax.grid(True, which="major", alpha=0.3, linewidth=0.5)
        ax.grid(True, which="minor", alpha=0.15, linewidth=0.4)
        ax.legend(loc="upper left", frameon=True, framealpha=0.95)

        # slope-1 reference anchored to ECB at smallest m
        m_vals = sorted(sub["m"].unique())
        m_lo, m_hi = m_vals[0], m_vals[-1]
        ecb_lo = sub[(sub["scheme_short"] == "ECB") & (sub["m"] == m_lo)]["dec_ms"].iloc[0]
        ref_x = [m_lo, m_hi]
        ref_y = [ecb_lo, ecb_lo * (m_hi / m_lo)]
        ax.plot(ref_x, ref_y, color="gray", linestyle=":", linewidth=0.9, zorder=0)

    plt.tight_layout()
    plt.savefig(output_path, bbox_inches="tight")
    plt.close()
    print("wrote " + output_path)


if __name__ == "__main__":
    csv_path = sys.argv[1] if len(sys.argv) > 1 else "benchmark-results.csv"
    out_dir = "figures"
    os.makedirs(out_dir, exist_ok=True)
    df = load_data(csv_path)
    plot(df, os.path.join(out_dir, "fig3_decoder_latency.pdf"))