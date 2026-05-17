package com.cabac;

import java.io.IOException;
import java.io.InputStream;

public class BitReader {

    private final InputStream in;
    private int buffer = 0;
    private int bitsLeft = 0;

    public BitReader(InputStream in) {
        this.in = in;
    }

    public int readBit() throws IOException {
        if (bitsLeft == 0) {
            int next = in.read();
            if (next < 0) return 0;
            buffer = next & 0xFF;
            bitsLeft = 8;
        }
        bitsLeft--;
        return (buffer >> bitsLeft) & 1;
    }
}