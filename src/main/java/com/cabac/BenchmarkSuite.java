package com.cabac;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class BenchmarkSuite {

    private static final int SEEDS = 50;
    private static final int N = 10000;

    private static final int N_SCHEMES = 4;

    private static final String[] KODAK_IMAGES = {
        "kodim01.png", "kodim02.png", "kodim03.png", "kodim04.png",
        "kodim05.png", "kodim06.png", "kodim07.png", "kodim08.png",
        "kodim09.png", "kodim10.png", "kodim11.png", "kodim12.png",
        "kodim13.png", "kodim14.png", "kodim15.png", "kodim16.png",
        "kodim17.png", "kodim18.png", "kodim19.png", "kodim20.png",
        "kodim21.png", "kodim22.png", "kodim23.png", "kodim24.png"
    };

    public static void main(String[] args) throws Exception {
        System.out.println("=== Statistical Benchmark Suite ===");
        System.out.printf("seeds per cell: %d, samples per trial: %d%n%n", SEEDS, N);

        // jit warmup
        warmup();

        List<Row> rows = new ArrayList<>();

        rows.addAll(runSource("uniform", false, (s) -> Source.uniform(8, N, s)));
        rows.addAll(runSource("biased_p0_0.7", false, (s) -> Source.biased(8, 0.7, N, s)));
        rows.addAll(runSource("geometric_p_0.6", false, (s) -> Source.geometric(8, 0.6, N, s)));
        rows.addAll(runSource("laplacian_b2", true, (s) -> Source.laplacian(2.0, N, s)));
        rows.addAll(runSource("laplacian_b8", true, (s) -> Source.laplacian(8.0, N, s)));

        // q-sweep
        for (int q : new int[]{2, 4, 8, 16, 32}) {
            String label = "dct_Q" + q;
            rows.addAll(runSource(label, true, (s) -> DctSource.generate(q, s)));
        }

        System.out.println("\n--- kodak q-sweep ---");
        System.out.println("images: " + String.join(", ", KODAK_IMAGES));
        for (int q : new int[]{2, 4, 8, 16, 32}) {
            rows.addAll(runKodakSweep(q));
        }

        printSummary(rows);
        new java.io.File("benchmark").mkdirs();
        writeCsv(rows, "benchmark/benchmark-results.csv");

        System.out.printf("%nwrote %d rows to benchmark/benchmark-results.csv%n", rows.size());
    }

    private static void warmup() throws Exception {
        for (int i = 0; i < 9; i++) {
            int[] data = Source.laplacian(4.0, 1000, i);
            for (Binarizer b : binarizersFor(data, true)) {
                roundTrip(b, data);
            }
        }
    }

    private static List<Row> runSource(String label, boolean signed, SourceGen gen) throws Exception {
        System.out.println("--- " + label + " ---");
        List<List<Trial>> trials = new ArrayList<>();
        for (int i = 0; i < N_SCHEMES; i++) trials.add(new ArrayList<>());
        String[] names = new String[N_SCHEMES];

        for (int s = 0; s < SEEDS; s++) {
            int[] data = gen.generate(s);
            double H = Stats.sourceEntropy(data);
            Binarizer[] bins = binarizersFor(data, signed);

            for (int i = 0; i < N_SCHEMES; i++) {
                Trial t = roundTrip(bins[i], data);
                t.entropy = H;
                trials.get(i).add(t);
                names[i] = bins[i].name();
            }
        }

        List<Row> rows = new ArrayList<>();
        for (int i = 0; i < N_SCHEMES; i++) {
            Row r = aggregate(label, names[i], trials.get(i));
            rows.add(r);
            System.out.printf("  %-22s bps=%.4f±%.4f  enc=%.2fms  dec=%.2fms  pass=%d/%d%n",
                    r.scheme, r.bpsMean, r.bpsStd, r.encMs, r.decMs, r.passes, r.passes + r.fails);
        }
        return rows;
    }

    private static List<Row> runKodakSweep(int q) throws Exception {
        String label = "kodak_Q" + q;
        System.out.println("--- " + label + " ---");

        List<List<Trial>> trials = new ArrayList<>();
        for (int i = 0; i < N_SCHEMES; i++) trials.add(new ArrayList<>());
        String[] names = new String[N_SCHEMES];

        for (String imageName : KODAK_IMAGES) {
            int[] data;
            try {
                data = KodakDctSource.generate(imageName, q);
            } catch (IOException e) {
                System.err.println("  Skipping " + imageName + ": " + e.getMessage());
                continue;
            }
            double H = Stats.sourceEntropy(data);
            Binarizer[] bins = binarizersFor(data, true);

            for (int i = 0; i < N_SCHEMES; i++) {
                Trial t = roundTrip(bins[i], data);
                t.entropy = H;
                trials.get(i).add(t);
                names[i] = bins[i].name();
            }
        }

        if (trials.get(0).isEmpty()) {
            System.err.println("  No Kodak images loaded; skipping Q=" + q);
            return new ArrayList<>();
        }

        List<Row> rows = new ArrayList<>();
        for (int i = 0; i < N_SCHEMES; i++) {
            Row r = aggregate(label, names[i], trials.get(i));
            rows.add(r);
            System.out.printf("  %-22s bps=%.4f±%.4f  enc=%.2fms  dec=%.2fms  pass=%d/%d%n",
                    r.scheme, r.bpsMean, r.bpsStd, r.encMs, r.decMs, r.passes, r.passes + r.fails);
        }
        return rows;
    }

    private static Binarizer[] binarizersFor(int[] data, boolean signed) {
        int[] order = AlphabetTable.orderByFrequencyDesc(data);
        int[] alphabet = AlphabetTable.uniqueValues(data);
        int[] freq = AlphabetTable.frequencies(data, alphabet);
        return new Binarizer[]{
                new UEGBinarizer(14, 0, signed),
                new ECBinarizer(order),
                new HuffmanBinarizer(alphabet, freq),
                new HuffmanPosBinarizer(alphabet, freq)
        };
    }

    private static Trial roundTrip(Binarizer bin, int[] data) throws Exception {
        Trial t = new Trial();

        long t0 = System.nanoTime();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ArithmeticEncoder enc = new ArithmeticEncoder(out);
        bin.encode(data, enc);
        enc.finish();
        long t1 = System.nanoTime();

        t.bits = enc.getBitsWritten();
        t.encNs = t1 - t0;

        long t2 = System.nanoTime();
        ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
        ArithmeticDecoder dec = new ArithmeticDecoder(in);
        int[] decoded = bin.decode(dec, data.length);
        long t3 = System.nanoTime();

        t.decNs = t3 - t2;
        t.pass = Arrays.equals(data, decoded);
        t.n = data.length;
        return t;
    }

    private static Row aggregate(String source, String scheme, List<Trial> trials) {
        Row r = new Row();
        r.source = source;
        r.scheme = scheme;
        r.seeds = trials.size();

        double[] bps = new double[trials.size()];
        double[] entropies = new double[trials.size()];
        long encSum = 0, decSum = 0;
        for (int i = 0; i < trials.size(); i++) {
            Trial t = trials.get(i);
            bps[i] = (double) t.bits / t.n;
            entropies[i] = t.entropy;
            encSum += t.encNs;
            decSum += t.decNs;
            if (t.pass) r.passes++; else r.fails++;
        }

        r.bpsMean = mean(bps);
        r.bpsStd = stddev(bps, r.bpsMean);
        r.entropyMean = mean(entropies);
        r.encMs = encSum / 1e6 / trials.size();
        r.decMs = decSum / 1e6 / trials.size();
        return r;
    }

    private static double mean(double[] x) {
        double s = 0;
        for (double v : x) s += v;
        return s / x.length;
    }

    private static double stddev(double[] x, double mean) {
        if (x.length < 2) return 0.0;
        double s = 0;
        for (double v : x) s += (v - mean) * (v - mean);
        return Math.sqrt(s / (x.length - 1));
    }

    private static void printSummary(List<Row> rows) {
        System.out.println("\n=== Summary ===");
        System.out.printf("%-18s %-22s %7s %7s %7s %7s %8s%n",
                "source", "scheme", "H", "bps", "std", "encMs", "pass");
        for (Row r : rows) {
            System.out.printf("%-18s %-22s %7.4f %7.4f %7.4f %7.2f %4d/%d%n",
                    r.source, r.scheme, r.entropyMean, r.bpsMean, r.bpsStd, r.encMs,
                    r.passes, r.passes + r.fails);
        }
    }

    private static void writeCsv(List<Row> rows, String path) throws Exception {
        try (PrintWriter w = new PrintWriter(new FileWriter(path))) {
            w.println("source,scheme,seeds,entropy,bps_mean,bps_std,enc_ms,dec_ms,passes,total");
            for (Row r : rows) {
                w.printf("%s,\"%s\",%d,%.6f,%.6f,%.6f,%.4f,%.4f,%d,%d%n",
                        r.source, r.scheme, r.seeds, r.entropyMean,
                        r.bpsMean, r.bpsStd, r.encMs, r.decMs, r.passes, r.passes + r.fails);
            }
        }
    }

    interface SourceGen {
        int[] generate(long seed);
    }

    static class Trial {
        int bits;
        long encNs;
        long decNs;
        boolean pass;
        int n;
        double entropy;
    }

    static class Row {
        String source;
        String scheme;
        int seeds;
        double entropyMean;
        double bpsMean;
        double bpsStd;
        double encMs;
        double decMs;
        int passes;
        int fails;
    }
}