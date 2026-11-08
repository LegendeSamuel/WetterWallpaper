package de.seelig.wetterwallpaper;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Die Info-Widgets: Typen, ihre Optionen (für die Bedienung) und ihr Inhalt aus den Daten eines Frames.
 * Jedes Widget ist ein eigener config-Abschnitt (type + Optionen + Stil wie Uhr/Datum).
 */
final class InfoWidgets {
    /** Typ, Name, Gruppe, Beschreibung, Standardgröße in % der Bildbreite. */
    static final String[][] TYPES = {
            {"clock_digital", "Digitale Uhr", "Uhr", "Klassische Digitaluhr im Querformat.", "32"},
            {"clock_analog", "Analoge Uhr", "Uhr", "Elegantes Zifferblatt mit Zeigern und Indizes.", "26"},
            {"clock_vertical", "Vertikale Uhr", "Uhr", "Moderne zweizeilige Digitaluhr: Stunde oben, Minute unten.", "28"},
            {"clock_words", "Wort-Uhr", "Uhr", "Die aktuelle Uhrzeit in Worten ausgeschrieben.", "12"},
            {"widget_weather", "Datum & Wetter", "Wetter", "Datum, Wochentag, Live-Wetter-Symbol und Temperatur.", "5.4"},
            {"hours", "Stunden-Vorschau", "Wetter", "Die nächsten Stunden mit Symbol und Temperatur.", "4.5"},
            {"days", "Tage-Vorschau", "Wetter", "Mehrere Tage mit Höchst- und Tiefstwert.", "4.5"},
            {"rain", "Regen in der nächsten Stunde", "Wetter", "Balken für die nächsten Viertelstunden und wann Regen kommt.", "4.5"},
            {"bigtemp", "Temperatur groß", "Wetter", "Große Zahl, darunter gefühlte Temperatur und Wetter.", "6"},
            {"details", "Wind, Feuchte, UV", "Wetter", "Wind mit Richtung, Luftfeuchte und UV-Index.", "4.2"},
            {"air", "Luftqualität & Pollen", "Wetter", "Europäischer Luftindex und die stärksten Pollen.", "4.2"},
            {"sunarc", "Sonnenbogen", "Himmel & Licht", "Tagesbogen mit Sonnenstand, Auf- und Untergang.", "4.2"},
            {"golden", "Goldene & blaue Stunde", "Himmel & Licht", "Wann das Licht warm wird und die blaue Stunde beginnt.", "4.2"},
            {"moon", "Mondphase", "Himmel & Licht", "Der Mond von heute, Name der Phase, beleuchteter Anteil.", "4.2"},
            {"stars", "Sternenhimmel heute", "Himmel & Licht", "Wie gut die Nacht für Sterne wird.", "4.2"},
            {"zone", "Zweite Zeitzone", "Zeit", "Eine zweite Uhrzeit mit Ortsname.", "4.5"},
            {"countdown", "Countdown", "Zeit", "Tage bis zu einem Datum, das du festlegst.", "4.5"},
            {"week", "Kalenderwoche", "Zeit", "KW und Tag des Jahres.", "4.2"},
            {"event", "Nächster Termin", "Persönlich", "Der nächste Termin aus deinem Kalender.", "4.4"},
            {"text", "Eigener Text", "Persönlich", "Ein Satz, ein Motto, ein Name.", "4.6"},
            {"battery", "Akku", "Gerät", "Akkustand als Ring oder Zahl.", "4.4"},
    };
    static final String[] GROUPS = {"Uhr", "Wetter", "Himmel & Licht", "Zeit", "Persönlich", "Gerät"};
    static final String[][] ZONES = {{"America/New_York", "New York"}, {"America/Los_Angeles", "Los Angeles"},
            {"America/Mexico_City", "Mexiko-Stadt"}, {"America/Sao_Paulo", "São Paulo"}, {"Europe/London", "London"},
            {"Europe/Lisbon", "Lissabon"}, {"Europe/Istanbul", "Istanbul"}, {"Africa/Johannesburg", "Johannesburg"},
            {"Asia/Dubai", "Dubai"}, {"Asia/Kolkata", "Indien"}, {"Asia/Bangkok", "Bangkok"}, {"Asia/Shanghai", "Peking"},
            {"Asia/Tokyo", "Tokio"}, {"Australia/Sydney", "Sydney"}, {"Pacific/Auckland", "Auckland"}};

