package de.seelig.wetterwallpaper;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;

/**
 * Ruhige Linien-Symbole fürs Wetter-Widget statt Emoji. Farbe und Schatten kommen von der Textfarbe,
 * die Größe von der Schriftgröße; gezeichnet wird auf der Grundlinie des Textes.
 */
final class WeatherIcon {
    private static final Paint LINE = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint DOT = new Paint(Paint.ANTI_ALIAS_FLAG);

    static {
        LINE.setStyle(Paint.Style.STROKE);
        LINE.setStrokeCap(Paint.Cap.ROUND);
        LINE.setStrokeJoin(Paint.Join.ROUND);
    }

    private WeatherIcon() {
    }

    /** Breite, die ein Symbol in der Zeile einnimmt. */
    static float advance(float size) {
        return size * 1.05f;
    }

    /** Art des Symbols aus dem Wetter (wie Weather.weatherSymbol, nur als Name). */
    static String kind(Weather wx, boolean night) {
        if (wx.thunder) return "storm";
        if (wx.snow > 0) return "snow";
        if (wx.rain > 0) return "rain";
        if (wx.fog > 0.4f) return "fog";
        if (wx.cloud > 0.7f) return "cloud";
        if (wx.cloud > 0.2f) return night ? "partly-night" : "partly-day";
        return night ? "clear-night" : "clear-day";
    }

    static void draw(Canvas c, String kind, float left, float baseline, float size, Paint text) {
        float u = size * 0.9f;            // Kantenlänge des Symbols
        float cx = left + u / 2 + size * 0.05f;
        float cy = baseline - size * 0.36f; // Mitte etwa auf Höhe der Kleinbuchstaben
        // Farbe und Effekt-Durchgang des Textes übernehmen (Weichzeichnung, Verlauf/Bild, Konturbreite)
        float outline = text.getStyle() == Paint.Style.STROKE ? text.getStrokeWidth() : 0;
        LINE.setStrokeWidth(Math.max(1.5f, size * 0.075f) + outline);
        LINE.setColor(text.getColor());
        LINE.setShader(text.getShader());
        LINE.setColorFilter(text.getColorFilter());
        LINE.setMaskFilter(text.getMaskFilter());
        DOT.set(LINE);
        DOT.setStyle(Paint.Style.FILL);
        switch (kind) {
            case "clear-day":
                sun(c, cx, cy, u * 0.2f, u);
                break;
            case "clear-night":
                c.drawPath(moon(cx, cy, u * 0.3f), LINE);
                break;
            case "partly-day":
            case "partly-night": {
                Path cloud = cloud(cx + u * 0.08f, cy + u * 0.1f, u * 0.82f);
                c.save();
                c.clipOutPath(cloud); // Sonne/Mond hinter der Wolke nicht durchscheinen lassen
                if (kind.equals("partly-day")) sun(c, cx - u * 0.18f, cy - u * 0.16f, u * 0.13f, u * 0.65f);
                else c.drawPath(moon(cx - u * 0.16f, cy - u * 0.14f, u * 0.2f), LINE);
                c.restore();
                c.drawPath(cloud, LINE);
                break;
            }
            case "rain":
                c.drawPath(cloud(cx, cy - u * 0.12f, u), LINE);
                for (int i = -1; i <= 1; i++) {
                    float x = cx + i * u * 0.22f;
                    c.drawLine(x + u * 0.04f, cy + u * 0.22f, x - u * 0.04f, cy + u * 0.4f, LINE);
                }
                break;
            case "snow":
                c.drawPath(cloud(cx, cy - u * 0.12f, u), LINE);
                for (int i = -1; i <= 1; i++) {
                    c.drawCircle(cx + i * u * 0.22f, cy + u * (i == 0 ? 0.38f : 0.3f), LINE.getStrokeWidth() * 0.9f, DOT);
                }
                break;
            case "storm": {
                c.drawPath(cloud(cx, cy - u * 0.12f, u), LINE);
                Path bolt = new Path();
                bolt.moveTo(cx + u * 0.05f, cy + u * 0.16f);
                bolt.lineTo(cx - u * 0.08f, cy + u * 0.32f);
                bolt.lineTo(cx + u * 0.06f, cy + u * 0.32f);
                bolt.lineTo(cx - u * 0.06f, cy + u * 0.48f);
                c.drawPath(bolt, LINE);
                break;
            }
            case "fog":
                c.drawLine(cx - u * 0.38f, cy - u * 0.18f, cx + u * 0.3f, cy - u * 0.18f, LINE);
                c.drawLine(cx - u * 0.3f, cy, cx + u * 0.38f, cy, LINE);
                c.drawLine(cx - u * 0.38f, cy + u * 0.18f, cx + u * 0.22f, cy + u * 0.18f, LINE);
                break;
            default: // cloud
                c.drawPath(cloud(cx, cy, u), LINE);
        }
    }

    private static void sun(Canvas c, float cx, float cy, float r, float u) {
        c.drawCircle(cx, cy, r, LINE);
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4;
            float cos = (float) Math.cos(a);
            float sin = (float) Math.sin(a);
            c.drawLine(cx + cos * (r + u * 0.1f), cy + sin * (r + u * 0.1f), cx + cos * (r + u * 0.2f), cy + sin * (r + u * 0.2f), LINE);
        }
    }

    private static Path moon(float cx, float cy, float r) {
        Path m = new Path();
        m.addCircle(cx, cy, r, Path.Direction.CW);
        Path cut = new Path();
        cut.addCircle(cx + r * 0.55f, cy - r * 0.35f, r * 0.85f, Path.Direction.CW);
        m.op(cut, Path.Op.DIFFERENCE);
        return m;
    }

    /** Wolke aus drei Kreisen und flachem Boden, Breite w um (cx, cy). */
    private static Path cloud(float cx, float cy, float w) {
        Path p = new Path();
        p.addCircle(cx - w * 0.2f, cy + w * 0.04f, w * 0.17f, Path.Direction.CW);
        Path part = new Path();
        part.addCircle(cx + w * 0.03f, cy - w * 0.07f, w * 0.23f, Path.Direction.CW);
        p.op(part, Path.Op.UNION);
        part.reset();
        part.addCircle(cx + w * 0.24f, cy + w * 0.06f, w * 0.15f, Path.Direction.CW);
        p.op(part, Path.Op.UNION);
        part.reset();
        part.addRoundRect(cx - w * 0.37f, cy + w * 0.04f, cx + w * 0.39f, cy + w * 0.21f, w * 0.085f, w * 0.085f, Path.Direction.CW);
        p.op(part, Path.Op.UNION);
        return p;
    }
}
