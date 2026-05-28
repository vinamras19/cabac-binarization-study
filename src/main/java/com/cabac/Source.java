package com.cabac;

import java.util.Random;

public class Source {

    public static int[] uniform(int alphabetSize, int n, long seed) {
        Random r = new Random(seed);
        int[] out = new int[n];
        for (int i = 0; i < n; i++) out[i] = r.nextInt(alphabetSize);
        return out;
    }

    public static int[] biased(int alphabetSize, double p0, int n, long seed) {
        Random r = new Random(seed);
        int[] out = new int[n];
        double pOther = (1 - p0) / (alphabetSize - 1);
        for (int i = 0; i < n; i++) {
            double u = r.nextDouble();
            if (u < p0) {
                out[i] = 0;
            } else {
                u -= p0;
                int v = 1;
                while (v < alphabetSize - 1 && u >= pOther) {
                    u -= pOther;
                    v++;
                }
                out[i] = v;
            }
        }
        return out;
    }

    public static int[] geometric(int alphabetSize, double p, int n, long seed) {
        Random r = new Random(seed);
        int[] out = new int[n];
        double[] cdf = new double[alphabetSize];
        double total = 0.0;
        for (int k = 0; k < alphabetSize; k++) {
            cdf[k] = Math.pow(1 - p, k) * p;
            total += cdf[k];
        }
        double cum = 0.0;
        for (int k = 0; k < alphabetSize; k++) {
            cum += cdf[k] / total;
            cdf[k] = cum;
        }
        for (int i = 0; i < n; i++) {
            double u = r.nextDouble();
            int v = 0;
            while (v < alphabetSize - 1 && u >= cdf[v]) v++;
            out[i] = v;
        }
        return out;
    }

    public static int[] laplacian(double scale, int n, long seed) {
        Random r = new Random(seed);
        int[] out = new int[n];
        for (int i = 0; i < n; i++) {
            double u = r.nextDouble() - 0.5;
            double sign = Math.signum(u);
            double mag = -scale * Math.log(1 - 2 * Math.abs(u));
            out[i] = (int) Math.round(sign * mag);
        }
        return out;
    }
}