    private InfoWidgets() {
    }

    static String[] type(String id) {
        for (String[] t : TYPES) if (t[0].equals(id)) return t;
        return TYPES[TYPES.length - 2]; // unbekannt: wie eigener Text
    }

    static String name(String type) {
        return type(type)[1];
    }

    static double defaultSize(String type) {
        return Double.parseDouble(type(type)[4]);
    }

    /** Daten eines Frames. */
    static final class Data {
        long now = System.currentTimeMillis();
        Weather wx = Weather.CLEAR;
        Forecast f = Forecast.EMPTY;
        double lat = 51;
        double lon = 10;
        int battery = -1;
        boolean charging;
        String[] event;
        boolean calendarGranted;
    }

    // ---------- Optionen ----------

    /** Eine Einstellung eines Widgets: Schalter, Auswahl, Regler oder Text. */
    static final class Opt {
        final String kind;
        final String key;
        final String label;
        final String def;
        final String[][] choices;
        final int min;
        final int max;
        final String unit;

        private Opt(String kind, String key, String label, String def, String[][] choices, int min, int max, String unit) {
            this.kind = kind;
            this.key = key;
            this.label = label;
            this.def = def;
            this.choices = choices;
            this.min = min;
            this.max = max;
            this.unit = unit;
        }

        static Opt sw(String key, String label, boolean def) {
            return new Opt("switch", key, label, String.valueOf(def), null, 0, 0, "");
        }

        static Opt choice(String key, String label, String def, String[][] choices) {
            return new Opt("choice", key, label, def, choices, 0, 0, "");
        }

        static Opt slider(String key, String label, int min, int max, int def, String unit) {
            return new Opt("slider", key, label, String.valueOf(def), null, min, max, unit);
        }

        static Opt text(String key, String label, String def) {
            return new Opt("text", key, label, def, null, 0, 0, "");
        }
    }

    private static Opt arrange(String def) {
        return Opt.choice("arrange", "Anordnung", def, new String[][]{{"row", "Nebeneinander"}, {"stack", "Gestapelt"}});
    }

