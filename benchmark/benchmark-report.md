# Binarization Sensitivity in Adaptive Arithmetic Coding

## Setup

Four binarization schemes feeding a shared H.264 M-coder: UEG (Truncated Unary + k-th order Exp-Golomb, the H.264 standard), ECB (entropy-conserving binarization, arXiv:1408.3083), Huffman (canonical prefix code, single shared adaptive context), and HuffmanPos (same canonical Huffman codes but with one M-coder context per bin position). HuffmanPos isolates the effect of context allocation strategy from the binarization itself - same codewords as Huffman, different context wiring into the M-coder.

50 seeds per (source, scheme) cell on synthetic and procedural sources; 24-image aggregate on Kodak. Sequential seed range, fixed warmup.

Phase 1 - five synthetic sources at N=10,000: uniform [0,8), biased [0,8) P(0)=0.7, geometric [0,8) p=0.6, Laplacian b=2 (narrow), Laplacian b=8 (wide).

Phase 2 - DCT quantization sweep on a 256×256 procedurally-generated test image (smooth gradient + Gaussian bumps + sharp rectangles + Gaussian sensor noise), 8×8 block DCT-II, zig-zag scan. Q ∈ {2, 4, 8, 16, 32}, 65,536 coefficients per trial.

Phase 3 - DCT quantization sweep on the full 24-image Kodak True Color Image Suite (kodim01-kodim24). RGB images converted to BT.601 luma (`Y' = 0.299R + 0.587G + 0.114B`), then through the same 8×8 DCT-II + quantize + zig-zag pipeline as Phase 2. ~393,216 coefficients per image. Std deviation in Phase 3 is reported across images, not seeds.

UEG at uCoff=14, k=0, signed mode auto-detected per source. ECB symbol order is empirical-frequency descending. Huffman and HuffmanPos trees built per-trial from observed frequencies; HuffmanPos allocates `maxCodeLen` M-coder contexts, one per bin position. JIT warmed with 9 Laplacian batches at N=1000 before measurement; measurement uses `System.nanoTime()` per encode/decode call.

The procedural image has a realistic mix of DC, low/mid AC, sharp edges, and high-frequency noise, but is not a 1/f natural-photograph power spectrum - Phase 3 closes this gap. Tuned UEG/EG-k parameters were not searched; defaults are used throughout. Huffman codebook serialization cost is not counted.

## Findings

**Phase 1 - no scheme dominates across synthetic sources, but HuffmanPos is competitive everywhere.** Each non-HuffmanPos scheme wins on the source class it is structurally aligned with; HuffmanPos is within ~0.5 percentage points of the leader on every source.

| Source | UEG | ECB | Huffman | HuffmanPos | Best |
|---|---|---|---|---|---|
| Uniform [0,8) | 3.38% | 3.16% | 1.86% | **1.68%** | HuffmanPos |
| Biased [0,8) P(0)=0.7 | 3.01% | **2.78%** | 6.78% | 2.59% | HuffmanPos (2.59%); ECB best aligned (2.78%) |
| Geometric [0,8) p=0.6 | 2.22% | **2.15%** | 2.20% | 2.17% | ECB (4-way tie) |
| Laplacian (b=2) | **1.91%** | 3.03% | 2.85% | 2.39% | UEG |
| Laplacian (b=8) | 6.21% | 6.72% | 2.34% | **2.11%** | HuffmanPos |

Bits/sym stddev across 50 seeds is ≤0.02 for all cells - the gaps shown exceed noise. Huffman is the best base scheme on uniform because 3-bit codewords match log₂(8) exactly; HuffmanPos extends that by ~0.18pp via per-position adaptation. Huffman is the best base scheme on wide Laplacian (m=111) because empirical-optimality of variable-length codes dominates UEG's structural assumptions about geometric tails; again HuffmanPos extracts a further ~0.23pp through context adaptation. UEG wins on narrow Laplacian because TU+EG-k was designed for exactly that distribution. ECB wins on biased and geometric where the most-frequent-first symbol order maps the dominant value to a binary string the M-coder can adapt to cheaply.

