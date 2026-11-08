package de.seelig.wetterwallpaper;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Stil eines Textes auf dem Wallpaper (Uhr oder Widget) aus seinem config-Abschnitt: Schrift und Stärke, Größe,
 * Laufweite, Farbe (automatisch oder Tag/Nacht), Deckkraft, Ausrichtung, Großbuchstaben und Effekte.
 * Gezeichnet wird in Durchgängen in einer eigenen Ebene: Effekt (Schatten, Leuchten, Neon, langer Schatten,
 * Retro-Versatz), Kontur, Füllung (Farbe, Verlauf, Milchglas, Bild, hohl), zuletzt Ausblenden als Maske.
 */
final class TextLayer {
    final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** Standard-Schatten (Weichheit, Abstand, Stärke; je 0..100) – ergibt den bisherigen Look. */
    static final int[] CLOCK_SHADOW = {20, 5, 60};
    static final int[] WIDGET_SHADOW = {33, 15, 75};
    /** Standardwerte der Effekt-Regler (0..100 bzw. Grad). */
    private static final Map<String, Integer> FX = Map.ofEntries(
            Map.entry("glowSize", 32), Map.entry("glowStrength", 85),
            Map.entry("neonSize", 50), Map.entry("neonStrength", 90),
            Map.entry("longLength", 40), Map.entry("longAngle", 45), Map.entry("longStrength", 70),
            Map.entry("retroDistance", 30), Map.entry("retroAngle", 135),
            Map.entry("outlineWidth", 25), Map.entry("hollowWidth", 30),
            Map.entry("gradientAngle", 90), Map.entry("glassFrost", 50), Map.entry("glassBlur", 40), Map.entry("glassEdge", 50),
            Map.entry("photoBright", 60), Map.entry("gradientMix", 60),
            Map.entry("fadeStart", 50), Map.entry("fadeLength", 40), Map.entry("fadeAngle", 90));
    /** Standardfarben der Effekte ("auto" bedeutet diese Farbe). */
    static final Map<String, String> FX_COLOR = Map.of(
            "neonColor", "#7FE7FF", "retroColor1", "#FF6B6B", "retroColor2", "#4FC3F7", "gradientColor", "#BFD8FF");

    static int fx(String key) {
        return FX.getOrDefault(key, 50);
    }

    private final int[] shadowDefaults;
    private final Paint work = new Paint(Paint.ANTI_ALIAS_FLAG);
    private JSONObject cfg = new JSONObject();
    private int shown = Color.WHITE;
    // für die Füllungen Bild und Glas, je Frame vom Renderer gesetzt
    private Bitmap photo;
    private Bitmap photoMid;
    private Bitmap photoSoft;
    private final Matrix photoMatrix = new Matrix();
    private final Matrix refract = new Matrix();
    private ColorMatrix grade = new ColorMatrix();

    TextLayer(int[] shadowDefaults) {
        this.shadowDefaults = shadowDefaults;
        paint.setTextAlign(Paint.Align.LEFT); // Ausrichtung rechnen wir selbst (left())
    }

    void apply(JSONObject cfg, File dir, String defaultFont, double defaultSizePct, int width) {
        this.cfg = cfg;
        paint.setTextSize((float) (width * cfg.optDouble("sizePct", defaultSizePct) / 100));
        paint.setLetterSpacing((float) cfg.optDouble("letterSpacing", 0));
        paint.setTypeface(typeface(dir, cfg, defaultFont));
    }

    /**
     * Schrift mit Stärke (cfg.weight 100..900, fehlt = wie gewählt): Familien aus mehreren Dateien wechseln zur
     * nächstliegenden Datei, einzelne Dateien bekommen die Achse 'wght' (wirkt bei variablen Schriften),
     * Systemschriften die Stärke direkt.
     */
    static Typeface typeface(File dir, JSONObject cfg, String defaultFont) {
        int weight = cfg.optInt("weight", 0);
        String font = cfg.optString("font", "");
        if (!font.isEmpty() && dir != null) {
            List<String> files = fontFiles(dir);
            boolean family = FontFamilies.weightCount(files, font) > 1;
            File f = new File(dir, weight > 0 && family ? FontFamilies.nearest(files, font, weight) : font);
            if (f.exists()) {
                Typeface t = null;
                if (weight > 0 && !family) t = new Typeface.Builder(f).setFontVariationSettings("'wght' " + weight).build();
                if (t == null) t = new Typeface.Builder(f).build();
                if (t != null) return t;
            }
        }
        Typeface base = Typeface.create(cfg.optString("systemFont", defaultFont), Typeface.NORMAL);
        return weight > 0 ? Typeface.create(base, weight, false) : base;
    }