    static List<Opt> options(String type) {
        List<Opt> o = new ArrayList<>();
        switch (type) {
            case "hours":
                o.add(arrange("row"));
                o.add(Opt.slider("count", "Anzahl Stunden", 1, 12, 5, ""));
                o.add(Opt.slider("step", "Abstand", 1, 3, 1, "h"));
                o.add(Opt.sw("showIcon", "Symbol", true));
                o.add(Opt.sw("showChance", "Regenwahrscheinlichkeit", false));
                break;
            case "days":
                o.add(arrange("row"));
                o.add(Opt.slider("count", "Anzahl Tage", 1, 7, 4, ""));
                o.add(Opt.sw("showIcon", "Symbol", true));
                o.add(Opt.sw("showMin", "Tiefstwert", true));
                break;
            case "rain":
                o.add(arrange("stack"));
                o.add(Opt.choice("span", "Zeitraum", "1", new String[][]{{"1", "1 Stunde"}, {"2", "2 Stunden"}}));
                o.add(Opt.sw("showBars", "Balken", true));
                o.add(Opt.sw("showText", "Text", true));
                o.add(Opt.sw("hideDry", "Nur zeigen, wenn Regen kommt", false));
                break;
            case "bigtemp":
                o.add(arrange("stack"));
                o.add(Opt.sw("showFeels", "Gefühlte Temperatur", true));
                o.add(Opt.sw("showDesc", "Wetter in Worten", true));
                break;
            case "details":
                o.add(arrange("row"));
                o.add(Opt.sw("showWind", "Wind", true));
                o.add(Opt.sw("showHumidity", "Luftfeuchte", true));
                o.add(Opt.sw("showUv", "UV-Index", true));
                break;
            case "air":
                o.add(arrange("stack"));
                o.add(Opt.sw("showAqi", "Luftqualität", true));
                o.add(Opt.sw("showPollen", "Pollen", true));
                o.add(Opt.slider("pollenCount", "Anzahl Pollenarten", 1, 5, 2, ""));
                break;
            case "sunarc":
                o.add(arrange("stack"));
                o.add(Opt.sw("showArc", "Bogen", true));
                o.add(Opt.sw("showTimes", "Auf- und Untergang", true));
                break;
            case "golden":
                o.add(arrange("stack"));
                o.add(Opt.sw("showCountdown", "Goldene Stunde", true));
                o.add(Opt.sw("showBlue", "Blaue Stunde", true));
                break;
            case "moon":
                o.add(arrange("row"));
                o.add(Opt.sw("showDisc", "Mondscheibe", true));
                o.add(Opt.sw("showName", "Name der Phase", true));
                o.add(Opt.sw("showPct", "Beleuchtet in %", true));
                o.add(Opt.sw("showNextFull", "Tage bis Vollmond", true));
                break;
            case "stars":
                o.add(arrange("stack"));
                o.add(Opt.sw("showDetail", "Ab wann klar", true));
                break;
            case "zone":
                o.add(arrange("row"));
                o.add(Opt.choice("zone", "Ort", "America/New_York", ZONES));
                o.add(Opt.text("label", "Eigener Name (leer = Ort)", ""));
                o.add(Opt.sw("showOffset", "Zeitunterschied", true));
                break;
            case "countdown":
                o.add(arrange("row"));
                o.add(Opt.text("date", "Datum (JJJJ-MM-TT)", nextYear() + "-01-01"));
                o.add(Opt.text("label", "Beschriftung", "bis Neujahr"));
                break;
            case "week":
                o.add(arrange("row"));
                o.add(Opt.sw("showDay", "Tag des Jahres", true));
                break;
            case "event":
                o.add(arrange("row"));
                o.add(Opt.slider("maxChars", "Länge des Titels", 8, 60, 30, ""));
                break;
            case "text":
                o.add(Opt.text("text", "Text", "Wäre es einfach, wäre es langweilig."));
                break;
            case "battery":
                o.add(arrange("row"));
                o.add(Opt.choice("style", "Darstellung", "ring", new String[][]{{"ring", "Ring + Zahl"}, {"number", "Nur Zahl"}}));
                break;
            default:
                break;
        }
        return o;
    }

    private static int nextYear() {
        return Calendar.getInstance().get(Calendar.YEAR) + 1;
    }

    // ---------- Inhalt ----------

    private static boolean b(JSONObject c, String k, boolean d) {
        return c.optBoolean(k, d);
    }

    private static String temp(float t) {
        return Float.isNaN(t) ? "–" : Math.round(t) + "°";
    }

    private static String hm(long ms) {
        return ms < 0 ? "–" : new SimpleDateFormat("HH:mm", Locale.GERMANY).format(new Date(ms));
    }

