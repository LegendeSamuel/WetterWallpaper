package de.seelig.wetterwallpaper;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * Zeichnet die Uhr in verschiedenen Presets / Stilen:
 * - digital: Klassische einzeilige Digitaluhr
 * - analog: Elegantes Zifferblatt mit Stunden-/Minutenzeigern und Indizes
 * - vertical: Zweizeilige moderne Digitaluhr (Stunde über Minute)
 * - words: Uhrzeit in deutschen Worten (exakt oder umgangssprachlich)
 *
 * Alle Stile nutzen TextLayer.draw(), wodurch sämtliche Effekte (Neon, Glow,
 * Schatten, Farbverläufe, Glas, Kontur, Deckkraft) nahtlos übernommen werden.
 */
final class ClockRenderer {
    private static final String[] HOURS = {
            "Zwölf", "Eins", "Zwei", "Drei", "Vier", "Fünf",
            "Sechs", "Sieben", "Acht", "Neun", "Zehn", "Elf"
    };

    private static final String[] ONES = {
            "", "ein", "zwei", "drei", "vier", "fünf", "sechs", "sieben", "acht", "neun"
    };

    private static final String[] TEENS = {
            "zehn", "elf", "zwölf", "dreizehn", "vierzehn", "fünfzehn",
            "sechzehn", "siebzehn", "achtzehn", "neunzehn"
    };

    private static final String[] TENS = {
            "", "", "zwanzig", "dreißig", "vierzig", "fünfzig"
    };

    private ClockRenderer() {
    }

    /** Berechnet den Begrenzungsrahmen der Uhr für Hit-Testing, Outline und Ebenenmaske. */
    static RectF clockBox(TextLayer layer, JSONObject clock, SimpleDateFormat timeFormat, int width, int height) {
        return clockBox(layer, clock, timeFormat, width, height, clock.optString("style", "digital"));
    }

    /** Berechnet den Begrenzungsrahmen für einen bestimmten Stil (Tag oder Nacht). */
    static RectF clockBox(TextLayer layer, JSONObject clock, SimpleDateFormat timeFormat, int width, int height, String style) {
        float x = (float) (width * clock.optDouble("xPct", 50) / 100);
        float y = (float) (height * clock.optDouble("yPct", 33) / 100);
        float size = layer.size();

        switch (style != null ? style : "digital") {
            case "analog": {
                float r = size * 0.95f;
                return new RectF(x - r, y - r, x + r, y + r);
            }
            case "vertical": {
                float lineSpacing = size * 0.88f;
                float w = layer.paint.measureText("00");
                boolean showAmPm = clock.optBoolean("vertical12h", false) && clock.optBoolean("verticalAmPm", false);
                float extraRight = showAmPm ? size * 0.45f : 0;
                float left = layer.left(x, w);
                return new RectF(left, y - size * 0.75f, left + w + extraRight, y - size * 0.1f + lineSpacing + size * 0.05f);
            }
            case "column": {
                String[] d = columnDigits(clock, Calendar.getInstance());
                float lh = size * COLUMN_LINE;
                float w = columnWidth(layer);
                float left = layer.left(x, w);
                return new RectF(left, y - size * 0.75f, left + w, y + (d.length - 1) * lh + size * COLUMN_GAP + size * 0.05f);
            }
            case "words": {
                float w = layer.paint.measureText("ZWEIUNDFÜNFZIG");
                float left = layer.left(x, w);
                return new RectF(left, y - size * 0.75f, left + w, y + size * 1.05f);
            }
            default: { // digital
                String text = timeFormat.format(new Date());
                float w = layer.paint.measureText(text);
                float left = layer.left(x, w);
                return new RectF(left, y - size * 0.72f, left + w, y + size * 0.05f);
            }
        }
    }

    /** Zeichnet die Uhr im konfigurierten Stil. */
    static void draw(Canvas c, TextLayer layer, JSONObject clock, SimpleDateFormat timeFormat,
                     long time, int width, int height) {
        draw(c, layer, clock, timeFormat, time, width, height, false);
    }

