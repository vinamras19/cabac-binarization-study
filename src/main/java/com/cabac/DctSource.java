package com.cabac;

import java.util.Random;

public class DctSource {

    private static final int W = 256;
    private static final int H = 256;

    public static int[] generate(int qStep, long seed) {
        double[][] image = procedural(seed);
        return DctPipeline.applyDctAndZigzag(image, qStep);
    }

    private static double[][] procedural(long seed) {
        Random r = new Random(seed);
        double[][] img = new double[H][W];

        // gradient
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                img[y][x] = 80 + 60 * Math.sin(2 * Math.PI * (x + y) / 256.0);
            }
        }

        // bumps
        double[][] bumps = {
                { 64,  64, 30, 60},
                {180,  90, 25, 50},
                {100, 180, 40, 70},
                {200, 200, 20, 40},
                { 40, 220, 35, 55},
                {220,  40, 28, 45}
        };
        for (double[] b : bumps) {
            double cx = b[0], cy = b[1], sigma = b[2], amp = b[3];
            for (int y = 0; y < H; y++) {
                for (int x = 0; x < W; x++) {
                    double d2 = (x - cx) * (x - cx) + (y - cy) * (y - cy);
                    img[y][x] += amp * Math.exp(-d2 / (2 * sigma * sigma));
                }
            }
        }

        // edges
        int[][] rects = {
                { 50,  50, 30, 30},
                {150, 100, 40, 25},
                { 80, 180, 25, 40},
                {190, 170, 35, 30}
        };
        for (int[] rc : rects) {
            int x0 = rc[0], y0 = rc[1], w = rc[2], h = rc[3];
            int x1 = Math.min(x0 + w, W);
            int y1 = Math.min(y0 + h, H);
            for (int y = y0; y < y1; y++) {
                for (int x = x0; x < x1; x++) {
                    img[y][x] += 50;
                }
            }
        }

        // noise + clamp
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                img[y][x] += r.nextGaussian() * 5;
                img[y][x] = Math.max(0, Math.min(255, img[y][x]));
            }
        }
        return img;
    }
}