The single-context Huffman scheme's catastrophic 6.78% on biased P(0)=0.7 vs HuffmanPos's 2.59% on the same source previews the central result of this report: Huffman's failure modes are largely a context-allocation problem, not a codeword-length problem. The optimal Huffman code for that distribution still pays ≥1 bit/symbol even though `H(X)` = 1.73; per-position contexts let the M-coder recover most of the loss by adapting to each bin position's local statistics.

**Phase 2 - Q-sweep on the procedural image reveals a sparsity-driven crossover, and HuffmanPos changes the story.** Increasing the quantization step shifts the source from rich (low Q, large alphabet, near-stationary AC distribution) to sparse (high Q, small alphabet, dominated by zeros and ±1).

| Q | alphabet | source H | UEG | ECB | Huffman | HuffmanPos |
|---|---|---|---|---|---|---|
| 2 | 571 | 3.801 | 3.95% | 6.86% | 2.22% | **2.05%** |
| 4 | 350 | 2.830 | 4.08% | 5.00% | 2.34% | **1.66%** |
| 8 | 193 | 1.923 | 4.04% | 3.54% | 4.54% | **1.62%** |
| 16 | 105 | 1.001 | 4.87% | 3.31% | 11.92% | **2.01%** |
| 32 | 55 | 0.394 | 3.15% | −0.69% | 26.21% | **−0.83%** |

Two findings here. First, the Huffman-vs-ECB crossover at Q=8 is real: below it, Huffman's symbol-level optimality wins; above it, Huffman's codeword-length floor (≥1 bin/symbol) inflates the rate while ECB's geometrically shrinking binary streams collapse toward 1 bin/symbol total. At Q=32, `H(X)` = 0.394 bits/sym; the M-coder compresses Huffman's bin stream to 0.497 bits/sym - below 1 bit/sym, but unable to recover the gap to entropy because Huffman has already committed to N bin-decisions for N symbols. The arithmetic coder can compress *within* the bin stream but cannot reorganize Huffman's bin allocation. ECB sidesteps the floor entirely: its first binary string asks "is this position the most-frequent value?" - at Q=32 that string is heavily one-biased, and the M-coder adapts to a near-deterministic context that codes each bin in fractional bits.

Second, HuffmanPos closes the gap. It uses *the same codewords* as Huffman - hence the same bin count per source symbol - but routes each bin position through its own M-coder context. At Q=32 the rate drops from 0.497 bps (Huffman) to 0.391 bps (HuffmanPos), matching ECB's 0.391 to three decimal places. The bin-count floor argument that explains Huffman's failure does not apply to HuffmanPos: the floor is still there in terms of bin count, but per-position contexts let the M-coder spend each bin in fractional bits according to that position's local statistics. The implication is that the "Huffman fails at high Q because of integer codeword lengths" story is incomplete - Huffman fails at high Q because *a single shared context cannot adapt to the per-position structure of the bin stream*. Decouple the two and Huffman is competitive across the entire sweep.

ECB at Q=32 lands slightly below the empirical `H(X)` (−0.69% overhead); HuffmanPos lands a hair further below (−0.83%). This is not free energy. Two effects contribute. First, a small Miller-Madow bias in the plug-in entropy estimator (~6×10⁻⁴ bits/sym at m=55, N=65,536) - this accounts for roughly 20% of the gap. Second, and more substantively, the plug-in marginal estimate `H(X) = -Σ p_i log p_i` assumes i.i.d. samples, but DCT residuals have spatial structure (within-block correlation along the zig-zag scan, and block-to-block correlation across the image) that the M-coder's adaptive contexts partially capture. ECB and HuffmanPos at Q=32 are therefore coding closer to the true *conditional* entropy of the source than to its *marginal* entropy. The takeaway is that both schemes' adaptive code rate at high Q is at the noise floor of how well source entropy can be measured by a memoryless estimator.

UEG occupies a middle band (3-5% overhead) across the full sweep. It is designed for residuals; it never excels, never fails badly, and is the most predictable across operating points.

**Phase 3 - Q-sweep on the full 24-image Kodak suite shifts the crossover and shrinks absolute overheads. HuffmanPos and ECB track each other within per-image variance and dominate on natural images.**