    /** Inhalt eines Widgets aus seiner Config und den Frame-Daten. */
    static WidgetParts.Content build(String type, JSONObject cfg, Data d) {
        boolean row = "row".equals(cfg.optString("arrange", defaultArrange(type)));
        boolean up = cfg.optBoolean("uppercase", false);
        WidgetParts.Content c = new WidgetParts.Content(row);
        Forecast f = d.f;
        switch (type) {
            case "hours": {
                int start = f.hourIndex(d.now);
                int n = cfg.optInt("count", 5);
                int step = Math.max(1, cfg.optInt("step", 1));
                if (start < 0) return c.add(t("Vorschau lädt …", 0.8f, up));
                for (int k = 0; k < n; k++) {
                    int i = start + k * step;
                    if (i >= f.hourTime.length) break;
                    Calendar cal = Calendar.getInstance();
                    cal.setTimeInMillis(f.hourTime[i] * 1000);
                    String label = k == 0 ? "Jetzt" : cal.get(Calendar.HOUR_OF_DAY) + " Uhr";
                    boolean night = SkyPosition.sun(f.hourTime[i] * 1000, d.lat, d.lon).elevation < -2;
                    c.add(t(label, 0.62f, up, true),
                            b(cfg, "showIcon", true) ? WidgetParts.icon(Forecast.iconFor(at(f.hourCode, i), at(f.hourCloud, i), night), 1f) : null,
                            t(temp(at(f.hourTemp, i)), 0.95f, up),
                            b(cfg, "showChance", false) ? t(Math.round(nz(at(f.hourRainChance, i))) + " %", 0.55f, up, true) : null);
                }
                return c;
            }
            case "days": {
                int start = f.dayIndex(d.now);
                int n = cfg.optInt("count", 4);
                if (start < 0) return c.add(t("Vorschau lädt …", 0.8f, up));
                for (int k = 0; k < n && start + k < f.dayTime.length; k++) {
                    int i = start + k;
                    String label = k == 0 ? "Heute" : new SimpleDateFormat("EE", Locale.GERMANY).format(new Date(f.dayTime[i] * 1000));
                    c.add(t(label, 0.62f, up, true),
                            b(cfg, "showIcon", true) ? WidgetParts.icon(Forecast.iconFor(at(f.dayCode, i), Float.NaN, false), 1f) : null,
                            t(temp(at(f.dayMax, i)), 0.95f, up),
                            b(cfg, "showMin", true) ? t(temp(at(f.dayMin, i)), 0.62f, up, true) : null);
                }
                return c;
            }
            case "rain": {
                int n = Math.min(f.q15Rain.length, "2".equals(cfg.optString("span", "1")) ? 8 : 4);
                if (n == 0) return c.add(t("Regen-Vorschau lädt …", 0.8f, up));
                float[] v = new float[n];
                boolean any = false;
                for (int i = 0; i < n; i++) {
                    v[i] = Math.min(1, nz(f.q15Rain[i]) / 1.5f); // 1,5 mm je Viertelstunde = kräftig
                    any |= f.q15Rain[i] > 0.05f;
                }
                if (!any && b(cfg, "hideDry", false)) return c;
                if (b(cfg, "showBars", true)) c.add(WidgetParts.bars(v));
                if (b(cfg, "showText", true)) c.add(t(rainText(f, n), 0.8f, up));
                return c;
            }
            case "widget_weather": { // weiteres Datum & Wetter
                boolean night = SkyPosition.sun(d.now, d.lat, d.lon).elevation < -2;
                String date = new SimpleDateFormat("EEE, d. MMM", Locale.GERMANY).format(new Date(d.now));
                return c.add(t(date + "  " + d.wx.weatherSymbol(night) + " " + d.wx.temperatureText(), 1f, up));
            }
            case "bigtemp": {
                c.add(t(temp(d.wx.temperature), 2.6f, up));
                StringBuilder sub = new StringBuilder();
                if (b(cfg, "showFeels", true) && !Float.isNaN(f.apparent)) sub.append("gefühlt ").append(temp(f.apparent));
                if (b(cfg, "showDesc", true)) sub.append(sub.length() > 0 ? " · " : "").append(describe(d.wx.code));
                if (sub.length() > 0) c.add(t(sub.toString(), 0.6f, up, true));
                return c;
            }
            case "details":
                if (b(cfg, "showWind", true)) c.add(t("Wind", 0.55f, up, true),
                        t(Float.isNaN(f.windSpeed) ? "–" : Math.round(f.windSpeed) + " km/h " + arrow(f.windDir), 0.85f, up));
                if (b(cfg, "showHumidity", true)) c.add(t("Feuchte", 0.55f, up, true),
                        t(Float.isNaN(f.humidity) ? "–" : Math.round(f.humidity) + " %", 0.85f, up));
                if (b(cfg, "showUv", true)) c.add(t("UV", 0.55f, up, true),
                        t(Float.isNaN(f.uv) ? "–" : String.valueOf(Math.round(f.uv)), 0.85f, up));
                return c;
            case "air": {
                if (b(cfg, "showAqi", true)) c.add(t("Luft " + aqiWord(f.aqi), 0.85f, up));
                if (b(cfg, "showPollen", true)) {
                    int count = cfg.optInt("pollenCount", 2);
                    for (int k : strongestPollen(f.pollen, count)) {
                        c.add(t(Forecast.POLLEN_NAMES[k] + " " + pollenWord(f.pollen[k]), 0.7f, up, true));
                    }
                    if (f.pollen.length == 0) c.add(t("Pollen laden …", 0.7f, up, true));
                }
                return c;
            }
            case "sunarc": {
                long[] sun = SkyTimes.sunTimes(d.now, d.lat, d.lon, TimeZone.getDefault());
                float progress = sun[0] < 0 || sun[1] < 0 ? -1 : (d.now - sun[0]) / (float) (sun[1] - sun[0]);
                if (b(cfg, "showArc", true)) c.add(WidgetParts.arc(progress));
                if (b(cfg, "showTimes", true)) c.add(t("↑ " + hm(sun[0]) + "   ↓ " + hm(sun[1]), 0.65f, up, true));
                return c;
            }
            case "golden": {
                if (b(cfg, "showCountdown", true)) {
                    long[] g = SkyTimes.nextGolden(d.now, d.lat, d.lon);
                    String s = g == null ? "Keine goldene Stunde" : g[0] <= d.now ? "Goldene Stunde jetzt · bis " + hm(g[1])
                            : "Goldene Stunde in " + duration(g[0] - d.now);
                    c.add(t(s, 0.85f, up));
                }
                if (b(cfg, "showBlue", true)) {
                    long[] bl = SkyTimes.nextBlue(d.now, d.lat, d.lon);
                    if (bl != null) c.add(t("Blaue Stunde " + hm(bl[0]) + " – " + hm(bl[1]), 0.62f, up, true));
                }
                return c;
            }
            case "moon": {
                double phase = SkyPosition.moonPhase(d.now);
                if (b(cfg, "showDisc", true)) c.add(WidgetParts.moon(phase));
                List<WidgetParts.Part> lines = new ArrayList<>();
                if (b(cfg, "showName", true)) lines.add(t(SkyTimes.moonName(phase), 0.85f, up));
                StringBuilder s = new StringBuilder();
                if (b(cfg, "showPct", true)) s.append(Math.round(SkyPosition.moonIllumination(phase) * 100)).append(" % beleuchtet");
                if (b(cfg, "showNextFull", true)) {
                    int days = SkyTimes.daysToFullMoon(d.now);
                    s.append(s.length() > 0 ? " · " : "").append(days == 0 ? "heute Vollmond" : "Vollmond in " + days + (days == 1 ? " Tag" : " Tagen"));
                }
                if (s.length() > 0) lines.add(t(s.toString(), 0.6f, up, true));
                if (!lines.isEmpty()) c.add(lines.toArray(new WidgetParts.Part[0]));
                return c;
            }
            case "stars": {
                float[] night = nightClouds(f, d);
                float score = SkyTimes.starScore(night[0], SkyPosition.moonIllumination(SkyPosition.moonPhase(d.now)));
                c.add(t("Sterne heute Nacht " + (f.hourTime.length == 0 ? "…" : SkyTimes.starWord(score)), 0.85f, up));
                if (b(cfg, "showDetail", true) && f.hourTime.length > 0) {
                    c.add(t(night[1] < 0 ? "die ganze Nacht bewölkt" : "klar ab " + hm((long) night[1]), 0.62f, up, true));
                }
                return c;
            }
            case "zone": {
                String id = cfg.optString("zone", "America/New_York");
                TimeZone tz = TimeZone.getTimeZone(id);
                SimpleDateFormat fmt = new SimpleDateFormat("HH:mm", Locale.GERMANY);
                fmt.setTimeZone(tz);
                String label = cfg.optString("label", "");
                if (label.isEmpty()) for (String[] z : ZONES) if (z[0].equals(id)) label = z[1];
                int diff = Math.round((tz.getOffset(d.now) - TimeZone.getDefault().getOffset(d.now)) / 3600_000f);
                c.add(t(fmt.format(new Date(d.now)), 1.8f, up));
                c.add(t(label, 0.8f, up), b(cfg, "showOffset", true)
                        ? t(diff == 0 ? "gleiche Zeit" : (diff > 0 ? "+" : "−") + Math.abs(diff) + " h", 0.6f, up, true) : null);
                return c;
            }
            case "countdown": {
                long target = parseDate(cfg.optString("date", nextYear() + "-01-01"));
                if (target < 0) return c.add(t("Datum als JJJJ-MM-TT eingeben", 0.7f, up));
                long days = Math.round((target - SkyTimes.startOfDay(d.now, TimeZone.getDefault())) / 86400_000.0);
                String label = cfg.optString("label", "bis Neujahr");
                c.add(t(String.valueOf(Math.abs(days)), 2.2f, up));
                c.add(t(days == 1 || days == -1 ? "Tag" : "Tage", 0.8f, up), label.isEmpty() ? null
                        : t(days < 0 ? label.replaceFirst("^bis ", "seit ") : label, 0.6f, up, true));
                return c;
            }
            case "week": {
                Calendar cal = Calendar.getInstance(Locale.GERMANY);
                cal.setTimeInMillis(d.now);
                c.add(t("KW " + cal.get(Calendar.WEEK_OF_YEAR), 0.9f, up));
                if (b(cfg, "showDay", true)) {
                    c.add(t("Tag " + cal.get(Calendar.DAY_OF_YEAR) + " von " + cal.getActualMaximum(Calendar.DAY_OF_YEAR), 0.6f, up, true));
                }
                return c;
            }
            case "event": {
                if (!d.calendarGranted) return c.add(t("Kalender freigeben", 0.75f, up));
                if (d.event == null) return c.add(t("Keine Termine", 0.75f, up, true));
                String title = d.event[1];
                int max = cfg.optInt("maxChars", 30);
                if (title.length() > max) title = title.substring(0, Math.max(1, max - 1)) + "…";
                return c.add(t(d.event[0], 0.85f, up)).add(t(title, 0.85f, up));
            }
            case "battery": {
                int pct = d.battery;
                if ("ring".equals(cfg.optString("style", "ring"))) c.add(WidgetParts.ring(pct < 0 ? 0 : pct / 100f));
                c.add(t((pct < 0 ? "–" : pct + " %") + (d.charging ? " ⚡" : ""), 1f, up));
                return c;
            }
            default: { // text
                for (String line : cfg.optString("text", "Wäre es einfach, wäre es langweilig.").split("\n")) c.add(t(line, 1f, up));
                return c;
            }
        }
    }

