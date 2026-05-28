package com.cabac;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AlphabetTable {

    public static int[] uniqueValues(int[] data) {
        Map<Integer, Integer> counts = new HashMap<>();
        for (int v : data) counts.merge(v, 1, Integer::sum);
        int[] out = new int[counts.size()];
        int i = 0;
        for (int k : counts.keySet()) out[i++] = k;
        Arrays.sort(out);
        return out;
    }

    public static int[] frequencies(int[] data, int[] alphabet) {
        Map<Integer, Integer> counts = new HashMap<>();
        for (int v : data) counts.merge(v, 1, Integer::sum);
        int[] out = new int[alphabet.length];
        for (int i = 0; i < alphabet.length; i++) {
            out[i] = counts.getOrDefault(alphabet[i], 0);
        }
        return out;
    }

    public static int[] orderByFrequencyDesc(int[] data) {
        Map<Integer, Integer> counts = new HashMap<>();
        for (int v : data) counts.merge(v, 1, Integer::sum);
        List<Map.Entry<Integer, Integer>> entries = new ArrayList<>(counts.entrySet());
        entries.sort((a, b) -> {
            int cmp = Integer.compare(b.getValue(), a.getValue());
            if (cmp != 0) return cmp;
            return Integer.compare(a.getKey(), b.getKey());
        });
        int[] out = new int[entries.size()];
        for (int i = 0; i < entries.size(); i++) out[i] = entries.get(i).getKey();
        return out;
    }

    public static boolean hasNegative(int[] data) {
        for (int v : data) if (v < 0) return true;
        return false;
    }
}