package com.cabac;

import java.io.IOException;

public class UEGBinarizer implements Binarizer {

    private final int uCoff;
    private final int egk;
    private final boolean signed;

    public UEGBinarizer(int uCoff, int egk, boolean signed) {
        this.uCoff = uCoff;
        this.egk = egk;
        this.signed = signed;
    }

    public String name() {
        return String.format("UEG(uCoff=%d,k=%d%s)", uCoff, egk, signed ? ",signed" : "");
    }

    public void encode(int[] data, ArithmeticEncoder enc) throws IOException {
        ContextModel[] ctx = new ContextModel[uCoff];
        for (int i = 0; i < uCoff; i++) ctx[i] = new ContextModel();

        for (int v : data) {
            int mag = signed ? Math.abs(v) : v;

            int prefixLen = Math.min(mag, uCoff);
            for (int i = 0; i < prefixLen; i++) enc.encodeBin(ctx[i], 1);
            if (mag < uCoff) {
                enc.encodeBin(ctx[mag], 0);
            } else {
                int[] suffix = Binarization.egkEncode(mag - uCoff, egk);
                for (int b : suffix) enc.encodeBypass(b);
            }

            if (signed && mag != 0) enc.encodeBypass(v < 0 ? 1 : 0);
        }
    }

    public int[] decode(ArithmeticDecoder dec, int count) throws IOException {
        ContextModel[] ctx = new ContextModel[uCoff];
        for (int i = 0; i < uCoff; i++) ctx[i] = new ContextModel();

        int[] result = new int[count];
        for (int s = 0; s < count; s++) {
            int mag = 0;
            while (mag < uCoff) {
                if (dec.decodeBin(ctx[mag]) == 0) break;
                mag++;
            }
            if (mag == uCoff) {
                int n = 0;
                while (dec.decodeBypass() == 1) n++;
                int suffix = 0;
                for (int i = 0; i < egk + n; i++) suffix = (suffix << 1) | dec.decodeBypass();
                mag += suffix + ((1 << (egk + n)) - (1 << egk));
            }
            int sign = (signed && mag != 0) ? dec.decodeBypass() : 0;
            result[s] = (sign == 1) ? -mag : mag;
        }
        return result;
    }
}