    /** Zeichnet die Uhr mit Beachtung des Nacht-Stils falls aktiv. */
    static void draw(Canvas c, TextLayer layer, JSONObject clock, SimpleDateFormat timeFormat,
                     long time, int width, int height, boolean isNight) {
        float x = (float) (width * clock.optDouble("xPct", 50) / 100);
        float y = (float) (height * clock.optDouble("yPct", 33) / 100);
        float size = layer.size();

        String nightStyle = clock.optString("styleNight", "");
        String style = (isNight && !nightStyle.isEmpty() && !"same".equals(nightStyle))
                ? nightStyle : clock.optString("style", "digital");

        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(time);

        switch (style) {
            case "analog":
                drawAnalog(c, layer, clock, cal, x, y, size);
                break;
            case "vertical":
                drawVertical(c, layer, clock, cal, x, y, size);
                break;
            case "words":
                drawWords(c, layer, clock, cal, x, y, size);
                break;
            case "column":
                drawColumn(c, layer, clock, cal, x, y, size);
                break;
            default:
                drawDigital(c, layer, timeFormat, time, x, y, size);
                break;
        }
    }

    // ---------- 1. Digital ----------

    private static void drawDigital(Canvas c, TextLayer layer, SimpleDateFormat timeFormat,
                                    long time, float x, float y, float size) {
        String text = timeFormat.format(new Date(time));
        float w = layer.paint.measureText(text);
        float left = layer.left(x, w);
        RectF box = new RectF(left, y - size * 0.75f, left + w, y + size * 0.05f);
        layer.draw(c, box, p -> c.drawText(text, left, y, p));
    }

    // ---------- 2. Analog ----------

