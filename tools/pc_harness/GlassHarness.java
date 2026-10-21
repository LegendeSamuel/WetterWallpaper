package de.seelig.wetterwallpaper;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** PC-Vorschau Fenster: Frost wenig | Frost viel | Regen nach 20 s. Aufruf: GlassHarness <bild> <ausgabe.png> */
public class GlassHarness {
    public static void main(String[] a) throws Exception {
        BufferedImage bg = ImageIO.read(new File(a[0]));
        int w = bg.getWidth(), h = bg.getHeight();
        int fw = w / 2, fh = h / 2; // wie im Renderer: Frost in halber Auflösung
        int[] tex = Frost.texture(fw, fh, 17);
        int[] cut = new int[tex.length];
        BufferedImage sheet = new BufferedImage(w * 3 + 16, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D sg = sheet.createGraphics();
        float[] amounts = {0.35f, 0.8f};
        for (int k = 0; k < amounts.length; k++) {
            BufferedImage img = copy(bg);
            Frost.apply(tex, amounts[k], cut);
            BufferedImage f = new BufferedImage(fw, fh, BufferedImage.TYPE_INT_ARGB);
            f.setRGB(0, 0, fw, fh, cut, 0, fw);
            Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
            g.drawImage(f, 0, 0, w, h, null);
            sg.drawImage(img, k * (w + 8), 0, null);
        }
        RainOnGlass rain = new RainOnGlass(w, h);
        for (int i = 0; i < 30 * 20; i++) rain.update(0.7f, 0.033f);
        BufferedImage img = copy(bg);
        int size = 64;
        BufferedImage sp = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        sp.setRGB(0, 0, size, size, RainOnGlass.sprite(size, true), 0, size);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        for (int i = 0; i < rain.count; i++) {
            lens(img, bg, rain.x[i], rain.y[i], rain.r[i]);
            drop(g, sp, rain.x[i], rain.y[i], rain.r[i], 1f);
        }
        for (int k = 0; k < RainOnGlass.MAX_RUNNERS; k++) {
            if (!rain.active[k]) continue;
            lens(img, bg, rain.rx[k], rain.ry[k], rain.rr[k]);
            drop(g, sp, rain.rx[k], rain.ry[k], rain.rr[k], rain.moving[k] ? 1.25f : 1f);
        }
        sg.drawImage(img, 2 * (w + 8), 0, null);
        ImageIO.write(sheet, "png", new File(a[1]));
    }

    /** Wie WindowGlass.lens: der Tropfen zeigt einen 3,5-mal größeren Ausschnitt kopfüber. */
    private static void lens(BufferedImage img, BufferedImage bg, float cx, float cy, float r) {
        int w = bg.getWidth(), h = bg.getHeight();
        for (int y = (int) (cy - r); y <= cy + r; y++) {
            for (int x = (int) (cx - r); x <= cx + r; x++) {
                if (x < 0 || y < 0 || x >= w || y >= h || (x - cx) * (x - cx) + (y - cy) * (y - cy) > r * r) continue;
                int sx = Math.round(cx - (x - cx) * 3.5f), sy = Math.round(cy - (y - cy) * 3.5f);
                img.setRGB(x, y, bg.getRGB(Math.max(0, Math.min(w - 1, sx)), Math.max(0, Math.min(h - 1, sy))));
            }
        }
    }

    private static void drop(Graphics2D g, BufferedImage sp, float x, float y, float r, float stretch) {
        float rh = r * stretch;
        g.drawImage(sp, Math.round(x - r), Math.round(y - rh - (rh - r)), Math.round(2 * r), Math.round(2 * rh), null);
    }

    private static BufferedImage copy(BufferedImage b) {
        BufferedImage c = new BufferedImage(b.getWidth(), b.getHeight(), BufferedImage.TYPE_INT_RGB);
        c.createGraphics().drawImage(b, 0, 0, null);
        return c;
    }
}
