package com.cabac;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;

public class HuffmanPosBinarizer implements Binarizer {

    private final int[] alphabet;
    private final int[] frequencies;
    private final Map<Integer, int[]> codes;
    private final int maxLen;

    public HuffmanPosBinarizer(int[] alphabet, int[] frequencies) {
        this.alphabet = alphabet.clone();
        this.frequencies = frequencies.clone();
        this.codes = buildCanonicalCodes();
        int max = 0;
        for (int[] c : codes.values()) max = Math.max(max, c.length);
        this.maxLen = max;
    }

    public String name() {
        return "HuffmanPos(m=" + alphabet.length + ")";
    }

    public void encode(int[] data, ArithmeticEncoder enc) throws IOException {
        ContextModel[] ctx = new ContextModel[maxLen];
        for (int i = 0; i < maxLen; i++) ctx[i] = new ContextModel();

        for (int v : data) {
            int[] code = codes.get(v);
            for (int i = 0; i < code.length; i++) {
                enc.encodeBin(ctx[i], code[i]);
            }
        }
    }

    public int[] decode(ArithmeticDecoder dec, int count) throws IOException {
        ContextModel[] ctx = new ContextModel[maxLen];
        for (int i = 0; i < maxLen; i++) ctx[i] = new ContextModel();

        Map<String, Integer> codeToSym = new HashMap<>();
        for (Map.Entry<Integer, int[]> e : codes.entrySet()) {
            StringBuilder sb = new StringBuilder();
            for (int b : e.getValue()) sb.append(b);
            codeToSym.put(sb.toString(), e.getKey());
        }

        int[] result = new int[count];
        StringBuilder cur = new StringBuilder();
        int produced = 0;
        int pos = 0;
        while (produced < count) {
            cur.append(dec.decodeBin(ctx[pos]));
            pos++;
            Integer sym = codeToSym.get(cur.toString());
            if (sym != null) {
                result[produced++] = sym;
                cur.setLength(0);
                pos = 0;
            }
            if (pos > maxLen) {
                throw new IOException("HuffmanPos decode overflow");
            }
        }
        return result;
    }

    private Map<Integer, int[]> buildCanonicalCodes() {
        int n = alphabet.length;
        if (n == 1) {
            Map<Integer, int[]> single = new HashMap<>();
            single.put(alphabet[0], new int[]{0});
            return single;
        }

        PriorityQueue<int[]> pq = new PriorityQueue<>((a, b) -> {
            if (a[0] != b[0]) return Integer.compare(a[0], b[0]);
            return Integer.compare(a[1], b[1]);
        });

        Map<Integer, int[]> nodes = new HashMap<>();
        for (int i = 0; i < n; i++) {
            int[] node = new int[]{frequencies[i], i, alphabet[i], -1, -1};  // node = {freq, id, symbol, leftId, rightId}
            nodes.put(i, node);
            pq.offer(node);
        }

        int nextId = n;
        while (pq.size() > 1) {
            int[] a = pq.poll();
            int[] b = pq.poll();
            int[] parent = new int[]{a[0] + b[0], nextId, -1, a[1], b[1]};
            nodes.put(nextId, parent);
            pq.offer(parent);
            nextId++;
        }

        int[] root = pq.poll();
        Map<Integer, Integer> lengths = new HashMap<>();
        assignLengths(root, 0, nodes, lengths);
        return canonicalize(lengths);
    }

    private void assignLengths(int[] node, int depth, Map<Integer, int[]> nodes, Map<Integer, Integer> lengths) {
        if (node[3] == -1 && node[4] == -1) {
            lengths.put(node[2], Math.max(depth, 1));
            return;
        }
        assignLengths(nodes.get(node[3]), depth + 1, nodes, lengths);
        assignLengths(nodes.get(node[4]), depth + 1, nodes, lengths);
    }

    private Map<Integer, int[]> canonicalize(Map<Integer, Integer> lengths) {
        Integer[] symbols = lengths.keySet().toArray(new Integer[0]);
        java.util.Arrays.sort(symbols, (a, b) -> {
            int la = lengths.get(a), lb = lengths.get(b);
            if (la != lb) return Integer.compare(la, lb);
            return Integer.compare(a, b);
        });

        Map<Integer, int[]> result = new HashMap<>();
        int code = 0;
        int prevLen = -1;
        for (int sym : symbols) {
            int len = lengths.get(sym);
            if (prevLen != -1) code = (code + 1) << (len - prevLen);
            int[] bits = new int[len];
            for (int i = 0; i < len; i++) bits[i] = (code >> (len - 1 - i)) & 1;
            result.put(sym, bits);
            prevLen = len;
        }
        return result;
    }
}