    private static void drawAnalog(Canvas c, TextLayer layer, JSONObject clock,
                                   Calendar cal, float cx, float cy, float size) {
        float r = size * 0.95f;
        RectF box = new RectF(cx - r, cy - r, cx + r, cy + r);

        int hour = cal.get(Calendar.HOUR);
        int minute = cal.get(Calendar.MINUTE);
        int second = cal.get(Calendar.SECOND);
        int millis = cal.get(Calendar.MILLISECOND);

        float hourAngle = (hour + minute / 60f + second / 3600f) * 30f;
        float minAngle = (minute + second / 60f) * 6f;
        float secAngle = (second + millis / 1000f) * 6f;

        String dial = clock.optString("analogDial", "ticks");
        boolean showSec = clock.optBoolean("analogSeconds", false);

        layer.draw(c, box, p -> {
            Paint.Style oldStyle = p.getStyle();
            float oldStroke = p.getStrokeWidth();
            Paint.Cap oldCap = p.getStrokeCap();
            float oldTextSize = p.getTextSize();
            Paint.Align oldAlign = p.getTextAlign();

            // 1. Zifferblatt
            drawDial(c, p, dial, cx, cy, r);

            // 2. Zeiger
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);

            // Stundenzeiger
            double ha = Math.toRadians(hourAngle);
            p.setStrokeWidth(Math.max(3.5f, r * 0.07f));
            c.drawLine(cx, cy, (float) (cx + Math.sin(ha) * r * 0.54f), (float) (cy - Math.cos(ha) * r * 0.54f), p);

            // Minutenzeiger
            double ma = Math.toRadians(minAngle);
            p.setStrokeWidth(Math.max(2.2f, r * 0.042f));
            c.drawLine(cx, cy, (float) (cx + Math.sin(ma) * r * 0.82f), (float) (cy - Math.cos(ma) * r * 0.82f), p);

            // Sekundenzeiger (optional)
            if (showSec) {
                double sa = Math.toRadians(secAngle);
                p.setStrokeWidth(Math.max(1.2f, r * 0.02f));
                c.drawLine(cx, cy, (float) (cx + Math.sin(sa) * r * 0.88f), (float) (cy - Math.cos(sa) * r * 0.88f), p);
            }

            // 3. Zentrum-Pin
            p.setStyle(Paint.Style.FILL);
            c.drawCircle(cx, cy, Math.max(3.5f, r * 0.065f), p);

            // Zustand wiederherstellen
            p.setStyle(oldStyle);
            p.setStrokeWidth(oldStroke);
            p.setStrokeCap(oldCap);
            p.setTextSize(oldTextSize);
            p.setTextAlign(oldAlign);
        });
    }

    private static void drawDial(Canvas c, Paint p, String dial, float cx, float cy, float r) {
        if ("minimal".equals(dial)) return;

        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);

        if ("ticks".equals(dial)) {
            for (int i = 0; i < 12; i++) {
                double a = Math.toRadians(i * 30);
                boolean major = (i % 3 == 0);
                float innerR = major ? r * 0.78f : r * 0.85f;
                float outerR = r * 0.95f;
                p.setStrokeWidth(major ? Math.max(2.5f, r * 0.045f) : Math.max(1.5f, r * 0.024f));
                c.drawLine((float) (cx + Math.sin(a) * innerR), (float) (cy - Math.cos(a) * innerR),
                        (float) (cx + Math.sin(a) * outerR), (float) (cy - Math.cos(a) * outerR), p);
            }
        } else if ("numbers4".equals(dial)) {
            p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(r * 0.24f);
            p.setStyle(Paint.Style.FILL);

            c.drawText("12", cx, cy - r * 0.72f, p);
            c.drawText("6", cx, cy + r * 0.86f, p);
            c.drawText("3", cx + r * 0.80f, cy + r * 0.08f, p);
            c.drawText("9", cx - r * 0.80f, cy + r * 0.08f, p);

            // Kleinere Punkte für die restlichen 8 Stunden
            for (int i = 0; i < 12; i++) {
                if (i % 3 != 0) {
                    double a = Math.toRadians(i * 30);
                    c.drawCircle((float) (cx + Math.sin(a) * r * 0.85f),
                            (float) (cy - Math.cos(a) * r * 0.85f), r * 0.024f, p);
                }
            }
        } else if ("numbers12".equals(dial)) {
            p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(r * 0.20f);
            p.setStyle(Paint.Style.FILL);

            for (int i = 1; i <= 12; i++) {
                double a = Math.toRadians(i * 30);
                float numR = r * 0.78f;
                float nx = (float) (cx + Math.sin(a) * numR);
                float ny = (float) (cy - Math.cos(a) * numR + r * 0.07f);
                c.drawText(String.valueOf(i), nx, ny, p);
            }
        }
    }

    // ---------- 3. Vertical (Zweizeilig) ----------

    private static void drawVertical(Canvas c, TextLayer layer, JSONObject clock,
                                     Calendar cal, float x, float y, float size) {
        boolean is12h = clock.optBoolean("vertical12h", false);
        int h;
        if (is12h) {
            h = cal.get(Calendar.HOUR);
            if (h == 0) h = 12;
        } else {
            h = cal.get(Calendar.HOUR_OF_DAY);
        }
        int m = cal.get(Calendar.MINUTE);
        boolean zero = clock.optBoolean("leadingZero", true);

        String hStr = String.format(Locale.GERMANY, zero ? "%02d" : "%d", h);
        String mStr = String.format(Locale.GERMANY, "%02d", m);

        float lineSpacing = size * 0.88f;
        float y1 = y - size * 0.1f;
        float y2 = y1 + lineSpacing;

        float w1 = layer.paint.measureText(hStr);
        float w2 = layer.paint.measureText(mStr);
        float left1 = layer.left(x, w1);
        float left2 = layer.left(x, w2);

        float minLeft = Math.min(left1, left2);
        float maxRight = Math.max(left1 + w1, left2 + w2);
        boolean showAmPm = is12h && clock.optBoolean("verticalAmPm", false);
        float extraRight = showAmPm ? size * 0.45f : 0;
        RectF box = new RectF(minLeft, y1 - size * 0.75f, maxRight + extraRight, y2 + size * 0.05f);

        String amPm = cal.get(Calendar.AM_PM) == Calendar.AM ? "AM" : "PM";

        layer.draw(c, box, p -> {
            c.drawText(hStr, left1, y1, p);
            c.drawText(mStr, left2, y2, p);
            if (showAmPm) {
                float oldTextSize = p.getTextSize();
                p.setTextSize(oldTextSize * 0.28f);
                c.drawText(amPm, maxRight + size * 0.06f, y1, p);
                p.setTextSize(oldTextSize);
            }
        });
    }

    // ---------- 4. Spalte (eine Ziffer pro Zeile) ----------

    /** Zeilenabstand und Lücke zwischen Stunden und Minuten, in Schriftgrößen. */
    private static final float COLUMN_LINE = 0.92f;
    private static final float COLUMN_GAP = 0.3f;

    /** Ziffern von oben nach unten; "columnUp" = von unten nach oben lesen. Letzter Eintrag: Index der Lücke. */
    static String[] columnDigits(JSONObject clock, Calendar cal) {
        int h = cal.get(Calendar.HOUR_OF_DAY);
        if (clock.optBoolean("vertical12h", false)) {
            h = cal.get(Calendar.HOUR);
            if (h == 0) h = 12;
        }
        String hs = String.format(Locale.GERMANY, clock.optBoolean("leadingZero", true) ? "%02d" : "%d", h);
        String ms = String.format(Locale.GERMANY, "%02d", cal.get(Calendar.MINUTE));
        String all = hs + ms;
        String[] d = new String[all.length()];
        for (int i = 0; i < d.length; i++) d[i] = String.valueOf(all.charAt(i));
        if (clock.optBoolean("columnUp", false)) java.util.Collections.reverse(java.util.Arrays.asList(d));
        return d;
    }

    /** Erste Zeile nach der Lücke zwischen Stunden und Minuten. */
    private static int columnSplit(JSONObject clock, Calendar cal, int count) {
        int h = clock.optBoolean("vertical12h", false) ? (cal.get(Calendar.HOUR) == 0 ? 12 : cal.get(Calendar.HOUR))
                : cal.get(Calendar.HOUR_OF_DAY);
        int hourDigits = clock.optBoolean("leadingZero", true) || h >= 10 ? 2 : 1;
        return clock.optBoolean("columnUp", false) ? count - hourDigits : hourDigits;
    }

    private static float columnWidth(TextLayer layer) {
        float w = 0;
        for (char ch = '0'; ch <= '9'; ch++) w = Math.max(w, layer.paint.measureText(String.valueOf(ch)));
        return w;
    }

    private static void drawColumn(Canvas c, TextLayer layer, JSONObject clock, Calendar cal, float x, float y, float size) {
        String[] d = columnDigits(clock, cal);
        int split = columnSplit(clock, cal, d.length);
        float lh = size * COLUMN_LINE;
        float w = columnWidth(layer);
        float left = layer.left(x, w);
        RectF box = new RectF(left, y - size * 0.75f, left + w, y + (d.length - 1) * lh + size * COLUMN_GAP + size * 0.05f);
        layer.draw(c, box, p -> {
            for (int i = 0; i < d.length; i++) {
                float base = y + i * lh + (i >= split ? size * COLUMN_GAP : 0);
                float cw = p.measureText(d[i]);
                c.drawText(d[i], left + (w - cw) / 2, base, p); // jede Ziffer mittig in der Spalte
            }
        });
    }

    // ---------- 5. Words (Wort-Uhr) ----------

    private static void drawWords(Canvas c, TextLayer layer, JSONObject clock,
                                  Calendar cal, float x, float y, float size) {
        boolean colloquial = clock.optBoolean("colloquial", false);
        boolean upper = clock.optBoolean("uppercase", false);

        String[] lines = words(cal, colloquial, upper);
        float lineSpacing = size * 0.95f;

        float maxW = 0;
        for (String line : lines) maxW = Math.max(maxW, layer.paint.measureText(line));
        float left = layer.left(x, maxW);

        RectF box = new RectF(left, y - size * 0.75f, left + maxW, y + (lines.length - 1) * lineSpacing + size * 0.05f);

        layer.draw(c, box, p -> {
            for (int i = 0; i < lines.length; i++) {
                float lineW = p.measureText(lines[i]);
                float lx = layer.left(x, lineW);
                c.drawText(lines[i], lx, y + i * lineSpacing, p);
            }
        });
    }

    static String[] words(Calendar cal, boolean colloquial, boolean uppercase) {
        int hour = cal.get(Calendar.HOUR);
        int minute = cal.get(Calendar.MINUTE);

        String l1;
        String l2;

        if (colloquial) {
            int round5 = (int) Math.round(minute / 5.0) * 5;
            int nextHour = (hour + 1) % 12;
            switch (round5) {
                case 0:
                case 60:
                    l1 = "Punkt";
                    l2 = HOURS[round5 == 60 ? nextHour : hour];
                    break;
                case 5:
                    l1 = "Fünf nach";
                    l2 = HOURS[hour];
                    break;
                case 10:
                    l1 = "Zehn nach";
                    l2 = HOURS[hour];
                    break;
                case 15:
                    l1 = "Viertel nach";
                    l2 = HOURS[hour];
                    break;
                case 20:
                    l1 = "Zehn vor";
                    l2 = "Halb " + HOURS[nextHour];
                    break;
                case 25:
                    l1 = "Fünf vor";
                    l2 = "Halb " + HOURS[nextHour];
                    break;
                case 30:
                    l1 = "Halb";
                    l2 = HOURS[nextHour];
                    break;
                case 35:
                    l1 = "Fünf nach";
                    l2 = "Halb " + HOURS[nextHour];
                    break;
                case 40:
                    l1 = "Zehn nach";
                    l2 = "Halb " + HOURS[nextHour];
                    break;
                case 45:
                    l1 = "Viertel vor";
                    l2 = HOURS[nextHour];
                    break;
                case 50:
                    l1 = "Zehn vor";
                    l2 = HOURS[nextHour];
                    break;
                default: // 55
                    l1 = "Fünf vor";
                    l2 = HOURS[nextHour];
                    break;
            }
        } else {
            l1 = HOURS[hour] + " Uhr";
            l2 = minuteToWord(minute);
        }

        if (uppercase) {
            l1 = l1.toUpperCase(Locale.GERMANY);
            l2 = l2.toUpperCase(Locale.GERMANY);
        }
        return new String[]{l1, l2};
    }

    private static String minuteToWord(int m) {
        if (m == 0) return "genau";
        if (m < 10) return ONES[m];
        if (m < 20) return TEENS[m - 10];
        int ten = m / 10;
        int one = m % 10;
        if (one == 0) return TENS[ten];
        return ONES[one] + "und" + TENS[ten];
    }

    public static void main(String[] args) {
        Calendar cal = Calendar.getInstance();
        for (int h = 0; h < 24; h += 3) {
            for (int m : new int[]{0, 5, 15, 23, 30, 42, 45, 55}) {
                cal.set(Calendar.HOUR_OF_DAY, h);
                cal.set(Calendar.MINUTE, m);
                String[] exact = words(cal, false, false);
                String[] col = words(cal, true, false);
                System.out.printf("%02d:%02d -> [%s %s] | [%s %s]%n",
                        h, m, exact[0], exact[1], col[0], col[1]);
            }
        }
        System.out.println("ClockRenderer words test OK!");
    }
}
