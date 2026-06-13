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


def load_dct_data(csv_path):
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
            source, scheme, _, entropy, bps_mean, _, _, _, _, _ = fields
            rows.append({
                "source": source,
                "scheme": scheme,
                "entropy": float(entropy),
                "bps_mean": float(bps_mean),
            })
    df = pd.DataFrame(rows)
    df["eta_pct"] = (df["bps_mean"] - df["entropy"]) / df["entropy"] * 100
    df["scheme_short"] = df["scheme"].apply(parse_scheme)
    parsed = df["source"].apply(parse_source)
    df["image_type"] = parsed.apply(lambda x: x[0])
    df["Q"] = parsed.apply(lambda x: x[1])
    df = df[df["image_type"].isin(["dct", "kodak"])]
    df = df.dropna(subset=["Q", "scheme_short"])
    df["Q"] = df["Q"].astype(int)
    return df


def plot(df, output_path):
    fig, axes = plt.subplots(1, 2, figsize=(7.0, 3.1), sharey=False)

    panels = [
        ("dct", "Procedural image"),
        ("kodak", "Kodak natural photographs"),
    ]

    for ax, (image_type, title) in zip(axes, panels):
        sub = df[df["image_type"] == image_type]
        for scheme in SCHEMES:
            s = sub[sub["scheme_short"] == scheme].sort_values("Q")
            ax.plot(
                s["Q"], s["eta_pct"],
                color=COLORS[scheme], marker=MARKERS[scheme],
                label=scheme, linewidth=1.5, markersize=5,
            )
        ax.axhline(0, color="gray", linewidth=0.6, linestyle="--", zorder=0)
        ax.set_xscale("log", base=2)
        ax.set_xticks([2, 4, 8, 16, 32])
        ax.set_xticklabels(["2", "4", "8", "16", "32"])
        ax.set_xlabel(r"Quantization step $Q$")
        ax.set_ylabel(r"Coding overhead $\eta$ (%)")
        ax.set_title(title)
        ax.grid(True, alpha=0.3, linewidth=0.5)
        ax.legend(loc="best", frameon=True, framealpha=0.95)

    # crossover
    axes[0].annotate(
        "Huffman/ECB\ncrossover",
        xy=(8, 4.0), xytext=(11, 15),
        fontsize=8,
        ha="left",
        arrowprops=dict(arrowstyle="->", lw=0.7, color="black"),
    )

    plt.tight_layout()
    plt.savefig(output_path, bbox_inches="tight")
    plt.close()
    print("wrote " + output_path)


if __name__ == "__main__":
    csv_path = sys.argv[1] if len(sys.argv) > 1 else "benchmark-results.csv"
    out_dir = "figures"
    os.makedirs(out_dir, exist_ok=True)
    df = load_dct_data(csv_path)
    plot(df, os.path.join(out_dir, "fig1_eta_vs_q.pdf"))