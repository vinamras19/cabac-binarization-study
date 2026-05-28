package com.cabac;

import java.util.HashMap;
import java.util.Map;

public class Stats {

    public static double sourceEntropy(int[] data) {
        if (data.length == 0) return 0.0;
        Map<Integer, Integer> freq = new HashMap<>();
        for (int v : data) freq.merge(v, 1, Integer::sum);

        double H = 0.0;
        int n = data.length;
        for (int c : freq.values()) {
            double p = (double) c / n;
            H -= p * log2(p);
        }
        return H;
    }

    public static double log2(double x) {
        return Math.log(x) / Math.log(2);
    }
}