    /** Schriftdateien im App-Ordner, sortiert. */
    static List<String> fontFiles(File dir) {
        String[] names = dir.list((d, n) -> n.toLowerCase(Locale.ROOT).matches(".*\\.(ttf|otf)"));
        List<String> out = new ArrayList<>(names != null ? Arrays.asList(names) : List.of());
        out.sort(null);
        return out;
    }

    /** Linke Kante für Ankerpunkt x und Breite w je nach Ausrichtung. */
    float left(float x, float w) {
        switch (cfg.optString("align", "center")) {
            case "left": return x;
            case "right": return x - w;
            default: return x - w / 2;
        }
    }

    String text(String s) {
        return cfg.optBoolean("uppercase", false) ? s.toUpperCase(Locale.GERMANY) : s;
    }

    float size() {
        return paint.getTextSize();
    }

    /** Gezeigte Farbe ohne Deckkraft (nach prepare()). */
    int shown() {
        return shown;
    }

    float opacity() {
        return WallpaperRenderer.clamp01((float) cfg.optDouble("opacity", 1));
    }

    /** Foto (Szenengröße), mittel und stark verkleinertes Foto und Bildfarben des Frames für die Füllungen Bild und Glas. */
    void setBackdrop(Bitmap photo, Bitmap photoMid, Bitmap photoSoft, Matrix fit, ColorMatrix grade) {
        this.photo = photo;
        this.photoMid = photoMid;
        this.photoSoft = photoSoft;
        this.grade = grade;
        photoMatrix.set(fit);
    }

    /**
     * Farbe für diesen Frame. behind = Helligkeit hinter dem Text mal Szenenlicht (für "auto"),
     * night = Nachtanteil der Tageszeit (für feste Tag-/Nachtfarbe).
     */
    void prepare(float behind, float night) {
        String color = cfg.optString("color", "auto");
        if ("auto".equals(color)) {
            // hell vor dunklem Hintergrund und umgekehrt
            int day = parseColor(cfg.optString("colorDark", "#26344A"), 0xFF26344A);
            int nightColor = parseColor(cfg.optString("colorLight", "#EEF2FA"), 0xFFEEF2FA);
            float t = 1 - WallpaperRenderer.smoothstep(0.3f, 0.5f, behind);
            shown = Mood.c(day, nightColor, t);
        } else {
            shown = parseColor(color, Color.WHITE);
        }
        paint.setColor(shown);
        paint.clearShadowLayer(); // Effekte zeichnet draw() selbst
    }

    /**
     * Zeichnet ein Element. box = seine Fläche (für Verlauf und Ausblenden), content zeichnet den Inhalt mit dem
     * übergebenen Paint (wird je Durchgang mehrfach aufgerufen).
     */
    void draw(Canvas c, RectF box, Consumer<Paint> content) {
        if (box.isEmpty()) return;
        float size = size();
        String style = shadowStyle();
        RectF bounds = new RectF(box);
        float reach = size * 1.5f + ("long".equals(style) ? size * 0.8f * pct("longLength") : 0);
        bounds.inset(-reach, -reach);
        int layer = c.saveLayerAlpha(bounds, Math.round(opacity() * 255));
        drawEffect(c, content, style, size);
        if (cfg.optBoolean("outline", false)) {
            int auto = Color.luminance(shown) > 0.4f ? 0xFF1C1B1F : Color.WHITE; // kontrastierend
            float width = size * 0.12f * pct("outlineWidth");
            String of = cfg.optString("outlineFill", "color");
            if ("color".equals(of)) strokePass(c, content, effectColor("outlineColor", auto), width);
            else drawBaseFill(c, content, of, style, box, size, "outline", width);
        }
        drawFill(c, content, cfg.optString("fill", "color"), style, box, size);
        if (cfg.optBoolean("fade", false)) fadeMask(c, box, bounds);
        c.restoreToCount(layer);
    }

