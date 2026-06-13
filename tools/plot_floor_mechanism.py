import csv
import os
import re
import sys

import matplotlib
import matplotlib.pyplot as plt
import pandas as pd
from matplotlib.lines import Line2D

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
    df["scheme_short"] = df["scheme"].apply(parse_scheme)
    parsed = df["source"].apply(parse_source)
    df["image_type"] = parsed.apply(lambda x: x[0])
    df["Q"] = parsed.apply(lambda x: x[1])
    df = df[df["image_type"].isin(["dct", "kodak"])]
    df = df.dropna(subset=["Q", "scheme_short"])
    return df


def plot(df, output_path):
    fig, ax = plt.subplots(figsize=(4.2, 3.4))

    h_grid = [0.3, 5.0]
    ax.plot(h_grid, h_grid, color="gray", linestyle=":", linewidth=0.9,
            label=r"$R = H(X)$", zorder=1)

    for scheme in SCHEMES:
        for image_type in ["dct", "kodak"]:
            s = df[(df["scheme_short"] == scheme) & (df["image_type"] == image_type)]
            if s.empty:
                continue
            label = scheme if image_type == "dct" else None
            face = "none" if image_type == "dct" else COLORS[scheme]
            ax.scatter(
                s["entropy"], s["bps_mean"],
                marker=MARKERS[scheme],
                facecolor=face, edgecolor=COLORS[scheme],
                s=45, linewidth=1.3,
                label=label, zorder=3,
            )

    extra = [
        Line2D([0], [0], marker="o", color="w", markerfacecolor="none",
               markeredgecolor="gray", markersize=7, label="Procedural"),
        Line2D([0], [0], marker="o", color="w", markerfacecolor="gray",
               markeredgecolor="gray", markersize=7, label="Kodak"),
    ]
    handles, labels = ax.get_legend_handles_labels()
    ax.legend(handles + extra, labels + ["Procedural", "Kodak"],
              loc="upper left", frameon=True, framealpha=0.95, fontsize=7.5)

    ax.set_xscale("log")
    ax.set_yscale("log")
    ax.set_xlabel(r"Source entropy $H(X)$ (bits/symbol)")
    ax.set_ylabel(r"Encoded rate $R(X)$ (bits/symbol)")
    ax.set_xlim(0.3, 5)
    ax.set_ylim(0.3, 5)
    ax.set_xticks([0.5, 1, 2, 5])
    ax.set_yticks([0.5, 1, 2, 5])
    ax.set_xticklabels(["0.5", "1", "2", "5"])
    ax.set_yticklabels(["0.5", "1", "2", "5"])
    ax.set_aspect("equal", adjustable="box")
    ax.grid(True, which="major", alpha=0.3, linewidth=0.5)
    ax.grid(True, which="minor", alpha=0.15, linewidth=0.4)

    # Q=32 procedural - Huffman departs from R=H line (bin-count floor)
    proc_huff = df[(df["scheme_short"] == "Huffman")
                   & (df["image_type"] == "dct") & (df["Q"] == 32)]
    if not proc_huff.empty:
        x = proc_huff["entropy"].iloc[0]
        y = proc_huff["bps_mean"].iloc[0]
        ax.annotate(
            "Huffman Q=32\nbin-count floor",
            xy=(x, y), xytext=(1.1, 0.9),
            fontsize=7.5,
            ha="left",
            arrowprops=dict(arrowstyle="->", lw=0.6, color="black"),
        )

    # Q=32 procedural - HuffmanPos sits on R=H line at the same H(X)
    proc_hpos = df[(df["scheme_short"] == "HuffmanPos")
                   & (df["image_type"] == "dct") & (df["Q"] == 32)]
    if not proc_hpos.empty:
        x = proc_hpos["entropy"].iloc[0]
        y = proc_hpos["bps_mean"].iloc[0]
        ax.annotate(
            "HuffmanPos Q=32\nsame H(X), on R=H line",
            xy=(x, y), xytext=(1.1, 0.42),
            fontsize=7.5,
            ha="left",
            arrowprops=dict(arrowstyle="->", lw=0.6, color="black"),
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
    plot(df, os.path.join(out_dir, "fig2_floor_mechanism.pdf"))