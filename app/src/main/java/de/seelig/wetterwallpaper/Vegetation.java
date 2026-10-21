package de.seelig.wetterwallpaper;

/**
 * Pflanzen am Farbton erkennen (gelbgrün bis blaugrün, nicht grau): Jahreszeiten färben nur Laub, Gras
 * und Feld, nicht Himmel, Straße oder Hauswand. Ohne Android-Abhängigkeiten, Selbsttest am PC.
 */
final class Vegetation {
    private Vegetation() {
    }

    /** Wie sehr ein Pixel nach Pflanze aussieht, 0..1. */
    static float amount(int c) {
        float r = ((c >> 16) & 0xFF) / 255f;
        float g = ((c >> 8) & 0xFF) / 255f;
        float b = (c & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float chroma = max - min;
        if (max < 0.03f || chroma < 0.02f) return 0;
        float hue = hue(r, g, b, max, chroma); // Grad
        // gelbgrün (Weizen ~60°) bis blaugrün (Nadelwald ~160°), weich auslaufend
        float inHue = smoothstep(42, 62, hue) * (1 - smoothstep(165, 190, hue));
        // Sättigung relativ zur Helligkeit: dunkles Laub im Schatten zählt, graue Flächen nicht
        float sat = chroma / max;
        return inHue * smoothstep(0.12f, 0.3f, sat) * smoothstep(0.03f, 0.1f, max);
    }

    /** Pflanzenmaske für ein ganzes Bild, leicht geglättet (sonst flimmern einzelne Pixel). */
    static float[] mask(int[] px, int w, int h, float k) {
        float[] m = new float[px.length];
        for (int i = 0; i < m.length; i++) m[i] = amount(px[i]);
        return EdgeRefine.box(m, w, h, Math.max(1, Math.round(2 * k)));
    }

    private static float hue(float r, float g, float b, float max, float chroma) {
        float h;
        if (max == r) h = ((g - b) / chroma) % 6;
        else if (max == g) h = (b - r) / chroma + 2;
        else h = (r - g) / chroma + 4;
        h *= 60;
        return h < 0 ? h + 360 : h;
    }

    private static float smoothstep(float a, float b, float x) {
        float t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    /** Selbsttest: javac/java mit EdgeRefine.java. */
    public static void main(String[] args) {
        check(amount(0xFF3C7A2A) > 0.9f, "Laubgrün");
        check(amount(0xFF1E3A1C) > 0.6f, "Laub im Schatten");
        check(amount(0xFFB8C24A) > 0.9f, "Weizen gelbgrün");
        check(amount(0xFF2F5A4A) > 0.5f, "Nadelwald blaugrün");
        check(amount(0xFF6A9AD8) == 0, "Himmelblau");
        check(amount(0xFF808080) == 0, "Asphalt grau");
        check(amount(0xFFF0F0EC) == 0, "Hauswand weiß");
        check(amount(0xFFB04A30) == 0, "Ziegelrot");
        check(amount(0xFFC8A070) < 0.1f, "Sand/Holz");
        System.out.println("Vegetation ok");
    }

    private static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError(what);
    }
}
