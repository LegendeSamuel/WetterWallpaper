package de.seelig.wetterwallpaper;

/** Himmel = sehr fern UND zusammenhängend mit dem oberen Bildrand (sonst wäre jede ferne Wand "Himmel"). */
final class SkyMask {
    private SkyMask() {
    }

    static boolean[] compute(float[] near, int w, int h, float maxNear) {
        boolean[] sky = new boolean[w * h];
        int[] stack = new int[w * h];
        int top = 0;
        for (int x = 0; x < w; x++) {
            if (near[x] < maxNear) {
                sky[x] = true;
                stack[top++] = x;
            }
        }
        while (top > 0) {
            int i = stack[--top];
            int x = i % w;
            int y = i / w;
            if (x > 0) top = visit(near, sky, stack, top, i - 1, maxNear);
            if (x < w - 1) top = visit(near, sky, stack, top, i + 1, maxNear);
            if (y > 0) top = visit(near, sky, stack, top, i - w, maxNear);
            if (y < h - 1) top = visit(near, sky, stack, top, i + w, maxNear);
        }
        return sky;
    }

    private static int visit(float[] near, boolean[] sky, int[] stack, int top, int j, float maxNear) {
        if (!sky[j] && near[j] < maxNear) {
            sky[j] = true;
            stack[top++] = j;
        }
        return top;
    }

    /** Selbsttest: java app/src/main/java/de/seelig/wetterwallpaper/SkyMask.java */
    public static void main(String[] args) {
        int w = 4, h = 4;
        // Landschaft: oben fern (Himmel), unten nah
        float[] landscape = {
                0, 0, 0, 0,
                0, 0, .5f, 0,
                .5f, .5f, .5f, .5f,
                .9f, .9f, .9f, .9f};
        boolean[] s = compute(landscape, w, h, 0.01f);
        check(s[0] && s[7] && !s[6] && !s[8], "Himmel oben erkannt, Berg nicht");
        // Tempeltor: oben nahes Dach, ferne Wand nur im Durchgang -> kein Himmel
        float[] gate = {
                .8f, .8f, .8f, .8f,
                .8f, 0, 0, .8f,
                .8f, 0, 0, .8f,
                .9f, .9f, .9f, .9f};
        for (boolean b : compute(gate, w, h, 0.01f)) check(!b, "ferne Wand im Tor ist kein Himmel");
        System.out.println("SkyMask ok");
    }

    private static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError(what);
    }
}