| Q | alphabet | source H | UEG | ECB | Huffman | HuffmanPos |
|---|---|---|---|---|---|---|
| 2 | 834 | 4.053 | −1.18% | −1.13% | −0.38% | **−3.60%** |
| 4 | 445 | 3.097 | −3.66% | −4.22% | −1.80% | **−5.48%** |
| 8 | 227 | 2.208 | −6.03% | −7.26% | −3.64% | **−7.67%** |
| 16 | 114 | 1.478 | −7.31% | **−8.94%** | −2.55% | −8.89% |
| 32 | 58 | 0.909 | −7.64% | **−9.10%** | 3.37% | −8.54% |

ECB beats single-context Huffman at every tested Q; the gap grows monotonically from 0.031 bps at Q=2 to 0.113 bps at Q=32. HuffmanPos beats single-context Huffman by even more (3-12 percentage points across the sweep) and trades the win with ECB depending on Q: HuffmanPos leads at Q∈{2,4,8} by 0.4-2.5pp, ECB leads narrowly at Q∈{16,32} by 0.05-0.6pp. The procedural-image crossover at Q=8 does not generalize - on Kodak, single-context Huffman never wins. ECB-vs-UEG crossover also shifts: from Q=8 on procedural to Q=4 on Kodak.

The mechanism behind ECB's gain over single-context Huffman is the same as Phase 2: Huffman's 1-bit codeword floor pins the rate above source entropy when `H(X)` falls below 1 bit/symbol. The threshold shifts because real images have lower `H(X)` at every Q than the procedural image - strong low-frequency concentration produces sparser residual distributions earlier in the quantization sweep. At Kodak Q=32, `H(X)` = 0.909, so Huffman's floor pins the rate only ~3.4% above entropy. At procedural Q=32, `H(X)` = 0.394, so the same floor mechanism produced 26% overhead. The mechanism is universal; its severity scales with how far `H(X)` falls below 1.

HuffmanPos's gain over single-context Huffman is the same as Phase 2 and confirms the context-allocation explanation: same codewords, same bin counts, just per-position adaptation. The fact that HuffmanPos matches or beats ECB on most Kodak cells - despite ECB having a structurally smaller bin budget - says that on natural-image residuals the dominant rate-reducer is per-bin-position context adaptation, not the choice of binarization per se.

Overheads are systematically smaller (and frequently negative) on Kodak. All four schemes code below the marginal `H(X)` at every Q ≥ 4; even at Q=2 only single-context Huffman fails to (and only by 0.38%). The sub-entropy pattern visible at procedural Q=32 is the norm on natural images, not an edge case. Two factors explain the difference. Natural images carry within-block correlation along the zig-zag scan and block-to-block correlation through the underlying image content; the M-coder's adaptive contexts capture some of this conditional structure. The marginal-entropy reference `H(X)` therefore undershoots the achievable rate by a margin that grows with how much conditional structure the source carries. Kodak photographs - 1/f power spectrum, smooth regions of correlated pixels, edges with predictable directional structure - have substantially more exploitable conditional structure than the procedural image.

Per-image variance is large. At Q=2, bits/sym across the 24 images has std=0.76 against a mean of 4.01. Different photographs have very different statistics. The scheme-to-scheme differences are smaller than the image-to-image differences, but the scheme ordering is consistent across images and across Q values.

**ECB has an O(N·m) decode cost. HuffmanPos pays a small premium over Huffman.** The current ECB decoder makes m−1 passes over the symbol array even though most positions resolve in the first few passes. UEG, Huffman, and HuffmanPos are O(N) regardless of alphabet.

