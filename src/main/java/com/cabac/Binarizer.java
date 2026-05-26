package com.cabac;

import java.io.IOException;

public interface Binarizer {

    String name();

    void encode(int[] data, ArithmeticEncoder enc) throws IOException;

    int[] decode(ArithmeticDecoder dec, int count) throws IOException;
}