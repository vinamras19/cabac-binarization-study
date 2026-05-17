package com.cabac;

import java.io.IOException;
import java.io.OutputStream;

public class BitWriter {

    private final OutputStream out;
    private int buffer = 0;
    private int bitsInBuffer = 0;
    private int bitsWritten = 0;

    public BitWriter(OutputStream out) {
        this.out = out;
    }

    public void writeBit(int bit) throws IOException {
        buffer = (buffer << 1) | (bit & 1);
        bitsInBuffer++;
        bitsWritten++;
        if (bitsInBuffer == 8) {
            out.write(buffer & 0xFF);
            buffer = 0;
            bitsInBuffer = 0;
        }
    }

    public void flush() throws IOException {
        if (bitsInBuffer > 0) {
            buffer <<= (8 - bitsInBuffer);
            out.write(buffer & 0xFF);
            buffer = 0;
            bitsInBuffer = 0;
        }
        out.flush();
    }

    public int getBitsWritten() { return bitsWritten; }
}