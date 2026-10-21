package de.seelig.wetterwallpaper;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Passende Schriftfarben aus dem eigenen Foto: häufige, deutlich verschiedene Farbtöne, kräftige bevorzugt.
 * Selbsttest: javac -cp android.jar, dann main (ruft nur pick() auf).
 */
final class PhotoColors {
    private PhotoColors() {
    }

    /** Bis zu 5 Farben aus background.png, leer wenn kein Bild. Nicht im Hauptthread aufrufen. */
    static int[] fromImage(File file) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getPath(), o);
        if (o.outWidth <= 0) return new int[0];
        o.inJustDecodeBounds = false;
        o.inSampleSize = Math.max(1, o.outWidth / 96); // ~100 px breit reicht für Farbanteile
        Bitmap b = BitmapFactory.decodeFile(file.getPath(), o);
        if (b == null) return new int[0];
        int[] px = new int[b.getWidth() * b.getHeight()];
        b.getPixels(px, 0, b.getWidth(), 0, 0, b.getWidth(), b.getHeight());
        return pick(px, 5);
    }

    /** Farben in 4-Bit-Fächer sortieren, nach Anteil × Sättigung wählen, nur deutlich verschiedene behalten. */
    static int[] pick(int[] px, int max) {
        int[] count = new int[4096];
        long[] sum = new long[4096 * 3];
        for (int c : px) {
            int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
            int k = (r >> 4) << 8 | (g >> 4) << 4 | (b >> 4);
            count[k]++;
            sum[k * 3] += r;
            sum[k * 3 + 1] += g;
            sum[k * 3 + 2] += b;
        }
        List<int[]> buckets = new ArrayList<>(); // {Farbe, Wert*1000}
        for (int k = 0; k < 4096; k++) {
            if (count[k] == 0) continue;
            int r = (int) (sum[k * 3] / count[k]), g = (int) (sum[k * 3 + 1] / count[k]), b = (int) (sum[k * 3 + 2] / count[k]);
            int hi = Math.max(r, Math.max(g, b)), lo = Math.min(r, Math.min(g, b));
            float sat = hi == 0 ? 0 : (hi - lo) / (float) hi;
            buckets.add(new int[]{0xFF000000 | r << 16 | g << 8 | b, Math.round(count[k] * (0.35f + sat) * 1000)});
        }
        buckets.sort((a, b) -> Integer.compare(b[1], a[1]));
        List<Integer> out = new ArrayList<>();
        for (int[] cand : buckets) {
            boolean distinct = true;
            for (int c : out) if (dist(c, cand[0]) < 70) distinct = false;
            if (distinct) out.add(cand[0]);
            if (out.size() == max) break;
        }
        int[] res = new int[out.size()];
        for (int i = 0; i < res.length; i++) res[i] = out.get(i);
        return res;
    }

    /**
     * Farben fürs Werkzeug: bis zu 4 aus dem Foto, dazu die Gegenfarbe der kräftigsten und ein heller Ton der
     * häufigsten (passen zum Bild, ohne darin vorzukommen). Höchstens 6.
     */
    static int[] withHarmonies(int[] photo) {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < Math.min(4, photo.length); i++) out.add(photo[i]);
        if (photo.length > 0) {
            int vivid = photo[0];
            float best = -1;
            for (int c : photo) {
                float s = hsv(c)[1];
                if (s > best) {
                    best = s;
                    vivid = c;
                }
            }
            float[] v = hsv(vivid);
            addDistinct(out, fromHsv((v[0] + 180) % 360, Math.max(0.45f, v[1]), Math.max(0.75f, v[2])));
            float[] d = hsv(photo[0]);
            addDistinct(out, fromHsv(d[0], Math.min(0.25f, d[1]), 0.96f));
        }
        int[] res = new int[Math.min(6, out.size())];
        for (int i = 0; i < res.length; i++) res[i] = out.get(i);
        return res;
    }

    private static void addDistinct(List<Integer> out, int c) {
        for (int o : out) if (dist(o, c) < 50) return;
        out.add(c);
    }

    /** {Farbton 0..360, Sättigung 0..1, Helligkeit 0..1} ohne Android, damit main() am PC läuft. */
    static float[] hsv(int c) {
        float r = ((c >> 16) & 0xFF) / 255f, g = ((c >> 8) & 0xFF) / 255f, b = (c & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), d = max - min;
        float h = 0;
        if (d > 0) {
            if (max == r) h = 60 * (((g - b) / d) % 6);
            else if (max == g) h = 60 * ((b - r) / d + 2);
            else h = 60 * ((r - g) / d + 4);
        }
        return new float[]{(h + 360) % 360, max == 0 ? 0 : d / max, max};
    }

    static int fromHsv(float h, float s, float v) {
        float c = v * s, x = c * (1 - Math.abs((h / 60) % 2 - 1)), m = v - c;
        float r, g, b;
        if (h < 60) { r = c; g = x; b = 0; }
        else if (h < 120) { r = x; g = c; b = 0; }
        else if (h < 180) { r = 0; g = c; b = x; }
        else if (h < 240) { r = 0; g = x; b = c; }
        else if (h < 300) { r = x; g = 0; b = c; }
        else { r = c; g = 0; b = x; }
        return 0xFF000000 | Math.round((r + m) * 255) << 16 | Math.round((g + m) * 255) << 8 | Math.round((b + m) * 255);
    }

    private static double dist(int a, int b) {
        int dr = ((a >> 16) & 0xFF) - ((b >> 16) & 0xFF);
        int dg = ((a >> 8) & 0xFF) - ((b >> 8) & 0xFF);
        int db = (a & 0xFF) - (b & 0xFF);
        return Math.sqrt(dr * dr + dg * dg + db * db);
    }

    public static void main(String[] args) {
        int[] px = new int[1000];
        for (int i = 0; i < px.length; i++) {
            if (i < 600) px[i] = 0xFF203040 + (i % 3);   // viel dunkles Blaugrau
            else if (i < 900) px[i] = 0xFFE08020;        // Orange
            else px[i] = 0xFF2A3A4A;                     // fast dasselbe Blaugrau
        }
        int[] c = pick(px, 5);
        if (c.length != 2) throw new AssertionError("zwei verschiedene Farben erwartet, war " + c.length);
        if ((c[1] & 0xFFFFFF) != 0xE08020) throw new AssertionError("Orange als zweite Farbe, war " + Integer.toHexString(c[1]));
        if (pick(new int[0], 5).length != 0) throw new AssertionError("leeres Bild");
        int[] h = withHarmonies(c);
        if (h.length != 4) throw new AssertionError("2 Fotofarben + Gegenfarbe + heller Ton, war " + h.length);
        float hue = hsv(h[2])[0];
        if (Math.abs(hue - (hsv(0xFFE08020)[0] + 180) % 360) > 2) throw new AssertionError("Gegenfarbe zu Orange, war " + hue);
        if (fromHsv(0, 0, 1) != 0xFFFFFFFF || (fromHsv(120, 1, 1) & 0xFFFFFF) != 0x00FF00) throw new AssertionError("HSV");
        System.out.println("PhotoColors ok");
    }
}
