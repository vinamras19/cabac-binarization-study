package com.cabac;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;

public class BenchmarkRunner {

    public static void main(String[] args) throws Exception {
        System.out.println("=== CABAC Binarization Study ===\n");

        runRoundTrip("Small fixed [0,3,1,2,0,1,1,0]",
                new int[]{0, 3, 1, 2, 0, 1, 1, 0},
                new UEGBinarizer(14, 0, false));

        // biased binary
        int[] biased = Source.biased(2, 0.9, 10000, 42);
        shootout("Biased binary (P(0)=0.9, n=10000)", biased, false);

        // uniform
        int[] uniform = Source.uniform(8, 10000, 42);
        shootout("Uniform [0,8) (n=10000)", uniform, false);

        // geometric
        int[] geom = Source.geometric(8, 0.6, 10000, 42);
        shootout("Geometric [0,8) p=0.6 (n=10000)", geom, false);

        // laplacian narrow
        int[] lapNarrow = Source.laplacian(2.0, 10000, 42);
        shootout("Laplacian b=2 (n=10000)", lapNarrow, true);

        // laplacian wide
        int[] lapWide = Source.laplacian(8.0, 10000, 42);
        shootout("Laplacian b=8 (n=10000)", lapWide, true);

        // dct residuals
        int[] dct = DctSource.generate(8, 42);
        shootout("DCT residuals Q=8 (n=" + dct.length + ")", dct, true);
    }

    private static void shootout(String label, int[] data, boolean signed) throws Exception {
        System.out.println("--- " + label + " ---");
        double H = Stats.sourceEntropy(data);
        System.out.printf("source entropy: %.4f bits/symbol%n", H);
        System.out.printf("raw bits (8/symbol): %d%n%n", data.length * 8);

        Binarizer ueg = new UEGBinarizer(14, 0, signed);
        runScheme(ueg, data);

        int[] order = AlphabetTable.orderByFrequencyDesc(data);
        Binarizer ecb = new ECBinarizer(order);
        runScheme(ecb, data);

        int[] alphabet = AlphabetTable.uniqueValues(data);
        int[] freq = AlphabetTable.frequencies(data, alphabet);
        Binarizer huff = new HuffmanBinarizer(alphabet, freq);
        runScheme(huff, data);

        System.out.println();
    }

    private static void runScheme(Binarizer bin, int[] data) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ArithmeticEncoder enc = new ArithmeticEncoder(out);
        bin.encode(data, enc);
        enc.finish();
        int bits = enc.getBitsWritten();

        ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
        ArithmeticDecoder dec = new ArithmeticDecoder(in);
        int[] decoded = bin.decode(dec, data.length);
        boolean ok = Arrays.equals(data, decoded);

        double bps = (double) bits / data.length;
        System.out.printf("%-22s %6d bits  %.4f bits/symbol  %s%n",
                bin.name(), bits, bps, ok ? "OK" : "MISMATCH");
    }

    private static void runRoundTrip(String label, int[] data, Binarizer bin) throws Exception {
        System.out.println("--- " + label + " ---");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ArithmeticEncoder enc = new ArithmeticEncoder(out);
        bin.encode(data, enc);
        enc.finish();
        int bits = enc.getBitsWritten();

        ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
        ArithmeticDecoder dec = new ArithmeticDecoder(in);
        int[] decoded = bin.decode(dec, data.length);
        boolean ok = Arrays.equals(data, decoded);

        System.out.printf("%-22s %d bits  %s%n%n", bin.name(), bits, ok ? "OK" : "MISMATCH");
    }
}