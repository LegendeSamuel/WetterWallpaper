package de.seelig.wetterwallpaper;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** PC-Gegenprobe der Scene-Kette (Tiefe/Himmel/Landfarbe) mit echten Handydateien. */
public class Harness {
    public static void main(String[] a) throws Exception {
        File d = new File(a[0]);
        BufferedImage bg = ImageIO.read(new File(d, "background.png"));
        int w = bg.getWidth(), h = bg.getHeight(), n = w * h;
        float k = w / 1080f;
        int[] px = bg.getRGB(0, 0, w, h, null, 0, w);
        int[] dp = scaled(ImageIO.read(new File(d, "depth.png")), w, h);
        int[] sk = scaled(ImageIO.read(new File(d, "sky.png")), w, h);
        float[] v = new float[n], lum = new float[n], sky = new float[n];
        for (int i = 0; i < n; i++) {
            v[i] = (dp[i] & 0xFF) / 255f;
            int c = px[i];
            lum[i] = (0.299f * ((c >> 16) & 0xFF) + 0.587f * ((c >> 8) & 0xFF) + 0.114f * (c & 0xFF)) / 255f;
            sky[i] = (sk[i] & 0xFF) / 255f;
        }
        long t = System.currentTimeMillis();
        v = EdgeRefine.guided(lum, v, w, h, Math.max(2, Math.round(12 * k)), 1e-3f);
        double skySum = 0;
        for (float x : sky) skySum += x;
        if (skySum < n * 0.001) { // leere sky.png (alte Testsätze): Himmel aus der Tiefe wie Scene ohne sky.png
            for (int i = 0; i < n; i++) sky[i] = Math.max(0, Math.min(1, 0.5f + (0.006f - v[i] * v[i]) / 0.006f));
        }
        float[] s = EdgeRefine.refineSky(px, lum, sky, w, h, k);
        s = EdgeRefine.widenToBlur(lum, sky, s, w, h, Math.max(2, Math.round(48 * k)), Math.max(1, Math.round(24 * k)));
        int[] land = EdgeRefine.landColors(px, s, w, h, Math.max(2, Math.round(48 * k)));
        System.out.println("ms " + (System.currentTimeMillis() - t));
        double mean = 0;
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int i = 0; i < n; i++) {
            mean += s[i];
            float sa = s[i];
            int c = land[i];
            int r = Math.round(77 * sa + ((c >> 16) & 0xFF) * 0.6f * (1 - sa));
            int g = Math.round(82 * sa + ((c >> 8) & 0xFF) * 0.6f * (1 - sa));
            int b = Math.round(92 * sa + (c & 0xFF) * 0.6f * (1 - sa));
            out.setRGB(i % w, i / w, (r << 16) | (g << 8) | b);
        }
        System.out.println("Himmelsanteil " + mean / n);
        ImageIO.write(out, "png", new File(d, "java_night.png"));

        // Nachthimmel alt (harte Maske, Land davor) | neu (ganzes Foto, Himmel weich darüber, EdgeRefine.softSky)
        float[] soft = EdgeRefine.softSky(s, w, h, k, 1f);
        BufferedImage cmp = new BufferedImage(w * 2 + 8, h, BufferedImage.TYPE_INT_RGB);
        for (int i = 0; i < n; i++) {
            int x = i % w, y = i / w;
            int night = nightSky(x, y, h);
            cmp.setRGB(x, y, mix(night, dim(land[i]), 1 - s[i]));
            cmp.setRGB(w + 8 + x, y, mix(dim(px[i]), night, soft[i]));
        }
        ImageIO.write(cmp, "png", new File(d, "sky_compare.png"));
    }

    /** Verlauf von tiefem Blau oben zu hellerem Blau am Horizont, dazu verstreute Sterne. */
    private static int nightSky(int x, int y, int h) {
        float t = Math.min(1f, y / (h * 0.5f));
        int r = Math.round(10 + 30 * t), g = Math.round(16 + 36 * t), b = Math.round(46 + 44 * t);
        int hsh = (x * 73856093) ^ (y * 19349663);
        if ((hsh & 0x3FF) == 7) return 0xE6EAF5;
        return (r << 16) | (g << 8) | b;
    }

    private static int dim(int c) {
        return (Math.round(((c >> 16) & 0xFF) * 0.6f) << 16) | (Math.round(((c >> 8) & 0xFF) * 0.6f) << 8)
                | Math.round((c & 0xFF) * 0.6f);
    }

    /** a über b mit Deckung t von a. */
    private static int mix(int b, int a, float t) {
        int r = Math.round(((a >> 16) & 0xFF) * t + ((b >> 16) & 0xFF) * (1 - t));
        int g = Math.round(((a >> 8) & 0xFF) * t + ((b >> 8) & 0xFF) * (1 - t));
        int bl = Math.round((a & 0xFF) * t + (b & 0xFF) * (1 - t));
        return (r << 16) | (g << 8) | bl;
    }

    private static int[] scaled(BufferedImage src, int w, int h) {
        BufferedImage b = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = b.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return b.getRGB(0, 0, w, h, null, 0, w);
    }
}
