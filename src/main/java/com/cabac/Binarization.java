package com.cabac;

import java.util.ArrayList;
import java.util.List;

public class Binarization {

    public static int[] tuEncode(int synVal, int cMax) {
        int len = (synVal < cMax) ? synVal + 1 : cMax;
        int[] bins = new int[len];
        int onesCount = Math.min(synVal, cMax);
        for (int i = 0; i < onesCount; i++) bins[i] = 1;
        return bins;
    }

    public static int tuDecode(int[] bins, int cMax) {
        int v = 0;
        while (v < bins.length && v < cMax && bins[v] == 1) v++;
        return v;
    }

    public static int[] flEncode(int synVal, int numBits) {
        int[] bins = new int[numBits];
        for (int i = 0; i < numBits; i++) {
            bins[i] = (synVal >> (numBits - 1 - i)) & 1;
        }
        return bins;
    }

    public static int flDecode(int[] bins, int numBits) {
        int v = 0;
        for (int i = 0; i < numBits; i++) v = (v << 1) | bins[i];
        return v;
    }

    public static int[] egkEncode(int synVal, int k) {
        List<Integer> bins = new ArrayList<>();
        int absV = synVal;
        int kCur = k;
        while (absV >= (1 << kCur)) {
            bins.add(1);
            absV -= (1 << kCur);
            kCur++;
        }
        bins.add(0);
        for (int i = kCur - 1; i >= 0; i--) {
            bins.add((absV >> i) & 1);
        }
        int[] arr = new int[bins.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = bins.get(i);
        return arr;
    }

    public static int egkDecode(int[] bins, int k) {
        int idx = 0;
        int n = 0;
        while (bins[idx] == 1) { n++; idx++; }
        idx++;
        int suffix = 0;
        for (int i = 0; i < k + n; i++) {
            suffix = (suffix << 1) | bins[idx + i];
        }
        return suffix + ((1 << (k + n)) - (1 << k));
    }
}