    /** Standard-Anordnung je Typ (wie in options()). */
    static String defaultArrange(String type) {
        for (Opt o : options(type)) if (o.key.equals("arrange")) return o.def;
        return "stack";
    }

    private static WidgetParts.Part t(String s, float scale, boolean up) {
        return t(s, scale, up, false);
    }

    private static WidgetParts.Part t(String s, float scale, boolean up, boolean dim) {
        return WidgetParts.text(up ? s.toUpperCase(Locale.GERMANY) : s, scale, dim);
    }

    private static float at(float[] a, int i) {
        return i < a.length ? a[i] : Float.NaN;
    }

    private static int at(int[] a, int i) {
        return i < a.length ? a[i] : 0;
    }

    private static float nz(float v) {
        return Float.isNaN(v) ? 0 : v;
    }

    private static String rainText(Forecast f, int n) {
        boolean rainingNow = f.q15Rain[0] > 0.05f;
        for (int i = 1; i < n; i++) {
            boolean r = f.q15Rain[i] > 0.05f;
            if (r != rainingNow) return (rainingNow ? "Regen bis " : "Regen ab ") + hm(f.q15Time[i] * 1000);
        }
        return rainingNow ? "Regen hält an" : "Kein Regen in Sicht";
    }

    private static String duration(long ms) {
        long min = Math.max(0, ms / 60000);
        return min >= 60 ? (min / 60) + " h " + (min % 60) + " min" : min + " min";
    }