    private String shadowStyle() {
        return cfg.optString("shadowStyle", cfg.optBoolean("shadow", true) ? "shadow" : "none");
    }

    private void drawEffect(Canvas c, Consumer<Paint> content, String style, float size) {
        switch (style) {
            case "shadow": {
                // automatisch kontrastiert der Schatten zur Schrift: helle Schrift → dunkler Schatten und umgekehrt
                boolean light = Color.luminance(shown) > 0.4f;
                boolean auto = "auto".equals(cfg.optString("shadowColor", "auto"));
                int color = effectColor("shadowColor", light ? Color.BLACK : Color.WHITE);
                float strength = pct("shadowStrength", shadowDefaults[2]) * (auto && !light ? 0.55f : 1f);
                float dist = size * 0.2f * pct("shadowDistance", shadowDefaults[1]);
                double a = Math.toRadians(cfg.optDouble("shadowAngle", 90)); // 90° = nach unten
                blurPass(c, content, withAlpha(color, strength), size * 0.3f * pct("shadowBlur", shadowDefaults[0]),
                        (float) Math.cos(a) * dist, (float) Math.sin(a) * dist);
                break;
            }
            case "glow": {
                int color = withAlpha(effectColor("glowColor", shown), pct("glowStrength"));
                float r = size * 0.5f * pct("glowSize");
                blurPass(c, content, color, r, 0, 0);
                blurPass(c, content, color, r / 2, 0, 0);
                break;
            }
            case "neon": {
                int color = effectColor("neonColor", 0);
                float a = pct("neonStrength");
                float r = size * 0.35f * pct("neonSize");
                blurPass(c, content, withAlpha(color, a * 0.8f), r * 2, 0, 0);
                blurPass(c, content, withAlpha(color, a), r, 0, 0);
                blurPass(c, content, withAlpha(color, a), r * 0.5f, 0, 0);
                break;
            }
            case "long": {
                // harte Kopien entlang einer Richtung, nach hinten heller
                int color = effectColor("longColor", Color.luminance(shown) > 0.4f ? 0xFF10141D : Color.WHITE);
                float step = Math.max(1f, size * 0.02f);
                int n = Math.round(size * 0.8f * pct("longLength") / step);
                double a = Math.toRadians(cfg.optDouble("longAngle", fx("longAngle")));
                float strength = pct("longStrength");
                for (int i = n; i >= 1; i--) {
                    blurPass(c, content, withAlpha(color, strength * (1 - i / (n + 1f) * 0.6f)), 0,
                            (float) Math.cos(a) * step * i, (float) Math.sin(a) * step * i);
                }
                break;
            }
            case "retro": {
                float d = size * 0.16f * pct("retroDistance");
                double a = Math.toRadians(cfg.optDouble("retroAngle", fx("retroAngle")));
                float dx = (float) Math.cos(a) * d;
                float dy = (float) Math.sin(a) * d;
                blurPass(c, content, effectColor("retroColor2", 0), 0, 2 * dx, 2 * dy);
                blurPass(c, content, effectColor("retroColor1", 0), 0, dx, dy);
                break;
            }
            default:
                break;
        }
    }

    private void drawFill(Canvas c, Consumer<Paint> content, String fill, String style, RectF box, float size) {
        drawBaseFill(c, content, fill, style, box, size, "", 0);
        // Verlauf zusätzlich über Glas, Bild oder Hohl (Füllungen stapeln)
        if (!"gradient".equals(fill) && !"color".equals(fill) && cfg.optBoolean("gradientOverlay", false)) {
            work.set(paint);
            work.setShader(gradientShader(box, ""));
            work.setAlpha(Math.round(255 * pct("gradientMix")));
            if ("hollow".equals(fill)) {
                work.setStyle(Paint.Style.STROKE);
                work.setStrokeJoin(Paint.Join.ROUND);
                work.setStrokeWidth(Math.max(1f, size * 0.08f * pct("hollowWidth")));
            }
            content.accept(work);
        }
    }

    /** Schlüssel einer Füllung: ohne Präfix die Schrift, mit "outline" die Kontur (outlineGradientAngle …). */
    static String key(String prefix, String key) {
        return prefix.isEmpty() ? key : prefix + Character.toUpperCase(key.charAt(0)) + key.substring(1);
    }

