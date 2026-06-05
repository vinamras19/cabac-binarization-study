package com.cabac;

public class DctPipeline {

    private static final int B = 8;

    private static final int[] ZIGZAG = {
             0,  1,  8, 16,  9,  2,  3, 10,
            17, 24, 32, 25, 18, 11,  4,  5,
            12, 19, 26, 33, 40, 48, 41, 34,
            27, 20, 13,  6,  7, 14, 21, 28,
            35, 42, 49, 56, 57, 50, 43, 36,
            29, 22, 15, 23, 30, 37, 44, 51,
            58, 59, 52, 45, 38, 31, 39, 46,
            53, 60, 61, 54, 47, 55, 62, 63
    };

    public static int[] applyDctAndZigzag(double[][] image, int qStep) {
        int h = (image.length / B) * B;
        int w = (image[0].length / B) * B;
        int blocks = (h / B) * (w / B);
        int[] out = new int[blocks * 64];
        int idx = 0;

        for (int by = 0; by < h; by += B) {
            for (int bx = 0; bx < w; bx += B) {
                double[][] block = new double[B][B];
                for (int y = 0; y < B; y++) {
                    for (int x = 0; x < B; x++) {
                        block[y][x] = image[by + y][bx + x] - 128;
                    }
                }
                double[][] dct = dct8(block);
                for (int k = 0; k < 64; k++) {
                    int zy = ZIGZAG[k] / 8;
                    int zx = ZIGZAG[k] % 8;
                    out[idx++] = (int) Math.round(dct[zy][zx] / qStep);
                }
            }
        }
        return out;
    }

    private static double[][] dct8(double[][] block) {
        double[][] out = new double[B][B];
        for (int u = 0; u < B; u++) {
            double cu = (u == 0) ? 1.0 / Math.sqrt(2) : 1.0;
            for (int v = 0; v < B; v++) {
                double cv = (v == 0) ? 1.0 / Math.sqrt(2) : 1.0;
                double sum = 0.0;
                for (int y = 0; y < B; y++) {
                    for (int x = 0; x < B; x++) {
                        sum += block[y][x]
                                * Math.cos((2 * x + 1) * u * Math.PI / 16.0)
                                * Math.cos((2 * y + 1) * v * Math.PI / 16.0);
                    }
                }
                out[u][v] = 0.25 * cu * cv * sum;
            }
        }
        return out;
    }
}
