package com.cabac;

import java.io.IOException;
import java.io.InputStream;

public class ArithmeticDecoder {

    private final BitReader bitReader;

    private int codIRange;
    private int codIOffset;

    public ArithmeticDecoder(InputStream in) throws IOException {
        this.bitReader = new BitReader(in);
        this.codIRange = 510;
        this.codIOffset = 0;
        for (int i = 0; i < 9; i++) {
            codIOffset = (codIOffset << 1) | bitReader.readBit();
        }
    }

    public int decodeBin(ContextModel ctx) throws IOException {
        int rangeLPS = Tables.RANGE_TAB_LPS[ctx.getStateIdx()][(codIRange >> 6) & 3];
        codIRange -= rangeLPS;

        int binVal;
        if (codIOffset >= codIRange) {
            binVal = 1 - ctx.getValMPS();
            codIOffset -= codIRange;
            codIRange = rangeLPS;
            ctx.updateOnLPS();
        } else {
            binVal = ctx.getValMPS();
            ctx.updateOnMPS();
        }
        renormD();
        return binVal;
    }

    public int decodeBypass() throws IOException {
        codIOffset = (codIOffset << 1) | bitReader.readBit();
        if (codIOffset >= codIRange) {
            codIOffset -= codIRange;
            return 1;
        }
        return 0;
    }

    private void renormD() throws IOException {
        while (codIRange < 256) {
            codIRange <<= 1;
            codIOffset = (codIOffset << 1) | bitReader.readBit();
        }
    }
}