    private Shader gradientShader(RectF box, String p) {
        double a = Math.toRadians(cfg.optDouble(key(p, "gradientAngle"), fx("gradientAngle")));
        float dx = (float) Math.cos(a);
        float dy = (float) Math.sin(a);
        float half = Math.abs(dx) * box.width() / 2 + Math.abs(dy) * box.height() / 2;
        int[] colors = gradientColors(p);
        float[] pos = GradientStops.positions(fractions(cfg.optJSONArray(key(p, "gradientStops"))), colors.length);
        float[] soft = GradientStops.softness(fractions(cfg.optJSONArray(key(p, "gradientSoft"))), colors.length);
        return new LinearGradient(box.centerX() - dx * half, box.centerY() - dy * half,
                box.centerX() + dx * half, box.centerY() + dy * half, GradientStops.colors(colors),
                GradientStops.stops(pos, soft), Shader.TileMode.CLAMP);
    }

    /** Liste von Prozentwerten (0..100) als Anteile 0..1, null ohne Liste. */
    static float[] fractions(org.json.JSONArray a) {
        if (a == null) return null;
        float[] f = new float[a.length()];
        for (int i = 0; i < f.length; i++) f[i] = (float) a.optDouble(i, 0) / 100f;
        return f;
    }

    /** Füllung der Schrift (prefix "", stroke 0) oder der Kontur (prefix "outline", stroke = Konturbreite). */
    private void drawBaseFill(Canvas c, Consumer<Paint> content, String fill, String style, RectF box, float size,
                              String p, float stroke) {
        work.set(paint);
        if (stroke > 0) {
            work.setStyle(Paint.Style.STROKE);
            work.setStrokeJoin(Paint.Join.ROUND);
            work.setStrokeWidth(Math.max(1f, stroke));
        }
        switch (fill) {
            case "hollow":
                strokePass(c, content, shown, size * 0.08f * pct("hollowWidth"));
                return;
            case "gradient":
                work.setShader(gradientShader(box, p));
                break;
            case "photo":
                if (photo == null) break;
                work.setShader(shader(photo, photoMatrix));
                work.setColorFilter(new ColorMatrixColorFilter(brighten(1 + pct(key(p, "photoBright"), fx("photoBright")) * 0.8f, 1.3f, 0)));
                break;
            case "glass": {
                if (photo == null || photoMid == null || photoSoft == null) break;
                // klares Glas: Foto leicht versetzt und aufgehellt, "Unschärfe" blendet scharf → mittel → weich über;
                // "Milchig" legt das weiche, weißliche Foto darüber
                float frost = pct(key(p, "glassFrost"), fx("glassFrost"));
                float blur = 2 * pct(key(p, "glassBlur"), fx("glassBlur"));
                Bitmap[] steps = {photo, photoMid, photoSoft};
                int base = Math.min(1, (int) blur);
                float shift = size * 0.03f;
                work.setShader(glassShader(steps[base], shift));
                work.setColorFilter(new ColorMatrixColorFilter(brighten(1.08f, 1.05f, 0.06f)));
                content.accept(work);
                if (blur - base > 0.01f) {
                    work.setShader(glassShader(steps[base + 1], shift));
                    work.setAlpha(Math.round(255 * (blur - base)));
                    content.accept(work);
                }
                work.setShader(glassShader(photoSoft, 0));
                work.setColorFilter(new ColorMatrixColorFilter(brighten(1.05f, 1f, 0.15f + 0.5f * frost)));
                work.setAlpha(Math.round(255 * frost));
                content.accept(work);
                if (stroke == 0) strokePass(c, content, withAlpha(Color.WHITE, pct("glassEdge")), Math.max(1f, size * 0.012f));
                return;
            }
            default:
                if (stroke == 0 && "neon".equals(style)) work.setColor(Mood.c(effectColor("neonColor", 0), Color.WHITE, 0.8f)); // heller Kern
                break;
        }
        content.accept(work);
    }

    /** Farbpunkte des Verlaufs (2–8, "text" = Schriftfarbe); ohne Liste Schriftfarbe → gradientColor. */
    private int[] gradientColors(String p) {
        org.json.JSONArray a = cfg.optJSONArray(key(p, "gradientColors"));
        if (a == null || a.length() < 2) return new int[]{shown, effectColor("gradientColor", 0)};
        int[] c = new int[Math.min(8, a.length())];
        for (int i = 0; i < c.length; i++) {
            String v = a.optString(i, "text");
            c[i] = "text".equals(v) ? shown : parseColor(v, shown);
        }
        return c;
    }

