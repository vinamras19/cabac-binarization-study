package com.cabac;

import java.io.IOException;

public class ECBinarizer implements Binarizer {

    private final int[] symbolOrder;

    public ECBinarizer(int[] symbolOrder) {
        this.symbolOrder = symbolOrder.clone();
    }

    public String name() {
        return "ECB(m=" + symbolOrder.length + ")";
    }

    public void encode(int[] data, ArithmeticEncoder enc) throws IOException {
        int m = symbolOrder.length;
        ContextModel[] ctx = new ContextModel[m - 1];
        for (int i = 0; i < m - 1; i++) ctx[i] = new ContextModel();

        int[] remaining = data.clone();
        int n = remaining.length;

        for (int i = 0; i < m - 1; i++) {
            int target = symbolOrder[i];
            int[] next = new int[n];
            int nextLen = 0;
            for (int j = 0; j < n; j++) {
                if (remaining[j] == target) {
                    enc.encodeBin(ctx[i], 1);
                } else {
                    enc.encodeBin(ctx[i], 0);
                    next[nextLen++] = remaining[j];
                }
            }
            remaining = new int[nextLen];
            System.arraycopy(next, 0, remaining, 0, nextLen);
            n = nextLen;
        }
    }

    public int[] decode(ArithmeticDecoder dec, int count) throws IOException {
        int m = symbolOrder.length;
        ContextModel[] ctx = new ContextModel[m - 1];
        for (int i = 0; i < m - 1; i++) ctx[i] = new ContextModel();

        int[] result = new int[count];
        boolean[] resolved = new boolean[count];
        int unresolved = count;

        for (int i = 0; i < m - 1 && unresolved > 0; i++) {
            for (int j = 0; j < count; j++) {
                if (resolved[j]) continue;
                int bit = dec.decodeBin(ctx[i]);
                if (bit == 1) {
                    result[j] = symbolOrder[i];
                    resolved[j] = true;
                    unresolved--;
                }
            }
        }
        for (int j = 0; j < count; j++) {
            if (!resolved[j]) result[j] = symbolOrder[m - 1];
        }
        return result;
    }
}