package com.cabac;

import java.io.IOException;
import java.io.OutputStream;

public class ArithmeticEncoder {

    private final BitWriter bitWriter;

    private int codILow;
    private int codIRange;
    private int bitsOutstanding;
    private boolean firstBitFlag;

    public ArithmeticEncoder(OutputStream out) {
        this.bitWriter = new BitWriter(out);
        this.codILow = 0;
        this.codIRange = 510;
        this.bitsOutstanding = 0;
        this.firstBitFlag = true;
    }

    public void encodeBin(ContextModel ctx, int binVal) throws IOException {
        int rangeLPS = Tables.RANGE_TAB_LPS[ctx.getStateIdx()][(codIRange >> 6) & 3];
        codIRange -= rangeLPS;

        if (binVal != ctx.getValMPS()) {
            codILow += codIRange;
            codIRange = rangeLPS;
            ctx.updateOnLPS();
        } else {
            ctx.updateOnMPS();
        }
        renormE();
    }

    public void encodeBypass(int binVal) throws IOException {
        codILow <<= 1;
        if (binVal == 1) codILow += codIRange;

        if (codILow >= 1024) {
            putBit(1);
            codILow -= 1024;
        } else if (codILow < 512) {
            putBit(0);
        } else {
            codILow -= 512;
            bitsOutstanding++;
        }
    }

    public void encodeTerminate(int binVal) throws IOException {
        codIRange -= 2;
        if (binVal != 0) {
            codILow += codIRange;
            encodeFlush();
        } else {
            renormE();
        }
    }

    public void finish() throws IOException {
        encodeTerminate(1);
    }

    private void renormE() throws IOException {
        while (codIRange < 256) {
            if (codILow < 256) {
                putBit(0);
            } else if (codILow >= 512) {
                codILow -= 512;
                putBit(1);
            } else {
                codILow -= 256;
                bitsOutstanding++;
            }
            codIRange <<= 1;
            codILow <<= 1;
        }
    }

    private void putBit(int B) throws IOException {
        if (firstBitFlag) {
            firstBitFlag = false;
        } else {
            bitWriter.writeBit(B);
        }
        while (bitsOutstanding > 0) {
            bitWriter.writeBit(1 - B);
            bitsOutstanding--;
        }
    }

    private void encodeFlush() throws IOException {
        codIRange = 2;
        renormE();
        putBit((codILow >> 9) & 1);
        bitWriter.writeBit((codILow >> 8) & 1);
        bitWriter.writeBit(1);
        bitWriter.flush();
    }

    public int getBitsWritten() { return bitWriter.getBitsWritten(); }
}