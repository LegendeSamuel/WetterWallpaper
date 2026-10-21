package de.seelig.wetterwallpaper;

import java.util.Random;

/**
 * Regentropfen auf der Scheibe. Reines Java (Selbsttest per main).
 * Ruhende Tropfen kommen mit dem Regen dazu; einzelne große laufen ruckweise herunter, schlucken
 * die Tropfen auf ihrem Weg (freie Spur) und lassen kleine Reste zurück. Nach dem Regen trocknet die
 * Scheibe in einigen Minuten ab.
 */
final class RainOnGlass {
    static final int MAX_DROPS = 900;
    static final int MAX_RUNNERS = 7;
    /** So viel Radius verliert ein Tropfen beim Trocknen pro Sekunde (bei 1080 px Breite). */
    private static final float DRY_PER_SECOND = 4f / 60f;

    private final int width;
    private final int height;
    private final float unit; // 1 bei 1080 px Breite
    private final Random rnd = new Random();

    // ruhende Tropfen: Mitte und Radius
    final float[] x = new float[MAX_DROPS];
    final float[] y = new float[MAX_DROPS];
    final float[] r = new float[MAX_DROPS];
    int count;

    // laufende Tropfen
    final float[] rx = new float[MAX_RUNNERS];
    final float[] ry = new float[MAX_RUNNERS];
    final float[] rr = new float[MAX_RUNNERS];
    final boolean[] moving = new boolean[MAX_RUNNERS];
    final boolean[] active = new boolean[MAX_RUNNERS];
    private final float[] speed = new float[MAX_RUNNERS];
    private final float[] timer = new float[MAX_RUNNERS]; // Restzeit der aktuellen Phase (laufen/stocken)
    private final float[] trail = new float[MAX_RUNNERS]; // Strecke bis zum nächsten Rest-Tropfen
    private final float[] phase = new float[MAX_RUNNERS];

    RainOnGlass(int width, int height) {
        this.width = width;
        this.height = height;
        this.unit = width / 1080f;
    }

    /** rain 0..1, dt in Sekunden. Ohne Regen laufen die letzten Läufer aus und die Tropfen trocknen. */
    void update(float rain, float dt) {
        if (rain <= 0) {
            dry(dt);
            for (int i = 0; i < MAX_RUNNERS; i++) if (active[i]) run(i, dt);
            return;
        }
        if (count == 0) fill(rain); // Regen läuft schon: Scheibe gleich nass zeigen
        float arrivals = rain * 70 * dt;
        while (arrivals > 1 || rnd.nextFloat() < arrivals) {
            addDrop(rnd.nextFloat() * width, rnd.nextFloat() * height, newRadius());
            arrivals--;
        }
        int want = Math.max(1, Math.round(MAX_RUNNERS * rain));
        for (int i = 0; i < MAX_RUNNERS; i++) {
            if (!active[i] && i < want && rnd.nextFloat() < dt * 0.6f) launch(i);
            if (active[i]) run(i, dt);
        }
    }

    /** Tropfen schrumpfen und verschwinden, große halten am längsten (gut 3 Minuten). */
    void dry(float dt) {
        float shrink = unit * DRY_PER_SECOND * dt;
        for (int i = count - 1; i >= 0; i--) {
            r[i] -= shrink;
            if (r[i] < unit * 1.2f) removeDrop(i);
        }
    }

    boolean running() {
        for (boolean a : active) if (a) return true;
        return false;
    }

    boolean wet() {
        return count > 0 || running();
    }

    private void fill(float rain) {
        int n = Math.round(MAX_DROPS * (0.4f + 0.5f * rain));
        for (int i = 0; i < n; i++) addDrop(rnd.nextFloat() * width, rnd.nextFloat() * height, newRadius());
    }

    /** Meist kleine Tropfen, selten große. */
    private float newRadius() {
        float u = rnd.nextFloat();
        return unit * (2.2f + 11f * u * u * u);
    }

    private void addDrop(float px, float py, float pr) {
        if (count < MAX_DROPS) {
            x[count] = px;
            y[count] = py;
            r[count] = pr;
            count++;
        } else { // volle Scheibe: ein alter Tropfen verdunstet bzw. geht im neuen auf
            int i = rnd.nextInt(MAX_DROPS);
            x[i] = px;
            y[i] = py;
            r[i] = pr;
        }
    }

    private void removeDrop(int i) {
        count--;
        x[i] = x[count];
        y[i] = y[count];
        r[i] = r[count];
    }

    /** Ein Tropfen im oberen Drittel wird schwer und beginnt zu laufen. */
    private void launch(int k) {
        active[k] = true;
        rx[k] = rnd.nextFloat() * width;
        ry[k] = rnd.nextFloat() * height * 0.35f;
        rr[k] = unit * (9f + rnd.nextFloat() * 4f);
        moving[k] = true;
        speed[k] = unit * (180 + rnd.nextFloat() * 260);
        timer[k] = 0.3f + rnd.nextFloat() * 1.2f;
        trail[k] = unit * 10;
        phase[k] = rnd.nextFloat() * 6.28f;
    }

