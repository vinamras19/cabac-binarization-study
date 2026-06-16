# CABAC Binarization Study

Implementation of Context-Adaptive Binary Arithmetic Coding (CABAC) - the entropy coder of H.264/AVC, and the basis for the entropy coders of HEVC and VVC - with four pluggable binarization schemes feeding a shared M-coder. Tests the entropy-conserving binarization scheme from [arXiv:1408.3083](https://arxiv.org/abs/1408.3083) in a full CABAC pipeline against the H.264 standard (UEG: Truncated Unary + k-th order Exp-Golomb), canonical Huffman with a single shared M-coder context (Huffman), and canonical Huffman with one context per bin position (HuffmanPos), on synthetic distributions, a procedurally generated test image, and the full Kodak True Color Image Suite.

## Background

CABAC decomposes a multi-symbol value into a sequence of binary decisions (bins), each routed through an adaptive binary arithmetic coder. The choice of decomposition - the *binarization scheme* - and the *context allocation* over the resulting bin stream jointly determine how well per-bin probability models can adapt and how close the coding rate gets to the source entropy `H(X)`. Standard CABAC uses TU + EG-k; this project measures it against entropy-conserving binarization (m-ary data → m-1 binary strings, total entropy provably preserved) and canonical Huffman with two context allocation strategies (single shared context vs one context per bin position) on the same M-coder backend. The two Huffman variants share codewords but differ in context wiring.

## Project Structure
```text
src/main/java/com/cabac/
  Tables.java              - H.264 Section 9 probability tables
  ContextModel.java        - (stateIdx, valMPS) state machine
  BitWriter.java           - MSB-first bit output
  BitReader.java           - MSB-first bit input
  ArithmeticEncoder.java   - M-coder encoder (regular, bypass, terminate)
  ArithmeticDecoder.java   - M-coder decoder

  Binarization.java        - TU, FL, EG-k primitives
  Binarizer.java           - Pluggable binarization interface
  UEGBinarizer.java        - Standard CABAC binarization (TU + EG-k)
  ECBinarizer.java         - Entropy-conserving binarization
  HuffmanBinarizer.java    - Canonical Huffman, single shared context
  HuffmanPosBinarizer.java - Canonical Huffman, one context per bin position

  AlphabetTable.java       - Unique values, frequency ordering
  Source.java              - Synthetic sources (uniform, biased, geometric, Laplacian)
  DctPipeline.java         - Shared DCT-II + quantize + zig-zag pipeline
  DctSource.java           - Procedural image -> DCT pipeline
  KodakDctSource.java      - Real RGB image (PNG) -> BT.601 luma -> DCT pipeline
  Stats.java               - Empirical Shannon entropy
  BenchmarkRunner.java     - Single-run shootout
  BenchmarkSuite.java      - Statistical benchmark, writes CSV

src/test/java/com/cabac/
  CabacTest.java           - Unit tests

benchmark/
  benchmark-results.csv    - Per-cell aggregates from BenchmarkSuite
  benchmark-report.md      - Findings and mechanism analysis

figures/
  fig1_eta_vs_q.pdf
  fig2_floor_mechanism.pdf
  fig3_decoder_latency.pdf

tools/                     - Python plot scripts (matplotlib)
kodak/                     - Real test images (download separately, see below)
```

## Getting Started

### Prerequisites

* Java 17+
* Maven 3.6+

### Build
```text
mvn clean package
```

### Run

Single-run shootout (default main class):
```text
mvn exec:java
```

50-seed statistical benchmark (writes `benchmark/benchmark-results.csv`):
```text
mvn exec:java -Dexec.mainClass=com.cabac.BenchmarkSuite
```

Regenerate figures from CSV:
```text
python3 tools/plot_eta_vs_q.py benchmark/benchmark-results.csv
python3 tools/plot_floor_mechanism.py benchmark/benchmark-results.csv
python3 tools/plot_decoder_latency.py benchmark/benchmark-results.csv
```

### Kodak Real-Image Testing

The benchmark suite includes a Q-sweep on the full 24-image Kodak True Color Image Suite to evaluate the binarization schemes on natural photographs in addition to the procedural test image.

Download the suite from <http://r0k.us/graphics/kodak/> and place `kodim01.png` through `kodim24.png` in `kodak/` (relative to project root).

Alternative path: override the directory with `-Dkodak.dir=/path/to/kodak`.

If Kodak images are not present, the benchmark prints a `Skipping <filename>` warning and continues without them; the synthetic and procedural-DCT cells still run.

Images are loaded once, converted to BT.601 luma (`Y' = 0.299R + 0.587G + 0.114B`), cached, and reused across the Q-sweep.

## Verification

Each binarization is exercised through the same M-coder and verified to round-trip losslessly. The reported overhead isolates the binarization's contribution to coding inefficiency:
```text
overhead(X) = (encoded_bits(X) / N - H(X)) / H(X) × 100%

where N is the symbol count and H(X) is the empirical Shannon entropy.
```

## Findings

Mean overhead vs source entropy `H(X)` across 50 seeds, 10,000 symbols/trial. All cells round-trip 50/50.

| Source                      | UEG   | ECB   | Huffman | HuffmanPos |
|---|---|---|---|---|
| Uniform [0,8)               | 3.38% | 3.16% | 1.86% | **1.68%** |
| Biased [0,8) p₀=0.7         | 3.01% | **2.78%** | 6.78% | 2.59% |
| Geometric [0,8) p=0.6       | 2.22% | **2.15%** | 2.20% | 2.17% |
| Laplacian b=2               | **1.91%** | 3.03% | 2.85% | 2.39% |
| Laplacian b=8               | 6.21% | 6.72% | 2.34% | **2.11%** |

DCT residual sweep over quantization step Q on the procedural 256×256 test image (8×8 blocks, 65,536 coefficients/trial, 50 seeds):

| Q  | alphabet | H(X) | UEG   | ECB   | Huffman | HuffmanPos |
|---|---|---|---|---|---|---|
| 2  | 571 | 3.801 | 3.95% | 6.86% | 2.22% | **2.05%** |
| 4  | 350 | 2.830 | 4.08% | 5.00% | 2.34% | **1.66%** |
| 8  | 193 | 1.923 | 4.04% | 3.54% | 4.54% | **1.62%** |
| 16 | 105 | 1.001 | 4.87% | 3.31% | 11.92% | **2.01%** |
| 32 | 55  | 0.394 | 3.15% | −0.69% | 26.21% | **−0.83%** |

DCT residual sweep on the full 24-image Kodak suite (~393,216 coefficients per image; std reported across images):

| Q  | alphabet | H(X) | UEG   | ECB   | Huffman | HuffmanPos |
|---|---|---|---|---|---|---|
| 2  | 834 | 4.053 | −1.18% | −1.13% | −0.38% | **−3.60%** |
| 4  | 445 | 3.097 | −3.66% | −4.22% | −1.80% | **−5.48%** |
| 8  | 227 | 2.208 | −6.03% | −7.26% | −3.64% | **−7.67%** |
| 16 | 114 | 1.478 | −7.31% | **−8.94%** | −2.55% | −8.89% |
| 32 | 58  | 0.909 | −7.64% | **−9.10%** | 3.37% | −8.54% |

The winning scheme depends on quantization step, image content, and context allocation. On the procedural image, single-context Huffman dominates below the Q=8 crossover; ECB takes over at and above it, beating Huffman by 27 percentage points at Q=32. HuffmanPos wins across the entire procedural sweep using the *same* Huffman codewords as the losing baseline, just routed through per-position contexts - the bin-count floor that explains Huffman's failure does not apply once context allocation is decoupled from binarization. On Kodak natural photographs, ECB and HuffmanPos track each other within per-image variance and dominate single-context Huffman at every tested Q (the ECB-vs-Huffman gap grows monotonically from 0.031 to 0.113 bps).

The mechanism: Huffman's 1-bit codeword floor pins the rate above source entropy when `H(X)` drops below 1, *given a single shared context*. ECB sidesteps the floor structurally (geometrically shrinking m-1 binary streams). HuffmanPos sidesteps it via context allocation - same codewords, same bin count, but per-position contexts let the M-coder spend each bin in fractional bits according to that position's local statistics. The "Huffman fails at low entropy because of integer codeword lengths" story is incomplete; Huffman fails at low entropy because a single shared context cannot adapt to per-position bin structure.

Overheads on Kodak are smaller and often negative. Natural images carry within-block and between-block correlation that the M-coder's adaptive contexts capture; the marginal-entropy reference `H(X)` systematically undershoots the achievable rate, so HuffmanPos and ECB at Kodak Q=32 code 8-9% below `H(X)` (vs 0.7-0.8% below on the procedural image). UEG occupies a stable middle band in both regimes - never excelling, never failing catastrophically.

ECB pays for its rate efficiency on the decode side. At procedural Q=2 (m=571), decode takes 24.5ms vs 3.2ms for UEG - ~7.6× slower. On Kodak Q=2 (m=834, ~6× more coefficients per trial) the ratio amplifies to ~10.1×. HuffmanPos decode is 10-15% slower than Huffman across all sources (position-indexed context array overhead). Full analysis in [`benchmark/benchmark-report.md`](benchmark/benchmark-report.md).

## References

ITU-T (2003). *H.264: Advanced Video Coding for Generic Audiovisual Services* - Section 9. <https://www.itu.int/rec/T-REC-H.264>

Srivastava, M. (2014). *Entropy Conserving Binarization Scheme for Video and Image Compression*. arXiv preprint. <https://arxiv.org/abs/1408.3083>

## License

See `LICENSE` for more information.
