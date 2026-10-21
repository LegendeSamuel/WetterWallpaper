package de.seelig.wetterwallpaper;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** Original | Maske | Herbst global (bisher) | Herbst nur Pflanzen. Herbstmatrix wie Season, Stärke 0.6. */
public class VegHarness {
    static final float[] AUTUMN = {
            1.10f, 0.50f, -0.05f, 0, 6,
            0.02f, 0.86f, -0.04f, 0, 0,
            -0.06f, -0.10f, 0.70f, 0, -6};

    public static void main(String[] a) throws Exception {
        File d = new File(a[0]);
        BufferedImage bg = ImageIO.read(new File(d, "background.png"));
        int w = bg.getWidth(), h = bg.getHeight();
        int[] px = bg.getRGB(0, 0, w, h, null, 0, w);
        long t = System.currentTimeMillis();
        float[] veg = Vegetation.mask(px, w, h, w / 1080f);
        System.out.println("ms " + (System.currentTimeMillis() - t));
        BufferedImage[] tiles = new BufferedImage[4];
        for (int j = 0; j < 4; j++) tiles[j] = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int i = 0; i < px.length; i++) {
            int c = px[i];
            int x = i % w, y = i / w;
            tiles[0].setRGB(x, y, c & 0xFFFFFF);
            int m = Math.round(veg[i] * 255);
            tiles[1].setRGB(x, y, (m << 16) | (m << 8) | m);
            int au = autumn(c, 0.6f);
            tiles[2].setRGB(x, y, au);
            tiles[3].setRGB(x, y, mix(c, au, veg[i]));
        }
        int tw = w / 2, th = h / 2;
        BufferedImage sheet = new BufferedImage(tw * 4 + 24, th, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sheet.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        for (int j = 0; j < 4; j++) g.drawImage(tiles[j], j * (tw + 8), 0, tw, th, null);
        g.dispose();
        ImageIO.write(sheet, "png", new File(d, "veg_sheet.png"));
    }

    static int autumn(int c, float s) {
        float[] in = {(c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF};
        int out = 0;
        for (int row = 0; row < 3; row++) {
            float v = 0;
            for (int k = 0; k < 3; k++) {
                float m = (row == k ? 1 : 0) + s * (AUTUMN[row * 5 + k] - (row == k ? 1 : 0));
                v += m * in[k];
            }
            v += s * AUTUMN[row * 5 + 4];
            out = (out << 8) | Math.max(0, Math.min(255, Math.round(v)));
        }
        return out;
    }

    static int mix(int a, int b, float t) {
        int out = 0;
        for (int sh = 16; sh >= 0; sh -= 8) {
            int x = (a >> sh) & 0xFF, y = (b >> sh) & 0xFF;
            out = (out << 8) | Math.round(x + (y - x) * t);
        }
        return out;
    }
}