    private void run(int k, float dt) {
        timer[k] -= dt;
        if (timer[k] <= 0) { // ruckweise: laufen, kurz hängen bleiben, weiterlaufen
            moving[k] = !moving[k];
            timer[k] = moving[k] ? 0.3f + rnd.nextFloat() * 1.4f : 0.1f + rnd.nextFloat() * 0.6f;
            speed[k] = unit * (180 + rnd.nextFloat() * 260);
        }
        if (!moving[k]) return;
        float dy = speed[k] * dt;
        ry[k] += dy;
        rx[k] += (float) Math.sin(ry[k] / (40 * unit) + phase[k]) * 0.25f * dy; // leichtes Schlängeln
        // was im Weg liegt, wird geschluckt
        for (int i = count - 1; i >= 0; i--) {
            float ddx = x[i] - rx[k], ddy = y[i] - ry[k];
            float reach = rr[k] + r[i];
            if (ddx * ddx + ddy * ddy < reach * reach) {
                rr[k] = Math.min(unit * 16, cbrt(rr[k] * rr[k] * rr[k] + r[i] * r[i] * r[i]));
                removeDrop(i);
            }
        }
        trail[k] -= dy;
        if (trail[k] <= 0) { // kleiner Rest in der Spur, der Läufer verliert dafür Wasser
            float d = rr[k] * (0.25f + rnd.nextFloat() * 0.2f);
            addDrop(rx[k] + (rnd.nextFloat() - 0.5f) * rr[k] * 0.6f, ry[k] - rr[k] * 1.2f, d);
            rr[k] = cbrt(Math.max(0, rr[k] * rr[k] * rr[k] - d * d * d));
            trail[k] = unit * (8 + rnd.nextFloat() * 22);
        }
        if (ry[k] - rr[k] > height || rr[k] < unit * 5f) {
            if (ry[k] - rr[k] <= height) addDrop(rx[k], ry[k], rr[k]); // zu klein: bleibt liegen
            active[k] = false;
        }
    }

    /**
     * Vorgerenderter Tropfen (size × size, ARGB). Ein Tropfen ist eine Linse: oben dunkel (zeigt den
     * Boden kopfüber), unten hell (zeigt den Himmel), dunkler Rand, Glanzpunkt oben links.
     * refracted: darunter liegt schon das echte Bild kopfüber, dann nur Rand, Lichtsichel und Glanz.
     */
    static int[] sprite(int size, boolean refracted) {
        float body = refracted ? 0.25f : 1f;
        int[] px = new int[size * size];
        float c = (size - 1) / 2f;
        for (int j = 0; j < size; j++) {
            for (int i = 0; i < size; i++) {
                float u = (i - c) / c, v = (j - c) / c;
                float d = (float) Math.sqrt(u * u + v * v);
                float inside = clamp01((1 - d) * c); // 1 px Kantenglättung
                if (inside <= 0) continue;
                // Schichten von unten nach oben mischen (vormultipliziert)
                float[] acc = {0, 0, 0, 0};
                over(acc, 1f, body * (0.16f + 0.22f * clamp01(v * 0.5f + 0.5f))); // Körper, unten heller
                over(acc, 0f, body * 0.30f * clamp01(-v));                         // oben dunkel
                over(acc, 0f, 0.55f * smooth(0.72f, 1f, d) * (0.6f + 0.4f * clamp01(-v + 0.3f))); // Rand
                float caustic = smooth(0.45f, 0.75f, v) * (1 - smooth(0.82f, 0.95f, d));
                over(acc, 1f, 0.45f * caustic);                                  // Lichtsichel unten
                float hx = u + 0.38f, hy = v + 0.42f;
                over(acc, 1f, 0.95f * (1 - smooth(0.05f, 0.22f, (float) Math.sqrt(hx * hx * 1.4f + hy * hy)))); // Glanz
                int al = Math.round(acc[3] * inside * 255);
                if (al == 0) continue;
                // ARGB nicht vormultipliziert: Farbe = Summe / Alpha
                int g = Math.round(clamp01(acc[0] / Math.max(acc[3], 1e-4f)) * 255);
                px[j * size + i] = (al << 24) | (g << 16) | (g << 8) | Math.min(255, g + 6);
            }
        }
        return px;
    }

    /** Graustufe gray mit Deckung a über acc (vormultipliziert: acc[0] = Farbe, acc[3] = Alpha). */
    private static void over(float[] acc, float gray, float a) {
        a = clamp01(a);
        acc[0] = gray * a + acc[0] * (1 - a);
        acc[3] = a + acc[3] * (1 - a);
    }

    private static float cbrt(float v) {
        return (float) Math.cbrt(v);
    }

    private static float smooth(float a, float b, float x) {
        float t = clamp01((x - a) / (b - a));
        return t * t * (3 - 2 * t);
    }

    private static float clamp01(float x) {
        return x < 0 ? 0 : (x > 1 ? 1 : x);
    }

    /** Selbsttest: Regen macht nass, Läufer kommen unten an und lassen eine Spur, ohne Regen trocken. */
    public static void main(String[] args) {
        RainOnGlass g = new RainOnGlass(1080, 2340);
        g.update(0.7f, 0.033f);
        check(g.count > 100, "Regen macht die Scheibe nass");
        boolean ran = false;
        for (int f = 0; f < 30 * 40; f++) {
            g.update(0.7f, 0.033f);
            for (int k = 0; k < MAX_RUNNERS; k++) ran |= g.active[k] && g.ry[k] > 1500;
            check(g.count <= MAX_DROPS, "höchstens MAX_DROPS");
        }
        check(ran, "Läufer kommen weit nach unten");
        g.update(0, 1f);
        check(g.count > 0, "nach dem Regen nicht sofort trocken");
        for (int t = 0; t < 300; t++) g.update(0, 1f);
        check(!g.wet(), "nach 5 Minuten trocken");
        int[] s = sprite(48, false);
        check((s[24 * 48 + 24] >>> 24) > 0 && s[0] == 0, "Tropfen rund, Ecke leer");
        int top = s[8 * 48 + 24] & 0xFF, bottom = s[40 * 48 + 24] & 0xFF;
        check(bottom > top, "Linse: unten heller als oben");
        System.out.println("RainOnGlass ok");
    }

    private static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError(what);
    }
}
