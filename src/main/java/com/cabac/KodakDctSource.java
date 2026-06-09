package com.cabac;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class KodakDctSource {

    private static final String KODAK_DIR = System.getProperty("kodak.dir", "kodak");

    private static final Map<String, double[][]> LUMA_CACHE = new HashMap<>();

    public static int[] generate(String imageName, int qStep) throws IOException {
        double[][] y = LUMA_CACHE.get(imageName);
        if (y == null) {
            BufferedImage img = loadImage(imageName);
            y = extractLuma(img);
            LUMA_CACHE.put(imageName, y);
        }
        return DctPipeline.applyDctAndZigzag(y, qStep);
    }

    private static BufferedImage loadImage(String imageName) throws IOException {
        File file = new File(KODAK_DIR, imageName);
        if (!file.exists()) {
            throw new FileNotFoundException(
                    "Kodak image not found: " + file.getAbsolutePath() + "\n" +
                    "  Download the Kodak True Color Image Suite from http://r0k.us/graphics/kodak/\n" +
                    "  and place PNG files in: " + new File(KODAK_DIR).getAbsolutePath()
            );
        }
        BufferedImage img = ImageIO.read(file);
        if (img == null) {
            throw new IOException("Failed to decode image: " + file.getAbsolutePath());
        }
        return img;
    }

    private static double[][] extractLuma(BufferedImage img) {
        int w = (img.getWidth() / 8) * 8;
        int h = (img.getHeight() / 8) * 8;
        double[][] y = new double[h][w];
        for (int row = 0; row < h; row++) {
            for (int col = 0; col < w; col++) {
                int rgb = img.getRGB(col, row);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                // BT.601 luma
                y[row][col] = 0.299 * r + 0.587 * g + 0.114 * b;
            }
        }
        return y;
    }
}