    /** Bildfarben des Frames, dann aufhellen (scale), sättigen und Richtung Weiß ziehen (white 0..1). */
    private ColorMatrix brighten(float scale, float saturation, float white) {
        ColorMatrix m = new ColorMatrix(grade);
        ColorMatrix sat = new ColorMatrix();
        sat.setSaturation(saturation);
        m.postConcat(sat);
        float k = 1 - white;
        float add = 255 * white;
        m.postConcat(new ColorMatrix(new float[]{
                scale * k, 0, 0, 0, add,
                0, scale * k, 0, 0, add,
                0, 0, scale * k, 0, add,
                0, 0, 0, 1, 0}));
        return m;
    }

    /** Foto b (Szenenfoto oder verkleinert) deckungsgleich über dem Bild, um shift versetzt (Lichtbrechung). */
    private Shader glassShader(Bitmap b, float shift) {
        refract.set(photoMatrix);
        refract.preScale(photo.getWidth() / (float) b.getWidth(), photo.getHeight() / (float) b.getHeight());
        refract.postTranslate(shift, shift);
        return shader(b, refract);
    }

    private static Shader shader(Bitmap b, Matrix m) {
        BitmapShader s = new BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        s.setLocalMatrix(m);
        return s;
    }

    /** Inhalt einfarbig, optional weich (radius > 0) und versetzt zeichnen. */
    private void blurPass(Canvas c, Consumer<Paint> content, int color, float radius, float dx, float dy) {
        work.set(paint);
        work.setColor(color);
        work.setMaskFilter(radius > 0.5f ? new BlurMaskFilter(radius, BlurMaskFilter.Blur.NORMAL) : null);
        c.save();
        c.translate(dx, dy);
        content.accept(work);
        c.restore();
    }

    private void strokePass(Canvas c, Consumer<Paint> content, int color, float width) {
        work.set(paint);
        work.setStyle(Paint.Style.STROKE);
        work.setStrokeJoin(Paint.Join.ROUND);
        work.setStrokeWidth(Math.max(1f, width));
        work.setColor(color);
        content.accept(work);
    }

    /** Ausblenden: ab Position (fadeStart) über die Länge (fadeLength) in Richtung fadeAngle durchsichtig werden. */
    private void fadeMask(Canvas c, RectF box, RectF bounds) {
        double a = Math.toRadians(cfg.optDouble("fadeAngle", fx("fadeAngle")));
        float dx = (float) Math.cos(a);
        float dy = (float) Math.sin(a);
        float half = Math.abs(dx) * box.width() / 2 + Math.abs(dy) * box.height() / 2;
        float from = -half + 2 * half * pct("fadeStart");
        float to = from + Math.max(1f, 2 * half * pct("fadeLength"));
        work.reset();
        work.setShader(new LinearGradient(box.centerX() + dx * from, box.centerY() + dy * from,
                box.centerX() + dx * to, box.centerY() + dy * to, 0xFF000000, 0x00000000, Shader.TileMode.CLAMP));
        work.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        c.drawRect(bounds, work);
        work.setXfermode(null);
    }

    /** Wert 0..100 aus der Config als 0..1; ohne Wert der Standard des Effekts. */
    private float pct(String key) {
        return pct(key, fx(key));
    }

    private float pct(String key, int def) {
        return (float) cfg.optDouble(key, def) / 100f;
    }

    /** Effektfarbe: "auto" (oder fehlt) = Standardfarbe des Effekts bzw. fallback, sonst Hex. */
    private int effectColor(String key, int fallback) {
        String c = cfg.optString(key, "auto");
        if ("auto".equals(c)) c = FX_COLOR.getOrDefault(key, "");
        return parseColor(c, fallback);
    }

    static int parseColor(String s, int fallback) {
        try {
            return s.isEmpty() ? fallback : Color.parseColor(s);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    /** Farbe mit zusätzlich verringerter Deckkraft (0..1). */
    static int withAlpha(int color, float opacity) {
        return (Math.round(Color.alpha(color) * WallpaperRenderer.clamp01(opacity)) << 24) | (color & 0xFFFFFF);
    }
}