    /** Pfeil, wohin der Wind weht (Open-Meteo gibt an, woher er kommt). */
    private static String arrow(float from) {
        if (Float.isNaN(from)) return "";
        String[] a = {"↓", "↙", "←", "↖", "↑", "↗", "→", "↘"}; // Wind aus Nord weht nach Süden
        return a[Math.round(((from % 360) + 360) % 360 / 45f) % 8];
    }

    private static String aqiWord(int aqi) {
        if (aqi < 0) return "…";
        if (aqi <= 20) return "sehr gut";
        if (aqi <= 40) return "gut";
        if (aqi <= 60) return "mäßig";
        if (aqi <= 80) return "schlecht";
        if (aqi <= 100) return "sehr schlecht";
        return "extrem schlecht";
    }

    private static String pollenWord(float grains) {
        if (grains < 1) return "keine";
        if (grains < 20) return "gering";
        if (grains < 100) return "mäßig";
        return "stark";
    }

    private static int[] strongestPollen(float[] p, int count) {
        Integer[] idx = new Integer[p.length];
        for (int i = 0; i < p.length; i++) idx[i] = i;
        java.util.Arrays.sort(idx, (a, b) -> Float.compare(p[b], p[a]));
        int n = Math.min(count, p.length);
        int[] r = new int[n];
        for (int i = 0; i < n; i++) r[i] = idx[i];
        return r;
    }

