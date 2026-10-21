package de.seelig.wetterwallpaper;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.RecordingCanvas;
import android.graphics.RectF;
import android.graphics.RenderNode;
import android.graphics.Shader;
import android.os.Build;

import org.json.JSONObject;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * Zeichnet das komplette Wallpaper in einer festen Größe. Wird vom Live-Wallpaper und von der
 * Vorschau in der App gleichermaßen benutzt, damit beide exakt gleich aussehen.
 * Ebenen von hinten nach vorn: gerenderter Himmel (Sterne, Mond, Sonne), Foto-Himmel, Wolken, Land,
 * Dunst, ferne Partikel, Uhr, Motiv, nahe Partikel.
 */
final class WallpaperRenderer {
    private static final PorterDuffXfermode SRC_IN = new PorterDuffXfermode(PorterDuff.Mode.SRC_IN);
    private static final PorterDuffXfermode DST_IN = new PorterDuffXfermode(PorterDuff.Mode.DST_IN);

    /** Alles, was Scene.load braucht; key() ändert sich nur, wenn die Ebenen neu berechnet werden müssen. */
    static final class SceneParams {
        final int targetWidth;
        final float skyThreshold;
        final float hazeRange;
        final float subjectThreshold;
        final RectF clockBoxRel;
        /** Himmelsübergang 0 = scharf, 1 = extrem weich; nur der Startwert, Änderungen rechnet Scene.skyBlend nach. */
        final float skySoftness;
        /** Ordner mit dem Foto: der App-Ordner oder modes/<Modus> bei eigenem Foto des Modus. */
        final File photoDir;

        SceneParams(int targetWidth, float skyThreshold, float hazeRange, float subjectThreshold, RectF clockBoxRel,
                    float skySoftness, File photoDir) {
            this.targetWidth = targetWidth;
            this.skyThreshold = skyThreshold;
            this.hazeRange = hazeRange;
            this.subjectThreshold = subjectThreshold;
            this.clockBoxRel = clockBoxRel;
            this.skySoftness = skySoftness;
            this.photoDir = photoDir;
        }

        Scene load(File dir) {
            return Scene.load(photoDir != null ? photoDir : dir, targetWidth, skyThreshold, hazeRange, subjectThreshold, clockBoxRel,
                    skySoftness);
        }

        String key() {
            // Flächen nur grob, damit Mini-Verschiebungen nicht neu rechnen
            return targetWidth + "|" + skyThreshold + "|" + hazeRange + "|" + subjectThreshold + "|"
                    + coarse(clockBoxRel) + "|" + (photoDir != null ? photoDir.getName() : "");
        }

        private static String coarse(RectF r) {
            return Math.round(r.left * 50) + "," + Math.round(r.top * 50) + ","
                    + Math.round(r.right * 50) + "," + Math.round(r.bottom * 50);
        }
    }

