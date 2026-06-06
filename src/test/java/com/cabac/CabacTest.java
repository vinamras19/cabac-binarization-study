package com.cabac;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.*;

public class CabacTest {

    @Test
    public void testBitWriterReader() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        BitWriter bw = new BitWriter(out);
        int[] bits = {1, 0, 1, 1, 0, 0, 1, 0, 1, 1, 1};
        for (int b : bits) bw.writeBit(b);
        bw.flush();

        BitReader br = new BitReader(new ByteArrayInputStream(out.toByteArray()));
        for (int b : bits) assertEquals(b, br.readBit());
    }

    @Test
    public void testEncoderDecoderRoundTrip() throws Exception {
        ContextModel encCtx = new ContextModel();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ArithmeticEncoder enc = new ArithmeticEncoder(out);
        int[] bins = {1, 0, 1, 1, 0, 0, 1, 1, 0, 1};
        for (int b : bins) enc.encodeBin(encCtx, b);
        enc.finish();

        ContextModel decCtx = new ContextModel();
        ArithmeticDecoder dec = new ArithmeticDecoder(new ByteArrayInputStream(out.toByteArray()));
        for (int b : bins) assertEquals(b, dec.decodeBin(decCtx));
    }

    @Test
    public void testTuRoundTrip() {
        for (int v = 0; v <= 10; v++) {
            int[] bins = Binarization.tuEncode(v, 14);
            assertEquals(v, Binarization.tuDecode(bins, 14));
        }
    }

    @Test
    public void testFlRoundTrip() {
        for (int v = 0; v < 16; v++) {
            int[] bins = Binarization.flEncode(v, 4);
            assertEquals(v, Binarization.flDecode(bins, 4));
        }
    }

    @Test
    public void testEgkRoundTrip() {
        for (int v = 0; v < 100; v++) {
            int[] bins = Binarization.egkEncode(v, 0);
            assertEquals(v, Binarization.egkDecode(bins, 0));
        }
        for (int v = 0; v < 100; v++) {
            int[] bins = Binarization.egkEncode(v, 3);
            assertEquals(v, Binarization.egkDecode(bins, 3));
        }
    }

    @Test
    public void testUEGRoundTrip() throws Exception {
        // values both below and above uCoff
        int[] data = {0, 1, 2, 5, 10, 14, 15, 20, 50, 100};
        Binarizer ueg = new UEGBinarizer(14, 0, false);
        assertArrayEquals(data, encDec(ueg, data));
    }

    @Test
    public void testUEGSignedRoundTrip() throws Exception {
        int[] data = {-50, -10, -1, 0, 1, 10, 50};
        Binarizer ueg = new UEGBinarizer(14, 0, true);
        assertArrayEquals(data, encDec(ueg, data));
    }

    @Test
    public void testECBRoundTripUniform() throws Exception {
        int[] data = Source.uniform(8, 1000, 42);
        int[] order = AlphabetTable.orderByFrequencyDesc(data);
        Binarizer ecb = new ECBinarizer(order);
        assertArrayEquals(data, encDec(ecb, data));
    }

    @Test
    public void testECBRoundTripBiased() throws Exception {
        int[] data = Source.biased(4, 0.7, 1000, 42);
        int[] order = AlphabetTable.orderByFrequencyDesc(data);
        Binarizer ecb = new ECBinarizer(order);
        assertArrayEquals(data, encDec(ecb, data));
    }

    @Test
    public void testHuffmanRoundTrip() throws Exception {
        int[] data = Source.biased(8, 0.5, 1000, 42);
        int[] alphabet = AlphabetTable.uniqueValues(data);
        int[] freq = AlphabetTable.frequencies(data, alphabet);
        Binarizer huff = new HuffmanBinarizer(alphabet, freq);
        assertArrayEquals(data, encDec(huff, data));
    }

    @Test
    public void testHuffmanPosRoundTrip() throws Exception {
        int[] data = Source.biased(8, 0.5, 1000, 42);
        int[] alphabet = AlphabetTable.uniqueValues(data);
        int[] freq = AlphabetTable.frequencies(data, alphabet);
        Binarizer huff = new HuffmanPosBinarizer(alphabet, freq);
        assertArrayEquals(data, encDec(huff, data));
    }

    @Test
    public void testCompressionBeatsRaw() throws Exception {
        int[] data = Source.biased(2, 0.95, 5000, 7);
        Binarizer ueg = new UEGBinarizer(14, 0, false);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ArithmeticEncoder enc = new ArithmeticEncoder(out);
        ueg.encode(data, enc);
        enc.finish();

        assertTrue(enc.getBitsWritten() < data.length / 2);
    }

    @Test
    public void testEntropyEfficiencyAcrossSchemes() throws Exception {
        // all values < uCoff
        int[] data = Source.geometric(8, 0.6, 5000, 11);
        double H = Stats.sourceEntropy(data);

        Binarizer ueg = new UEGBinarizer(14, 0, false);
        Binarizer ecb = new ECBinarizer(AlphabetTable.orderByFrequencyDesc(data));
        int[] alphabet = AlphabetTable.uniqueValues(data);
        int[] freq = AlphabetTable.frequencies(data, alphabet);
        Binarizer huff = new HuffmanBinarizer(alphabet, freq);

        for (Binarizer b : new Binarizer[]{ueg, ecb, huff}) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ArithmeticEncoder enc = new ArithmeticEncoder(out);
            b.encode(data, enc);
            enc.finish();
            double bps = (double) enc.getBitsWritten() / data.length;
            assertTrue(bps <= 1.10 * H + 0.05,
                    b.name() + " bps=" + bps + " H=" + H);
        }
    }

    @Test
    public void testDctRoundTrip() throws Exception {
        int[] data = DctSource.generate(8, 42);
        Binarizer ueg = new UEGBinarizer(14, 0, true);
        assertArrayEquals(data, encDec(ueg, data));
    }

    @Test
    public void testAlphabetUtilities() {
        int[] data = {3, 1, 3, 2, 1, 3, 0};
        assertArrayEquals(new int[]{0, 1, 2, 3}, AlphabetTable.uniqueValues(data));
        int[] order = AlphabetTable.orderByFrequencyDesc(data);
        assertEquals(3, order[0]);
        assertFalse(AlphabetTable.hasNegative(data));
        assertTrue(AlphabetTable.hasNegative(new int[]{-1, 0, 1}));
    }

    @Test
    public void testEntropyOfBiasedBinary() {
        int[] data = Source.biased(2, 0.9, 10000, 42);
        double H = Stats.sourceEntropy(data);
        assertTrue(H > 0.4 && H < 0.55, "H=" + H);
    }

    private static int[] encDec(Binarizer bin, int[] data) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ArithmeticEncoder enc = new ArithmeticEncoder(out);
        bin.encode(data, enc);
        enc.finish();

        ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
        ArithmeticDecoder dec = new ArithmeticDecoder(in);
        return bin.decode(dec, data.length);
    }
}