    /** {mittlere Bewölkung 0..1 der dunklen Stunden, erste klare dunkle Stunde in ms oder -1}. */
    private static float[] nightClouds(Forecast f, Data d) {
        float sum = 0;
        int n = 0;
        float clearAt = -1;
        int start = Math.max(0, f.hourIndex(d.now));
        for (int i = start; i < f.hourTime.length && i < start + 18; i++) {
            long t = f.hourTime[i] * 1000;
            if (SkyPosition.sun(t, d.lat, d.lon).elevation > -12) continue; // nur richtig dunkle Stunden
            float cl = nz(at(f.hourCloud, i)) / 100f;
            sum += cl;
            n++;
            if (clearAt < 0 && cl < 0.3f) clearAt = t;
        }
        return new float[]{n == 0 ? 1 : sum / n, clearAt};
    }

    private static long parseDate(String s) {
        try {
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.GERMANY);
            f.setLenient(false);
            return f.parse(s.trim()).getTime();
        } catch (Exception e) {
            return -1;
        }
    }

    /** Wetter in Worten aus dem WMO-Code. */
    static String describe(int code) {
        if (code == 0) return "klar";
        if (code == 1) return "überwiegend klar";
        if (code == 2) return "teils bewölkt";
        if (code == 3) return "bedeckt";
        if (code == 45 || code == 48) return "Nebel";
        if (code >= 51 && code <= 57) return "Niesel";
        if (code >= 61 && code <= 65) return "Regen";
        if (code == 66 || code == 67) return "gefrierender Regen";
        if (code >= 71 && code <= 77) return "Schnee";
        if (code >= 80 && code <= 82) return "Schauer";
        if (code == 85 || code == 86) return "Schneeschauer";
        if (code >= 95) return "Gewitter";
        return "";
    }
}