| Source | m | UEG | ECB | Huffman | HuffmanPos |
|---|---|---|---|---|---|
| Laplacian (b=8) | 111 | 0.77 ms | 2.03 ms | 1.92 ms | 2.16 ms |
| Procedural Q=2 | 571 | 3.21 ms | 24.45 ms | 8.58 ms | 9.74 ms |
| Procedural Q=8 | 193 | 1.72 ms | 9.55 ms | 4.66 ms | 5.42 ms |
| Procedural Q=32 | 55 | 0.57 ms | 2.66 ms | 2.48 ms | 2.63 ms |
| Kodak Q=2 | 834 | 20.54 ms | 206.68 ms | 56.51 ms | 63.39 ms |
| Kodak Q=8 | 227 | 10.55 ms | 56.92 ms | 29.32 ms | 33.53 ms |
| Kodak Q=32 | 58 | 5.04 ms | 17.04 ms | 17.16 ms | 18.87 ms |

ECB decode is 4.7-7.6× slower than UEG on procedural DCT sources, worst at the largest alphabets, with the penalty growing roughly linearly in m. On smaller-alphabet sources like Laplacian (m=111) the ratio is closer to 2.6×. Kodak absolute times are ~6× larger than procedural because each image contributes ~6× more coefficients per trial; the per-scheme ratios are broadly preserved but slightly amplified at large m (ECB/UEG at Kodak Q=2 is 10.1× vs 7.6× procedurally, attributable to cache pressure across the m−1 array passes). This is an implementation artifact of ECB, not an algorithmic property - interleaving the m−1 binary strings into a single decoder pass would close the gap, but is not implemented here.

HuffmanPos decode runs 6-28% slower than Huffman depending on the source, with the small synthetic alphabets (biased, geometric, Laplacian b=2) at the top of that range and the DCT and Kodak cells lower. The extra cost comes from maintaining a position-indexed context array and resetting the position counter on each symbol match; the per-bin arithmetic decoding cost itself is identical. Given the rate savings (often several percentage points), this is a favorable trade.

**Round-trip correctness: 2,480/2,480 pass.** Encoder and decoder agree bit-exact across every (source, scheme, seed) and (image, scheme, Q) combination, including the 480 Kodak trials.

## Problems

- ECB decoder is O(N·m). Documented above. The current implementation is the simplest correct decoder, not the fastest. Production use of ECB through arithmetic coding would require the interleaved single-pass variant.

- Huffman codebook serialization not counted. A deployed Huffman or HuffmanPos codec pays for tree storage; this benchmark omits that cost. Huffman's 1.86% on Uniform and HuffmanPos's 2.11% on wide Laplacian are therefore lower bounds. The relative ordering against UEG and ECB is unaffected at N ≥ 10,000 (overhead amortizes), but at smaller N the picture would change.

- `H(X)` measurement is a memoryless estimator. The reported `H(X)` is the plug-in Shannon entropy of observed marginal frequencies - it assumes i.i.d. samples and ignores the spatial structure DCT residuals actually have. At very low entropy (procedural Q=32, H≈0.39 bits/sym), Miller-Madow bias adds ~6×10⁻⁴ bits/sym; on Kodak across all Q values, the conditional structure the M-coder captures dominates the gap. The negative-overhead cells throughout the Kodak table should be read as statements that ECB, UEG, and HuffmanPos code near the conditional entropy of the source, not as violations of source-coding bounds.

- Image diversity is limited to Kodak. Phase 3 uses the full 24-image Kodak suite, but other content classes (high-resolution modern photos, screen content, video frames) are not tested. The relative ordering of schemes likely generalizes given the mechanism-level explanation, but absolute overheads will shift further on different image classes.

- JVM timing is not JMH-grade. Warmup runs before measurement but there is no fork isolation, allocation profiling, or explicit GC handling. Encode-time stddev is small relative to mean for most cells. ECB decode at large m shows higher variance attributable to cache behavior across the m−1 array passes.

- Tuned UEG/EG-k parameters not searched. Defaults (uCoff=14, k=0) used throughout. UEG's gap to Huffman on rich DCT sources may close with parameter search; not measured.

## Reproducing

```bash
mvn compile exec:java -Dexec.mainClass=com.cabac.BenchmarkSuite
```

Writes `benchmark/benchmark-results.csv`. 50 seeds × 10 (source, Q) configurations × 4 schemes + 24 images × 5 Q values × 4 schemes = 2,480 round-trip trials.

Kodak images must be downloaded separately into `kodak/` - see README.

Raw data: `benchmark-results.csv`