    private final int width;
    private final int height;
    private final TextLayer clockText = new TextLayer(TextLayer.CLOCK_SHADOW);
    private final TextLayer widgetText = new TextLayer(TextLayer.WIDGET_SHADOW);
    /** Widget-Abschnitt mit geerbten Werten der Uhr (siehe widgetStyle). */
    private JSONObject widgetCfg = new JSONObject();
    /** Info-Widgets (Abschnittsname aus widgetList) → Stil mit geerbten Werten bzw. Textebene. */
    private final java.util.LinkedHashMap<String, JSONObject> infoCfg = new java.util.LinkedHashMap<>();
    private final java.util.HashMap<String, TextLayer> infoText = new java.util.HashMap<>();
    /** Masken pro Element ("widget" oder Abschnittsname) → {Schlüssel, Scene.Cut}; neu nur, wenn Fläche/Ebene sich ändert. */
    private final java.util.HashMap<String, Object[]> cuts = new java.util.HashMap<>();
    private InfoWidgets.Data widgetData = new InfoWidgets.Data();
    private final Paint layerPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix fit = new Matrix();
    private final NightSky nightSky;
    private final WeatherFx fx;
    private final WindowGlass glass;
    /** Szene ohne Scheibe, aufgezeichnet, damit die Scheibe sie hinter dem Frost weichzeichnen kann. */
    private final RenderNode sceneNode = new RenderNode("scene");
    /** Licht und Farbfilter des zuletzt gezeichneten Modus, für die Scheibe. */
    private float frameLight = 1f;
    private ColorMatrixColorFilter frameGrade;
    /** Dasselbe für den aktuellen Modus (nicht den des Übergangsbilds). */
    private float glassLight = 1f;
    private ColorMatrixColorFilter glassGrade;
    private JSONObject rawConfig = new JSONObject();
    private JSONObject config = new JSONObject();
    private File configDir;
    private String previewMode; // null im Live-Wallpaper, in der App-Vorschau der gerade bearbeitete Modus
    private String activeModeId = "";
    /** "lock" oder "home": welcher Bildschirm (Startbildschirm kann abweichen). */
    private String screen = "lock";
    private String holdMode;
    /** Wetter des letzten Frames, für den Regen-Look beim Auflösen des Modus. */
    private Weather lastWx;
    /** Übergang: zuletzt gezeichneter Frame des vorigen Modus (halbe Auflösung). */
    private Bitmap transitionFrame;
    private String transitionMode;
    private long transitionAt;
    private final Paint transitionPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.GERMANY);
    private SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, d. MMM", Locale.GERMANY);
    private volatile Scene scene;
    private Bitmap highlight;
    private int highlightColor;

    WallpaperRenderer(int width, int height) {
        this.width = width;
        this.height = height;
        nightSky = new NightSky(width, height);
        fx = new WeatherFx(width, height);
        glass = new WindowGlass(width, height);
    }

    int width() {
        return width;
    }

    int height() {
        return height;
    }

    Scene scene() {
        return scene;
    }

    JSONObject section(String name) {
        JSONObject s = config.optJSONObject(name);
        return s != null ? s : new JSONObject();
    }

    void setPreviewMode(String mode) {
        previewMode = mode;
        if (configDir != null) activateMode(resolveMode(System.currentTimeMillis(), widgetData.lat, widgetData.lon));
    }

    String resolveMode(long time, double lat, double lon) {
        if (previewMode != null && !previewMode.isEmpty()) return previewMode;
        if (holdMode != null) return holdMode;
        return liveMode(time, lat, lon);
    }

    /** Modus nach Uhrzeit und Wetter, ohne Vorschau und ohne Halten. */
    String liveMode(long time, double lat, double lon) {
        return DayNight.resolveMode(rawConfig.optJSONObject("dayNight"), time, lat, lon, lastWx);
    }

    /**
     * Bis die Szene des neuen Modus berechnet ist, weiter den alten Modus zeichnen: sonst passt die Uhr
     * (neue Position, neue Ebene) nicht zum noch alten Bild. null = nicht halten.
     */
    void hold(String mode) {
        holdMode = mode;
    }

    String activeMode() {
        return activeModeId;
    }

    /** Szenen-Parameter für einen anderen Modus (zum Vorausberechnen), ohne den aktiven Modus zu verändern. */
    SceneParams sceneParamsFor(String mode, int targetWidth) {
        if (mode == null || mode.equals(activeModeId)) return sceneParams(targetWidth);
        String keep = activeModeId;
        activateMode(mode);
        SceneParams p = sceneParams(targetWidth);
        activateMode(keep);
        return p;
    }

    /** Start- oder Sperrbildschirm (Engine-Flags bzw. Auswahl im Editor). */
    void setScreen(String s) {
        screen = "home".equals(s) ? "home" : "lock";
        if (configDir != null) activateMode(resolveMode(System.currentTimeMillis(), widgetData.lat, widgetData.lon));
    }

    /** Ordner mit dem Foto des aktiven Modus (eigenes Foto) oder der App-Ordner. */
    File photoDir() {
        if (configDir == null) return new File("");
        File own = new File(new File(configDir, Presets.MODES), activeModeId);
        return new File(own, "background.png").isFile() ? own : configDir;
    }

    /** Übernimmt Einstellungen; Uhr- und Widget-Stil wirken sofort, Ebenen erst über sceneParams() + setScene(). */
    void applyConfig(JSONObject newConfig, File dir) {
        rawConfig = newConfig != null ? newConfig : new JSONObject();
        configDir = dir;
        activateMode(resolveMode(System.currentTimeMillis(), widgetData.lat, widgetData.lon));
    }

    private void activateMode(String mode) {
        activeModeId = mode != null ? mode : "day";
        config = Settings.resolve(rawConfig, activeModeId, screen);
        applyStyles();
    }

    /** Schriften und Stile aus config übernehmen (auch nach Live-Änderungen wie der Zwei-Finger-Geste). */
    private void applyStyles() {
        JSONObject clock = section("clock");
        timeFormat = new SimpleDateFormat(clock.optString("format", "HH:mm"), Locale.GERMANY);
        clockText.apply(clock, configDir, "sans-serif-black", 32, width);
        widgetCfg = widgetStyle(section("widget"), clock);
        dateFormat = new SimpleDateFormat(widgetCfg.optString("dateFormat", "EEE, d. MMM"), Locale.GERMANY);
        widgetText.apply(widgetCfg, configDir, "sans-serif-medium", 5.4, width);

        infoCfg.clear();
        org.json.JSONArray list = config.optJSONArray("widgetList");
        for (int i = 0; list != null && i < list.length(); i++) {
            String id = list.optString(i);
            JSONObject own = section(id);
            if (!own.has("type")) continue;
            JSONObject eff = infoStyle(own, clock);
            infoCfg.put(id, eff);
            infoText.computeIfAbsent(id, k -> new TextLayer(TextLayer.WIDGET_SHADOW))
                    .apply(eff, configDir, "sans-serif-medium", InfoWidgets.defaultSize(own.optString("type")), width);
        }
        infoText.keySet().retainAll(infoCfg.keySet());
        cuts.clear();
    }

    /** Info-Widget: wie widgetStyle, nur standardmäßig an die Uhr gekoppelt („Stil der Uhr übernehmen“). */
    private static JSONObject infoStyle(JSONObject own, JSONObject clock) {
        try {
            JSONObject w = new JSONObject(own.toString());
            if (!w.has("linkClock")) w.put("linkClock", true);
            return widgetStyle(w, clock);
        } catch (org.json.JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Für die Daten-Abrufe: ob ein sichtbares Info-Widget dieses Typs existiert. */
    boolean wants(String type) {
        for (JSONObject c : infoCfg.values()) if (type.equals(c.optString("type")) && c.optBoolean("enabled", true)) return true;
        return false;
    }

    /** Daten für die Info-Widgets (Vorhersage, Akku, Termin); Zeit und Wetter setzt render() selbst. */
    void setWidgetData(InfoWidgets.Data d) {
        widgetData = d;
    }

    // ---------- Elemente (Uhr, Datum & Wetter, Info-Widgets) für die Vorschau ----------

    /** Sichtbare Elemente in Zeichenreihenfolge (die Uhr liegt immer hinter dem Motiv, also zuerst). */
    java.util.List<String> elementIds() {
        java.util.List<String> ids = new java.util.ArrayList<>();
        if (shown(section("clock"))) ids.add("clock");
        ids.addAll(drawOrder());
        return ids;
    }

    /** Eingeblendet und nicht entfernt (Uhr und Datum & Wetter werden beim Entfernen nur als "present": false markiert). */
    private static boolean shown(JSONObject s) {
        return s.optBoolean("enabled", true) && s.optBoolean("present", true);
    }

    /** Elemente über dem Motiv von hinten nach vorn: gespeicherte Reihenfolge, neue Elemente vorn. */
    java.util.List<String> drawOrder() {
        java.util.List<String> base = new java.util.ArrayList<>();
        if (shown(section("widget"))) base.add("widget");
        for (java.util.Map.Entry<String, JSONObject> e : infoCfg.entrySet()) {
            if (e.getValue().optBoolean("enabled", true)) base.add(e.getKey());
        }
        java.util.List<String> out = new java.util.ArrayList<>();
        org.json.JSONArray order = config.optJSONArray("order");
        for (int i = 0; order != null && i < order.length(); i++) {
            String id = order.optString(i);
            if (base.remove(id)) out.add(id);
        }
        out.addAll(base);
        return out;
    }

    /** Größe in % der Bildbreite (wie der Regler "Größe"). */
    float sizePct(String id) {
        JSONObject s = section(id);
        double def = "clock".equals(id) ? 32 : "widget".equals(id) ? 5.4 : InfoWidgets.defaultSize(s.optString("type", "text"));
        return (float) s.optDouble("sizePct", def);
    }

    /** Größe live ändern (Vorschau, Zwei-Finger-Geste), ohne Datei zu schreiben. */
    void setSize(String id, float pct) {
        try {
            JSONObject s = config.optJSONObject(id);
            if (s == null) {
                s = new JSONObject();
                config.put(id, s);
            }
            s.put("sizePct", Math.round(pct * 10) / 10.0);
        } catch (org.json.JSONException e) {
            throw new IllegalStateException(e);
        }
        applyStyles();
    }

    RectF elementBox(String id, long time, Weather wx) {
        if ("clock".equals(id)) return rotatedBox(id, clockBox(time));
        if ("widget".equals(id)) return rotatedBox(id, widgetBox(time, wx));
        return rotatedBox(id, infoBox(id));
    }

    /** Drehung in Grad (positiv = im Uhrzeigersinn), um die Mitte des Elements. */
    float rotation(String id) {
        return (float) section(id).optDouble("rotation", 0);
    }

    /** Drehung live ändern (Vorschau, Zwei-Finger-Geste), ohne Datei zu schreiben. */
    void setRotation(String id, float deg) {
        try {
            JSONObject s = config.optJSONObject(id);
            if (s == null) {
                s = new JSONObject();
                config.put(id, s);
            }
            s.put("rotation", Math.round(deg));
        } catch (org.json.JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Umschließendes Rechteck der gedrehten Fläche (Trefferfläche, Rahmen, Verdeckungsmaske). */
    private RectF rotatedBox(String id, RectF box) {
        float deg = rotation(id);
        if (deg == 0 || box.isEmpty()) return box;
        RectF r = new RectF(box);
        Matrix m = new Matrix();
        m.setRotate(deg, box.centerX(), box.centerY());
        m.mapRect(r);
        return r;
    }

    /** Element gedreht um die Mitte seiner (ungedrehten) Fläche zeichnen. */
    private void rotated(Canvas c, String id, RectF box, Runnable draw) {
        float deg = rotation(id);
        if (deg == 0 || box.isEmpty()) {
            draw.run();
            return;
        }
        c.save();
        c.rotate(deg, box.centerX(), box.centerY());
        draw.run();
        c.restore();
    }

    /** Position in % (x Ankerpunkt, y Grundlinie bzw. bei Info-Widgets Oberkante). */
    float[] position(String id) {
        JSONObject s = section(id);
        double y = "clock".equals(id) ? 33 : "widget".equals(id) ? 36 : 55;
        return new float[]{(float) s.optDouble("xPct", 50), (float) s.optDouble("yPct", y)};
    }

    /** Element live verschieben (Vorschau), ohne Datei zu schreiben. */
    void setPosition(String id, float xPct, float yPct) {
        try {
            JSONObject s = config.optJSONObject(id);
            if (s == null) {
                s = new JSONObject();
                config.put(id, s);
            }
            s.put("xPct", xPct).put("yPct", yPct);
        } catch (org.json.JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    private RectF infoBox(String id) {
        JSONObject eff = infoCfg.get(id);
        TextLayer l = infoText.get(id);
        if (eff == null || l == null) return new RectF();
        WidgetParts.Content content = InfoWidgets.build(eff.optString("type"), eff, widgetData);
        if (content.isEmpty()) return new RectF();
        float[] m = WidgetParts.measure(content, l.paint, l.size());
        float[] pos = position(id);
        float x = width * pos[0] / 100;
        float y = height * pos[1] / 100;
        float left = l.left(x, m[0]);
        return new RectF(left, y, left + m[0], y + m[1]);
    }

    /** Automatisch gewählte Ebene eines Elements (für den Regler), NaN solange keine Maske berechnet ist. */
    float autoThreshold(String id) {
        Object[] e = cuts.get(id);
        return e == null || e[1] == null ? Float.NaN : ((Scene.Cut) e[1]).threshold;
    }

    /**
     * Widget-Einstellungen: ohne eigene Farbe gilt die der Uhr (wie bisher), ohne eigenen Schatten deren Schatten.
     * Gekoppelt (linkClock) übernimmt das Widget Schrift, Stärke, Farbe und Schatten ganz von der Uhr.
     */
    /** Was „Stil der Uhr übernehmen“ von der Uhr übernimmt (Größe, Position und Inhalt bleiben eigen). */
    static final String[] LINK_KEYS = {"font", "systemFont", "weight", "letterSpacing", "color", "colorNight", "opacity",
            "shadow", "shadowStyle", "shadowColor", "shadowBlur", "shadowDistance", "shadowAngle", "shadowStrength",
            "glowColor", "glowSize", "glowStrength", "fill", "gradientColor", "gradientColors", "gradientAngle", "glassFrost",
            "glassEdge", "photoBright", "hollowWidth", "gradientOverlay", "gradientMix", "outline", "outlineColor", "outlineWidth",
            "outlineFill", "outlineGradientColors", "outlineGradientAngle", "outlineGlassFrost", "outlinePhotoBright", "neonColor", "neonSize",
            "neonStrength", "longColor", "longLength", "longAngle", "longStrength", "retroColor1", "retroColor2",
            "retroDistance", "retroAngle", "fade", "fadeStart", "fadeLength", "fadeAngle"};

    static JSONObject widgetStyle(JSONObject widget, JSONObject clock) {
        try {
            JSONObject w = new JSONObject(widget.toString());
            boolean link = widget.optBoolean("linkClock", false);
            if (link) {
                for (String k : LINK_KEYS) {
                    w.remove(k);
                    if (clock.has(k)) w.put(k, clock.get(k));
                }
                if (!w.has("font") && !w.has("systemFont")) w.put("systemFont", "sans-serif-black"); // Standard der Uhr
            }
            if (!w.has("color")) {
                for (String k : new String[]{"color", "colorNight"}) if (clock.has(k)) w.put(k, clock.get(k));
            }
            for (String k : new String[]{"colorDark", "colorLight", "shadow"}) {
                if (!w.has(k) && clock.has(k)) w.put(k, clock.get(k));
            }
            if (!w.has("shadowStyle") && !widget.has("shadow") && clock.has("shadowStyle")) w.put("shadowStyle", clock.get("shadowStyle"));
            return w;
        } catch (org.json.JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Vorschau: Maske (ALPHA_8 in Szenengröße) einfärben, null = aus. Farbe inkl. Deckkraft. */
    void setHighlight(Bitmap mask, int color) {
        highlight = mask;
        highlightColor = color;
    }

    /** Uhr live verschieben (Vorschau), ohne Datei zu schreiben. */
    void setClockPosition(float xPct, float yPct) {
        setPosition("clock", xPct, yPct);
    }

    private float skySoftness() {
        return (float) section("scene").optDouble("skySoftness", 1);
    }

    SceneParams sceneParams(int targetWidth) {
        JSONObject sc = section("scene");
        return new SceneParams(targetWidth, (float) sc.optDouble("skyThreshold", 0.006),
                (float) sc.optDouble("hazeRange", 0.35), (float) sc.optDouble("subjectThreshold", Double.NaN),
                relative(clockBoxNow()), skySoftness(), photoDir());
    }

    private RectF relative(RectF box) {
        return new RectF(box.left / width, box.top / height, box.right / width, box.bottom / height);
    }

    void setScene(Scene s) {
        if (s != null) {
            float scale = Math.max(width / (float) s.width(), height / (float) s.height());
            Matrix m = new Matrix();
            m.setScale(scale, scale);
            m.postTranslate((width - s.width() * scale) / 2f, (height - s.height() * scale) / 2f);
            fit.set(m);
        }
        scene = s;
    }

    /** Fläche der Uhr im aktiven Modus, ohne den Modus neu aufzulösen. */
    private RectF clockBoxNow() {
        return ClockRenderer.clockBox(clockText, section("clock"), timeFormat, width, height);
    }

    /** Fläche der Uhr in Pixeln dieses Renderers (aktuelle Zeit). */
    RectF clockBox() {
        return clockBox(System.currentTimeMillis());
    }

    /** Fläche der Uhr in Pixeln dieses Renderers für eine bestimmte Szenenzeit. */
    RectF clockBox(long time) {
        String mode = resolveMode(time, widgetData.lat, widgetData.lon);
        if (!mode.equals(activeModeId)) activateMode(mode);
        return ClockRenderer.clockBox(clockText, section("clock"), timeFormat, width, height);
    }

    /** Wetter-/Info-Widget live verschieben (Vorschau). */
    void setWidgetPosition(float xPct, float yPct) {
        setPosition("widget", xPct, yPct);
    }

    /** Fläche des Wetter-/Info-Widgets in Pixeln dieses Renderers. */
    RectF widgetBox(long time, Weather wx) {
        JSONObject pos = section("widget");
        if (!shown(pos)) return new RectF();
        float x = (float) (width * pos.optDouble("xPct", 50) / 100);
        float y = (float) (height * pos.optDouble("yPct", 36) / 100);
        float size = widgetText.size();
        Line[] lines = widgetLines(time, wx, false);
        RectF box = new RectF();
        for (int i = 0; i < lines.length; i++) {
            float w = lineWidth(lines[i]);
            float left = widgetText.left(x, w);
            float base = y + i * size * LINE_HEIGHT;
            RectF r = new RectF(left, base - size * 0.85f, left + w, base + size * 0.25f);
            if (i == 0) box.set(r);
            else box.union(r);
        }
        return box;
    }

    private static final float LINE_HEIGHT = 1.3f;

    /** Eine Zeile des Widgets: Text, optional ein Linien-Symbol, Text. */
    private static final class Line {
        final String before;
        final String icon;
        final String after;

        Line(String before, String icon, String after) {
            this.before = before;
            this.icon = icon;
            this.after = after;
        }
    }

    /** Zeilen nach Format (alles / nur Datum / nur Wetter), ein- oder zweizeilig, mit Emoji oder Linien-Symbol. */
    private Line[] widgetLines(long time, Weather wx, boolean night) {
        Weather w = wx != null ? wx : Weather.CLEAR;
        String format = widgetCfg.optString("format", "all");
        String date = widgetText.text(dateFormat.format(new Date(time)));
        boolean icons = widgetCfg.optBoolean("lineIcons", false);
        String icon = icons ? WeatherIcon.kind(w, night) : null;
        String weather = icons ? " " + w.temperatureText() : w.weatherSymbol(night) + " " + w.temperatureText();
        if ("date_only".equals(format)) return new Line[]{new Line(date, null, "")};
        Line weatherLine = new Line("", icon, weather);
        if ("weather_only".equals(format)) return new Line[]{weatherLine};
        if (widgetCfg.optBoolean("twoLines", false)) return new Line[]{new Line(date, null, ""), weatherLine};
        return new Line[]{new Line(date + "  ", icon, weather)};
    }

    private float lineWidth(Line l) {
        Paint p = widgetText.paint;
        return p.measureText(l.before) + (l.icon != null ? WeatherIcon.advance(widgetText.size()) : 0) + p.measureText(l.after);
    }

    /** Wetter nach Priorität: Test-Vorgabe (debug), feste Wahl in den Einstellungen, sonst live. */
    Weather chooseWeather(WeatherSource live, double lat, double lon) {
        Weather forced = Weather.preset(section("debug").optString("weather", ""));
        if (forced != null) return forced;
        JSONObject w = section("weather");
        if (!w.optBoolean("live", true)) {
            Weather fixed = Weather.preset(w.optString("fixed", "rain"));
            if (fixed != null) return fixed;
        }
        return live != null ? live.get(lat, lon) : Weather.CLEAR;
    }

    /**
     * Zeichnet einen Frame. time = Szenenzeit (Uhr, Sonnenstand), now = echte Zeit (Animation).
     * Gibt die gewünschte Pause bis zum nächsten Frame in ms zurück, 0 = statisch.
     */
    long render(Canvas c, long time, long now, Weather wx, double lat, double lon, boolean motion) {
        boolean showGlass = section("layers").optBoolean("glass", true);
        glass.update(wx, now, motion && showGlass);
        if (!showGlass) return renderScene(c, time, now, wx, lat, lon, motion);
        // Frost braucht die Szene noch einmal, weichgezeichnet; das geht nur auf der Grafikkarte
        boolean blur = glass.frosted(wx) && c.isHardwareAccelerated() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S;
        long delay;
        if (blur) {
            sceneNode.setPosition(0, 0, width, height);
            RecordingCanvas rc = sceneNode.beginRecording(width, height);
            try {
                delay = renderScene(rc, time, now, wx, lat, lon, motion);
            } finally {
                sceneNode.endRecording();
            }
            c.drawRenderNode(sceneNode);
        } else {
            delay = renderScene(c, time, now, wx, lat, lon, motion);
        }
        Scene s = scene;
        glass.draw(c, wx, glassLight, blur ? sceneNode : null, s != null ? s.photo : null, fit, glassGrade);
        if (motion && glass.animating(wx) && (delay == 0 || delay > 33)) delay = 33;
        return delay;
    }

    /** Alles außer der Scheibe, samt weichem Übergang beim Moduswechsel. */
    private long renderScene(Canvas c, long time, long now, Weather wx, double lat, double lon, boolean motion) {
        lastWx = wx;
        String mode = resolveMode(time, lat, lon);
        String[] prev = new String[1];
        float p = (previewMode == null || previewMode.isEmpty()) && holdMode == null
                ? DayNight.transition(rawConfig.optJSONObject("dayNight"), time, lat, lon, wx, prev) : 1f;
        long delay = renderMode(c, time, now, wx, lat, lon, motion, mode);
        glassLight = frameLight; // vor dem Übergangsbild merken, das rechnet den vorigen Modus
        glassGrade = frameGrade;
        if (p >= 1f || prev[0] == null || prev[0].equals(mode)) {
            transitionMode = null;
            return delay;
        }
        // Weicher Übergang: vorigen Modus darüber ausblenden. Sein Bild wird nur alle 15 s neu gezeichnet.
        if (transitionFrame == null) {
            transitionFrame = Bitmap.createBitmap(Math.max(1, width / 2), Math.max(1, height / 2), Bitmap.Config.ARGB_8888);
        }
        if (!prev[0].equals(transitionMode) || Math.abs(now - transitionAt) > 15_000) {
            transitionFrame.eraseColor(0);
            Canvas tc = new Canvas(transitionFrame);
            tc.scale(transitionFrame.getWidth() / (float) width, transitionFrame.getHeight() / (float) height);
            renderMode(tc, time, now, wx, lat, lon, false, prev[0]);
            activateMode(mode);
            transitionMode = prev[0];
            transitionAt = now;
        }
        transitionPaint.setAlpha(Math.round((1 - p) * 255));
        c.drawBitmap(transitionFrame, null, new RectF(0, 0, width, height), transitionPaint);
        return delay;
    }

    /** Ein Frame für einen bestimmten Modus. */
    private long renderMode(Canvas c, long time, long now, Weather wx, double lat, double lon, boolean motion, String mode) {
        widgetData.now = time;
        widgetData.wx = wx;
        widgetData.lat = lat;
        widgetData.lon = lon;
        if (!mode.equals(activeModeId)) activateMode(mode);

        Scene scene = this.scene;
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(time);
        JSONObject seasonCfg = section("season");
        Season season = Season.resolve(seasonCfg, cal);
        Season seasonTint = seasonCfg.optBoolean("tint", true) ? season : null;
        float seasonStrength = (float) seasonCfg.optDouble("strength", Season.DEFAULT_STRENGTH);
        if (!seasonCfg.optBoolean("particles", true)) season = null; // ab hier nur noch Blätter/Blüten

        JSONObject layers = section("layers");
        boolean showBackgroundFilter = layers.optBoolean("backgroundFilter", true);
        boolean showClock = shown(section("clock"));
        boolean showParticles = layers.optBoolean("particles", true);
        boolean showSkyObjects = layers.optBoolean("skyObjects", true);
        boolean showClouds = layers.optBoolean("clouds", true);

        SkyPosition sun = SkyPosition.sun(time, lat, lon);
        SkyPosition moon = SkyPosition.moon(time, lat, lon);
        double phase = SkyPosition.moonPhase(time);
        Mood mood = Mood.forElevation(sun.elevation);
        float horizonY = (float) (height * section("scene").optDouble("horizonPct", 30) / 100);
        float moonUp = clamp01((float) (moon.elevation + 2) / 6f);
        float moonlight = 0.5f * (float) SkyPosition.moonIllumination(phase) * moonUp * mood.night * (1 - wx.cloud);

        float effectiveNight;
        if ("night".equals(mode)) effectiveNight = 1f;
        else if ("twilight".equals(mode)) effectiveNight = 0.5f;
        else if ("day".equals(mode)) effectiveNight = 0f;
        else effectiveNight = mood.night;

        if (motion && showParticles) fx.update(wx, season, now);
        float flash = motion ? fx.flash(wx, now) : 0;

        boolean replaceSky = layers.optBoolean("skyReplace", true);
        Bitmap skyBlend = scene != null ? scene.skyBlend(skySoftness()) : null;
        boolean blendSky = skyBlend != null;
        float starVis = 0;
        if (!blendSky) starVis = drawSky(c, mood, wx, sun, moon, phase, moonUp, moonlight, horizonY, now, showSkyObjects);

        ColorMatrix cm;
        ColorMatrixColorFilter plantGrade = null;
        ColorMatrixColorFilter grade;
        int hazeColor;
        float hazeAlpha;

        if (showBackgroundFilter) {
            // trübes Wetter: dunkler, entsättigt; nass = dunkler und satter; verschneit = matter; Blitz hellt auf
            float light = moonlight - 0.3f * wx.cloud - 0.15f * Math.max(wx.rain, wx.snow) - 0.12f * wx.wetness
                    + flash * 0.6f;
            float saturation = (1 - 0.35f * wx.cloud) * (1 + 0.25f * wx.wetness) * (1 - 0.35f * wx.snowCover);
            float darkness = (float) section("look").optDouble("darkness", 0.7);

            cm = new ColorMatrix(mood.grade(light, saturation, darkness));
            if (seasonTint != null && seasonTint.onlyPlants()) {
                ColorMatrix pm = new ColorMatrix(cm);
                seasonTint.applyColorGrading(pm, seasonStrength);
                plantGrade = new ColorMatrixColorFilter(pm);
            } else if (seasonTint != null) {
                seasonTint.applyColorGrading(cm, seasonStrength);
            }
            grade = new ColorMatrixColorFilter(cm);

            hazeColor = Mood.c(mood.haze, mood.overcast(), Math.max(wx.fog, wx.cloud * 0.6f));
            hazeAlpha = Math.max(mood.hazeAlpha, Math.max(wx.fog * 0.85f, Math.max(wx.rain * 0.45f, wx.snow * 0.5f)))
                    * (0.5f + 0.5f * darkness);
        } else {
            // Hintergrundfilter AUS: Originales Foto ohne Abdunklung/Farbstich
            cm = new ColorMatrix();
            if (seasonTint != null && seasonTint.onlyPlants()) {
                ColorMatrix pm = new ColorMatrix();
                seasonTint.applyColorGrading(pm, seasonStrength);
                plantGrade = new ColorMatrixColorFilter(pm);
            } else if (seasonTint != null) {
                seasonTint.applyColorGrading(cm, seasonStrength);
            }
            grade = (seasonTint != null && !seasonTint.onlyPlants()) ? new ColorMatrixColorFilter(cm) : null;
            hazeColor = Mood.c(mood.haze, mood.overcast(), Math.max(wx.fog, wx.cloud * 0.6f));
            hazeAlpha = 0f;
        }

        frameLight = mood.brightness;
        frameGrade = grade;
        if (blendSky) {
            // Himmel nach Tageszeit: erst das ganze Foto, darüber der echte Himmel (nachts, bei Wolken) und die
            // Wolken durch die sehr weiche Maske. So gibt es keine harte Kante zum Foto (EdgeRefine.softSky).
            layerPaint.setColorFilter(grade);
            layerPaint.setAlpha(255);
            c.drawBitmap(scene.sky, fit, layerPaint);
            drawLand(c, scene, grade, plantGrade, hazeColor, hazeAlpha);
            float overcastFade = 1 - 0.9f * smoothstep(0.5f, 1f, wx.cloud);
            float replace = replaceSky ? 1 - mood.photoSky * overcastFade : 0;
            boolean clouds = showClouds && wx.cloud >= 0.02f;
            if (replace > 0.01f || clouds) {
                int layer = c.saveLayer(null, null);
                if (replace > 0.01f) {
                    int skyLayer = c.saveLayerAlpha(null, Math.round(replace * 255));
                    starVis = replace * drawSky(c, mood, wx, sun, moon, phase, moonUp, moonlight, horizonY, now, showSkyObjects);
                    c.restoreToCount(skyLayer);
                }
                if (clouds) fx.drawClouds(c, wx, cloudTint(mood, wx), now);
                layerPaint.setColorFilter(null);
                layerPaint.setAlpha(255);
                layerPaint.setXfermode(DST_IN);
                c.drawBitmap(skyBlend, fit, layerPaint);
                layerPaint.setXfermode(null);
                c.restoreToCount(layer);
            }
        } else {
            if (showClouds) fx.drawClouds(c, wx, cloudTint(mood, wx), now);
            if (scene != null) drawLand(c, scene, grade, plantGrade, hazeColor, hazeAlpha);
        }
        if (showParticles) fx.drawParticles(c, wx, season, false, mood.brightness);
        if (wx.fog > 0.05f) { // Nebelschleier auch über nahem Land, aber unter der Uhr
            fill.setShader(null);
            fill.setColor(hazeColor);
            fill.setAlpha(Math.round(wx.fog * 0.45f * 255));
            c.drawRect(0, 0, width, height, fill);
            fill.setAlpha(255);
        }

        float sceneLight = showBackgroundFilter ? (mood.brightness * (1 - 0.3f * wx.cloud)) : 1.0f;
        if (scene != null) { // Füllungen Bild und Milchglas brauchen Foto und Bildfarben dieses Frames
            clockText.setBackdrop(scene.photo, scene.photoSoft, fit, cm);
            for (TextLayer l : infoText.values()) l.setBackdrop(scene.photo, scene.photoSoft, fit, cm);
            widgetText.setBackdrop(scene.photo, scene.photoSoft, fit, cm);
        }
        final float light = sceneLight;
        final float night = effectiveNight;
        if (showClock) rotated(c, "clock", clockBoxNow(), () -> drawClock(c, scene, mood, time, light, night));

        if (scene != null && highlight != null) {
            // Vorschau beim Ziehen des Ebenen-Reglers: Motiv live aus der Maske, die vorberechnete Ebene ist noch alt
            int layer = c.saveLayer(null, null);
            layerPaint.setColorFilter(null);
            layerPaint.setColor(0xFF000000);
            c.drawBitmap(highlight, fit, layerPaint);
            layerPaint.setXfermode(SRC_IN);
            layerPaint.setColorFilter(grade);
            layerPaint.setAlpha(255);
            c.drawBitmap(scene.land, fit, layerPaint);
            layerPaint.setXfermode(null);
            c.restoreToCount(layer);
        } else if (scene != null && scene.subject != null) {
            layerPaint.setColorFilter(grade);
            layerPaint.setAlpha(255);
            c.drawBitmap(scene.subject, fit, layerPaint);
            drawPlants(c, scene.subjectPlants, plantGrade);
            drawHaze(c, scene.subjectHaze, hazeColor, hazeAlpha);
        }
        // Widgets auf eigener Ebene: erst über alles, dann was näher liegt nur auf ihrer Fläche wieder darüber
        final ColorMatrixColorFilter plants = plantGrade;
        Runnable land = () -> drawLand(c, scene, grade, plants, hazeColor, hazeAlpha);
        for (String id : drawOrder()) { // gespeicherte Reihenfolge: zuletzt gezeichnet liegt vorn
            if ("widget".equals(id)) {
                RectF box = widgetBox(time, wx);
                rotated(c, "widget", box, () -> drawWidget(c, scene, mood, time, wx, light, night));
                drawCut(c, scene, "widget", section("widget"), rotatedBox("widget", box), land);
            } else {
                JSONObject eff = infoCfg.get(id);
                RectF box = infoBox(id);
                rotated(c, id, box, () -> drawInfo(c, scene, mood, id, eff, light, night));
                drawCut(c, scene, id, eff, rotatedBox(id, box), land);
            }
        }
        if (showParticles) fx.drawParticles(c, wx, season, true, mood.brightness);
        fx.drawFlash(c, flash);
        if (highlight != null) { // Vorschau: markiert, was vor der Uhr liegt
            layerPaint.setColorFilter(null);
            layerPaint.setColor(highlightColor);
            c.drawBitmap(highlight, fit, layerPaint);
            layerPaint.setAlpha(255);
        }

        // Bildrate nach Bedarf: Regen/Schnee/Gewitter flüssig, Wolken/Sterne gemächlich, sonst statisch
        if (!motion) return 0;
        if (showParticles && (fx.hasParticles(wx, season) || wx.thunder)) return 33;
        if (nightSky.animating(starVis)) return 50;
        if (showClouds && wx.cloud > 0.02f) return 100;
        return 0;
    }

    private int cloudTint(Mood mood, Weather wx) {
        int lit = Mood.c(0xFFF4F6F8, mood.skyHorizon, 0.4f);
        float f = 0.3f + 0.7f * Math.min(1f, mood.brightness);
        lit = Color.rgb(Math.round(Color.red(lit) * f), Math.round(Color.green(lit) * f), Math.round(Color.blue(lit) * f));
        return Mood.c(lit, mood.overcast(), wx.cloud * 0.7f);
    }

    /** Zeichnet den gerenderten Himmel; gibt die Sternsichtbarkeit zurück. */
    private float drawSky(Canvas c, Mood mood, Weather wx, SkyPosition sun, SkyPosition moon, double phase,
                          float moonUp, float moonlight, float horizonY, long now, boolean showSkyObjects) {
        float clear = 1 - wx.cloud;
        int grey = mood.overcast();
        fill.setShader(new LinearGradient(0, 0, 0, horizonY * 1.3f, Mood.c(mood.skyTop, grey, wx.cloud * 0.85f),
                Mood.c(mood.skyHorizon, grey, wx.cloud * 0.85f), Shader.TileMode.CLAMP));
        c.drawRect(0, 0, width, height, fill);
        fill.setShader(null);

        if (!showSkyObjects) return 0f;

        // heller Mond überstrahlt schwache Sterne, Wolken verdecken sie
        float starVis = mood.stars * (1 - moonlight * 0.8f) * clear * clear;
        nightSky.drawStars(c, starVis, now);

        float[] mp = skyPoint(moon, horizonY);
        nightSky.drawMoon(c, mp[0], mp[1], width * 0.04f, phase,
                clamp01(mood.night * 1.4f) * moonUp * (1 - wx.cloud * 0.9f));

        float[] sp = skyPoint(sun, horizonY);
        float glowAlpha = mood.glowAlpha * (1 - wx.cloud * 0.8f);
        if (glowAlpha > 0.01f) {
            int glow = mood.glow;
            int core = Color.argb(Math.round(glowAlpha * 255), Color.red(glow), Color.green(glow), Color.blue(glow));
            fill.setShader(new RadialGradient(sp[0], sp[1], width * 0.75f, core, glow & 0x00FFFFFF, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, width, height, fill);
            fill.setShader(null);
        }
        if (sun.elevation > -1 && clear > 0.05f) {
            fill.setColor(Mood.c(0xFFFFB070, 0xFFFFFAF0, clamp01((float) sun.elevation / 15)));
            fill.setAlpha(Math.round(clear * 255));
            c.drawCircle(sp[0], sp[1], width * 0.035f, fill);
            fill.setAlpha(255);
        }
        return starVis;
    }

    /**
     * Bildschirmposition eines Himmelskörpers. Annahme: er ist immer im Bild (Blickrichtung egal),
     * Osten links, Westen rechts; hoch am Himmel nähert er sich asymptotisch dem oberen Rand.
     */
    private float[] skyPoint(SkyPosition pos, float horizonY) {
        float x = width * Math.max(0.12f, Math.min(0.88f, (float) (0.5 + (pos.azimuth - 180) / 225)));
        double up = 1 - Math.exp(-Math.max(pos.elevation, -5) / 20.0);
        float y = horizonY - (float) up * (horizonY - height * 0.06f);
        return new float[]{x, y};
    }

    /** Land mit Pflanzenfarbe und Dunst. */
    private void drawLand(Canvas c, Scene scene, ColorMatrixColorFilter grade, ColorMatrixColorFilter plantGrade,
                          int hazeColor, float hazeAlpha) {
        layerPaint.setColorFilter(grade);
        layerPaint.setAlpha(255);
        c.drawBitmap(scene.land, fit, layerPaint);
        drawPlants(c, scene.plants, plantGrade);
        drawHaze(c, scene.haze, hazeColor, hazeAlpha);
    }

    /** Info-Widget mit seinem Stil und allen Effekten. */
    private void drawInfo(Canvas c, Scene scene, Mood mood, String id, JSONObject eff, float sceneLight, float effectiveNight) {
        TextLayer l = infoText.get(id);
        if (l == null) return;
        l.prepare((scene != null ? scene.clockLuminance : 0.5f) * sceneLight, effectiveNight);
        WidgetParts.Content content = InfoWidgets.build(eff.optString("type"), eff, widgetData);
        if (content.isEmpty()) return;
        RectF box = infoBox(id);
        String align = eff.optString("align", "center");
        l.draw(c, box, p -> WidgetParts.draw(c, content, p, l.size(), box.left, box.top, align));
    }

    /** Was vor dem Element liegt (nach seiner Ebene), auf seiner Fläche wieder darüberzeichnen. */
    private void drawCut(Canvas c, Scene scene, String id, JSONObject cfg, RectF screenBox, Runnable land) {
        if (scene == null || scene.depth8 == null || screenBox.isEmpty()) return;
        Scene.Cut cut = cutFor(scene, id, (float) cfg.optDouble("layerThreshold", Double.NaN), screenBox);
        if (cut == null || cut.mask == null) return;
        Matrix m = new Matrix(fit);
        m.preTranslate(cut.x, cut.y);
        RectF area = new RectF(0, 0, cut.mask.getWidth(), cut.mask.getHeight());
        m.mapRect(area);
        int layer = c.saveLayer(area, null);
        land.run();
        layerPaint.setColorFilter(null);
        layerPaint.setAlpha(255);
        layerPaint.setXfermode(DST_IN);
        c.drawBitmap(cut.mask, m, layerPaint);
        layerPaint.setXfermode(null);
        c.restoreToCount(layer);
    }

    private Scene.Cut cutFor(Scene scene, String id, float threshold, RectF screenBox) {
        Matrix inv = new Matrix();
        fit.invert(inv);
        RectF b = new RectF(screenBox);
        inv.mapRect(b);
        String key = System.identityHashCode(scene) + "|" + Math.round(b.left / 4) + "," + Math.round(b.top / 4) + ","
                + Math.round(b.right / 4) + "," + Math.round(b.bottom / 4) + "|" + threshold;
        Object[] e = cuts.get(id);
        if (e != null && key.equals(e[0])) return (Scene.Cut) e[1];
        Scene.Cut cut = scene.cut(b, threshold);
        cuts.put(id, new Object[]{key, cut});
        return cut;
    }

    /** Pflanzen-Ebene mit Jahreszeitenfarbe über das normal gefärbte Land legen. */
    private void drawPlants(Canvas c, Bitmap plants, ColorMatrixColorFilter plantGrade) {
        if (plants == null || plantGrade == null) return;
        layerPaint.setColorFilter(plantGrade);
        layerPaint.setAlpha(255);
        c.drawBitmap(plants, fit, layerPaint);
    }

    private void drawHaze(Canvas c, Bitmap mask, int color, float alpha) {
        if (mask == null) return;
        layerPaint.setColorFilter(null);
        layerPaint.setColor(color);
        layerPaint.setAlpha(Math.round(alpha * 255));
        c.drawBitmap(mask, fit, layerPaint);
        layerPaint.setAlpha(255);
    }

    /** sceneLight: grobe Helligkeit der Szene (Tageszeit, Wolken), 0..1. */
    private void drawClock(Canvas c, Scene scene, Mood mood, long time, float sceneLight, float effectiveNight) {
        JSONObject clock = section("clock");
        clockText.prepare((scene != null ? scene.clockLuminance : 0.5f) * sceneLight, effectiveNight);
        ClockRenderer.draw(c, clockText, clock, timeFormat, time, width, height);
    }

    /** Datum-, Wetter- und Temperatur-Widget (Samsung-Sperrbildschirm-Stil), optional auf Glasfläche. */
    private void drawWidget(Canvas c, Scene scene, Mood mood, long time, Weather wx, float sceneLight, float effectiveNight) {
        JSONObject pos = section("widget");
        widgetText.prepare((scene != null ? scene.clockLuminance : 0.5f) * sceneLight, effectiveNight);
        float x = (float) (width * pos.optDouble("xPct", 50) / 100);
        float y = (float) (height * pos.optDouble("yPct", 36) / 100);
        float size = widgetText.size();
        if (widgetCfg.optBoolean("glass", false)) drawGlass(c, widgetBox(time, wx), size);
        Line[] lines = widgetLines(time, wx, effectiveNight > 0.5f);
        widgetText.draw(c, widgetBox(time, wx), p -> {
            for (int i = 0; i < lines.length; i++) {
                Line l = lines[i];
                float base = y + i * size * LINE_HEIGHT;
                float cx = widgetText.left(x, lineWidth(l));
                c.drawText(l.before, cx, base, p);
                cx += p.measureText(l.before);
                if (l.icon != null) {
                    WeatherIcon.draw(c, l.icon, cx, base, size, p);
                    cx += WeatherIcon.advance(size);
                }
                c.drawText(l.after, cx, base, p);
            }
        });
    }

    /** Milchige, abgerundete Fläche hinter dem Widget, passend zur Schriftfarbe hell oder dunkel. */
    private void drawGlass(Canvas c, RectF box, float size) {
        if (box.isEmpty()) return;
        RectF b = new RectF(box);
        b.inset(-size * 0.55f, -size * 0.35f);
        boolean lightText = Color.luminance(widgetText.shown()) > 0.4f;
        float op = widgetText.opacity();
        float r = size * 0.7f;
        fill.setShader(null);
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(TextLayer.withAlpha(lightText ? 0x52101418 : 0x80FFFFFF, op));
        c.drawRoundRect(b, r, r, fill);
        fill.setStyle(Paint.Style.STROKE);
        fill.setStrokeWidth(Math.max(1f, size * 0.04f));
        fill.setColor(TextLayer.withAlpha(lightText ? 0x40FFFFFF : 0x26000000, op));
        c.drawRoundRect(b, r, r, fill);
        fill.setStyle(Paint.Style.FILL);
    }

    static float clamp01(float x) {
        return x < 0 ? 0 : (x > 1 ? 1 : x);
    }

    static float smoothstep(float a, float b, float x) {
        float t = clamp01((x - a) / (b - a));
        return t * t * (3 - 2 * t);
    }
}
