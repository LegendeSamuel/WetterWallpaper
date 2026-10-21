package de.seelig.wetterwallpaper;

import android.app.TimePickerDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.widget.NestedScrollView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Modi: Tag, Morgen- und Abenddämmerung, Nacht, Regen-Look und beliebig viele eigene Zeiträume. Löst für jede
 * Uhrzeit (und das Wetter) den aktiven Modus auf und baut das Blatt hinter der Modus-Pille: oben welcher Modus
 * bearbeitet wird, darunter das heutige Tagesband und die Regeln nach Vorrang (oben gewinnt).
 */
final class DayNight {
    static final int COLOR_DAY = 0xFFF0CF72, COLOR_DAWN = 0xFFF4A983, COLOR_DUSK = 0xFFE07F8A,
            COLOR_NIGHT = 0xFF8794E0, COLOR_RAIN = 0xFF6FA8D6;
    private static final int[] SLOT_COLORS = {0xFF6CC4A4, 0xFFC49BE8, 0xFF7CC7E8, 0xFFE8C27C, 0xFFE89BB8};

    private DayNight() {
    }

    // ---------- Auflösen ----------

    static String resolveMode(JSONObject dn, long time, double lat, double lon) {
        return resolveMode(dn, time, lat, lon, null);
    }

    /** Aktiver Modus ("day", "dawn", "dusk", "night", "rain" oder "slot_...") für Zeitpunkt, Ort und Wetter. */
    static String resolveMode(JSONObject dn, long time, double lat, double lon, Weather wx) {
        if (dn == null) dn = new JSONObject();
        // 1. Regen-Look: Wetter schlägt jede Uhrzeit
        if (dn.optBoolean("rainLook", false) && wx != null && (wx.rain > 0.15f || wx.thunder)) return "rain";

        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(time);
        int min = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE);

        // 2. Eigene Zeiträume (minutengenau)
        JSONArray intervals = dn.optJSONArray("intervals");
        if (intervals != null) {
            for (int i = 0; i < intervals.length(); i++) {
                JSONObject it = intervals.optJSONObject(i);
                if (it == null || !it.optBoolean("enabled", true)) continue;
                if (inRange(min, it.optInt("startMin", 0), it.optInt("endMin", 60))) {
                    String id = it.optString("id", "");
                    if (!id.isEmpty()) return id;
                }
            }
        }

        // 3. Dämmerung zu fester Uhrzeit
        if (dn.optBoolean("twilightTime", false)) {
            if (dn.optBoolean("twilightMorning", true)
                    && inRange(min, dn.optInt("twilightMorningStart", 6 * 60), dn.optInt("twilightMorningEnd", 7 * 60 + 30))) {
                return "dawn";
            }
            if (dn.optBoolean("twilightEvening", true)
                    && inRange(min, dn.optInt("twilightStart", 19 * 60 + 30), dn.optInt("twilightEnd", 21 * 60 + 30))) {
                return "dusk";
            }
        }

        // 4. Nacht zu fester Uhrzeit
        boolean legacyTime = "time".equals(dn.optString("mode", ""));
        if (dn.optBoolean("nightTime", legacyTime)) {
            int nStart = dn.has("nightStart") ? dn.optInt("nightStart", 22 * 60) : dn.optInt("endHour", 22) * 60;
            int nEnd = dn.has("nightEnd") ? dn.optInt("nightEnd", 7 * 60) : dn.optInt("startHour", 7) * 60;
            if (inRange(min, nStart, nEnd)) return "night";
        }

        // 5. Dämmerung nach Sonnenstand (Sonne zwischen −7° und +4°)
        SkyPosition sun = SkyPosition.sun(time, lat, lon);
        if (dn.optBoolean("twilightSun", false) && sun.elevation >= -7.0 && sun.elevation <= 4.0) {
            boolean morning = min < 12 * 60;
            if (morning && dn.optBoolean("dawnSun", true)) return "dawn";
            if (!morning && dn.optBoolean("duskSun", true)) return "dusk";
        }

        // 6. Tag & Nacht nach Sonnenstand
        if (dn.optBoolean("sun", !legacyTime) && Mood.forElevation(sun.elevation).night > 0.5f) return "night";
        return "day";
    }

    /**
     * Weicher Übergang: Liegt der letzte Moduswechsel weniger als transitionMin Minuten zurück, kommt der vorige
     * Modus in prev[0] und der Fortschritt 0..1 zurück; sonst 1 (kein Übergang).
     */
    static float transition(JSONObject dn, long time, double lat, double lon, Weather wx, String[] prev) {
        int minutes = dn != null ? dn.optInt("transitionMin", 0) : 0;
        if (minutes <= 0) return 1f;
        String cur = resolveMode(dn, time, lat, lon, wx);
        long span = minutes * 60_000L;
        if (cur.equals(resolveMode(dn, time - span, lat, lon, wx))) return 1f;
        for (long back = 60_000L; back <= span; back += 60_000L) {
            String m = resolveMode(dn, time - back, lat, lon, wx);
            if (!m.equals(cur)) {
                prev[0] = m;
                return Math.max(0f, Math.min(1f, (back - 60_000L) / (float) span));
            }
        }
        return 1f;
    }

    /** Zeitpunkt des nächsten Moduswechsels nach Uhrzeit (ohne Wetter), minutengenau; -1 = keiner in maxMinutes. */
    static long nextChange(JSONObject dn, long from, double lat, double lon, int maxMinutes) {
        String now = resolveMode(dn, from, lat, lon, null);
        long start = from - from % 60_000L;
        for (int i = 1; i <= maxMinutes; i++) {
            long t = start + i * 60_000L;
            if (!now.equals(resolveMode(dn, t, lat, lon, null))) return t;
        }
        return -1;
    }

    /** Prüft, ob Minute m (0..1439) im Intervall [start, end) liegt (inkl. Mitternachts-Überlauf). */
    static boolean inRange(int m, int start, int end) {
        start = ((start % 1440) + 1440) % 1440;
        end = ((end % 1440) + 1440) % 1440;
        if (start == end) return m == start; // exakt 1 Minute
        if (start < end) return m >= start && m < end;
        return m >= start || m < end;
    }

    /** Mittelpunkt eines Intervalls in Minuten (0..1439) für die Live-Vorschau. */
    static int midMinute(int start, int end) {
        start = ((start % 1440) + 1440) % 1440;
        end = ((end % 1440) + 1440) % 1440;
        int dur = end > start ? (end - start) : (1440 - start + end);
        return (start + dur / 2) % 1440;
    }

    static String formatMin(int min) {
        min = ((min % 1440) + 1440) % 1440;
        return String.format(Locale.GERMANY, "%02d:%02d", min / 60, min % 60);
    }

    // ---------- Namen, Symbole, Farben ----------

    /** Alle Modi zum Bearbeiten: {id, Name}. Regen nur, wenn der Regen-Look an ist. */
    static List<String[]> modes(Settings settings) {
        List<String[]> out = new ArrayList<>();
        out.add(new String[]{"day", "Tag"});
        out.add(new String[]{"dawn", "Morgendämmerung"});
        out.add(new String[]{"dusk", "Abenddämmerung"});
        out.add(new String[]{"night", "Nacht"});
        if (settings.b("dayNight", "rainLook", false) || "rain".equals(settings.activeMode())) out.add(new String[]{"rain", "Regen"});
        JSONArray arr = settings.sec("dayNight").optJSONArray("intervals");
        for (int i = 0; arr != null && i < arr.length(); i++) {
            JSONObject it = arr.optJSONObject(i);
            if (it == null) continue;
            String name = it.optString("name", "").trim();
            out.add(new String[]{it.optString("id"), name.isEmpty() ? "Zeitraum " + (i + 1) : name});
        }
        return out;
    }

    static String name(Settings settings, String mode) {
        for (String[] m : modes(settings)) if (m[0].equals(mode)) return m[1];
        return "dusk".equals(mode) || "twilight".equals(mode) ? "Abenddämmerung" : "Tag";
    }

    /** Kurzname für die Modus-Pille. */
    static String buttonLabel(Settings settings) {
        String m = settings.activeMode();
        String label;
        if ("dawn".equals(m)) label = "Morgen";
        else if ("dusk".equals(m) || "twilight".equals(m)) label = "Abend";
        else label = name(settings, m);
        return "home".equals(settings.screen()) ? label + " · Start" : label;
    }

    /** Ausgeschriebener Name (für „gilt für …“). */
    static String fullLabel(Settings settings) {
        String n = name(settings, settings.activeMode());
        return "home".equals(settings.screen()) ? n + ", Startbildschirm" : n;
    }

    static int iconFor(String mode) {
        switch (mode) {
            case "day": return R.drawable.ic_sun;
            case "dawn": return R.drawable.ic_sunrise;
            case "dusk": case "twilight": return R.drawable.ic_sunset;
            case "night": return R.drawable.ic_moon;
            case "rain": return R.drawable.ic_rain;
            default: return R.drawable.ic_clock;
        }
    }

    static int colorFor(Settings settings, String mode) {
        switch (mode) {
            case "day": return COLOR_DAY;
            case "dawn": return COLOR_DAWN;
            case "dusk": case "twilight": return COLOR_DUSK;
            case "night": return COLOR_NIGHT;
            case "rain": return COLOR_RAIN;
            default:
                List<String[]> all = modes(settings);
                int idx = 0;
                for (int i = 0; i < all.size(); i++) if (all.get(i)[0].equals(mode)) idx = i;
                return SLOT_COLORS[idx % SLOT_COLORS.length];
        }
    }

    /** Schaltet die Vorschau passend zum gewählten Modus um (Tageszeit und beim Regen-Look das Wetter). */
    static void applyPreviewForMode(MainActivity a, Settings settings, String mode) {
        PreviewView pv = a.preview();
        if (pv == null) return;
        pv.setPreviewMode(mode);
        pv.setModeWeather("rain".equals(mode) ? "rain" : null);
        switch (mode) {
            case "day": case "rain": pv.simulateTime(PreviewView.TimeOfDay.NOON); return;
            case "dawn": pv.simulateTime(PreviewView.TimeOfDay.MORNING); return;
            case "dusk": case "twilight": pv.simulateTime(PreviewView.TimeOfDay.EVENING); return;
            case "night": pv.simulateTime(PreviewView.TimeOfDay.NIGHT); return;
            default:
                JSONArray arr = settings.sec("dayNight").optJSONArray("intervals");
                for (int i = 0; arr != null && i < arr.length(); i++) {
                    JSONObject it = arr.optJSONObject(i);
                    if (it != null && mode.equals(it.optString("id"))) {
                        pv.simulateMinutes(midMinute(it.optInt("startMin", 720), it.optInt("endMin", 780)));
                        return;
                    }
                }
                pv.simulateTime(PreviewView.TimeOfDay.NOW);
        }
    }

    // ---------- Heute: wann gilt welcher Modus ----------

    /** Modus für jede 5-Minuten-Stufe des heutigen Tages (288 Werte). */
    static String[] today(Settings settings, double lat, double lon) {
        String[] out = new String[288];
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        long start = c.getTimeInMillis();
        JSONObject dn = settings.sec("dayNight");
        for (int i = 0; i < out.length; i++) out[i] = resolveMode(dn, start + i * 5 * 60_000L + 150_000L, lat, lon, null);
        return out;
    }

    /** "18:35–07:50" bzw. mehrere Zeiträume; über Mitternacht zusammengefasst. */
    static String ranges(String[] day, String mode) {
        List<int[]> r = new ArrayList<>();
        for (int i = 0; i < day.length; i++) {
            if (!mode.equals(day[i])) continue;
            if (!r.isEmpty() && r.get(r.size() - 1)[1] == i) r.get(r.size() - 1)[1] = i + 1;
            else r.add(new int[]{i, i + 1});
        }
        if (r.isEmpty()) return null;
        if (r.size() > 1 && r.get(0)[0] == 0 && r.get(r.size() - 1)[1] == day.length) {
            int[] last = r.remove(r.size() - 1);
            r.set(0, new int[]{last[0], r.get(0)[1] + day.length});
        }
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < Math.min(2, r.size()); i++) {
            if (i > 0) b.append(" · ");
            b.append(formatMin(r.get(i)[0] * 5)).append('–').append(formatMin(r.get(i)[1] * 5));
        }
        if (r.size() > 2) b.append(" …");
        return b.toString();
    }

    // ---------- Blatt ----------

    static void showDialog(MainActivity a, Settings settings, Runnable onModeChanged) {
        BottomSheetDialog d = new BottomSheetDialog(a);
        a.glassify(d);
        NestedScrollView scroll = new NestedScrollView(a);
        LinearLayout root = SheetKit.column(a);
        int pad = a.dp(20);
        root.setPadding(pad, a.dp(8), pad, a.dp(32));
        scroll.addView(root);
        Runnable[] rebuild = new Runnable[1];
        rebuild[0] = () -> {
            int y = scroll.getScrollY();
            root.removeAllViews();
            build(a, settings, root, onModeChanged, rebuild[0], d);
            scroll.post(() -> scroll.scrollTo(0, y));
        };
        rebuild[0].run();
        d.setContentView(scroll);
        d.show();
    }

    private static void build(MainActivity a, Settings settings, LinearLayout root, Runnable onModeChanged,
                              Runnable rebuild, BottomSheetDialog dialog) {
        root.addView(SheetKit.heading(a, "Modus & Zeiten"));
        String[] day = today(settings, a.preview().lat(), a.preview().lon());

        if (settings.splitScreens()) {
            root.addView(SheetKit.label(a, "Bildschirm"));
            root.addView(SheetKit.choice(a, new String[][]{{"lock", "Sperrbildschirm"}, {"home", "Startbildschirm"}},
                    settings.screen(), s -> {
                        settings.setScreen(s);
                        onModeChanged.run();
                        rebuild.run();
                    }));
        }

        // 1. Welcher Modus wird bearbeitet
        root.addView(SheetKit.label(a, "Welchen Modus bearbeitest du?"));
        for (String[] m : modes(settings)) root.addView(modeRow(a, settings, m, day, onModeChanged, rebuild));
        String cur = settings.activeMode();
        if (!"day".equals(cur)) {
            MaterialButton reset = SheetKit.button(a, "Alles wie am Tag", 0, false, v -> new MaterialAlertDialogBuilder(a)
                    .setTitle("Alle Abweichungen verwerfen?")
                    .setMessage(name(settings, cur) + " übernimmt dann wieder alles vom Tag.")
                    .setNegativeButton("Abbrechen", null)
                    .setPositiveButton("Verwerfen", (x, w) -> {
                        settings.copyFromDay(cur);
                        onModeChanged.run();
                        a.changed();
                        rebuild.run();
                    })
                    .show());
            root.addView(reset);
        }

        // 2. Ergebnis heute
        root.addView(SheetKit.label(a, "Heute"));
        root.addView(new DayBand(a, settings, day, mode -> {
            select(a, settings, mode, onModeChanged);
            rebuild.run();
        }), new LinearLayout.LayoutParams(-1, a.dp(56)));

        // 3. Regeln nach Vorrang
        LinearLayout head = new LinearLayout(a);
        head.setGravity(Gravity.BOTTOM);
        TextView rulesLabel = SheetKit.label(a, "Wann gilt welcher Modus?");
        head.addView(rulesLabel, new LinearLayout.LayoutParams(0, -2, 1));
        TextView prio = SheetKit.help(a, "oben gewinnt");
        prio.setPadding(0, 0, 0, a.dp(6));
        head.addView(prio);
        root.addView(head);
        int n = 1;
        root.addView(rule(a, n++, SheetKit.switchRow(a, "Eigener Look bei Regen",
                "Regnet oder gewittert es, gilt der Modus „Regen“, egal zu welcher Uhrzeit.",
                settings.b("dayNight", "rainLook", false), on -> {
                    settings.put("dayNight", "rainLook", on);
                    if (on) settings.ensureMode("rain");
                    a.changed();
                    rebuild.run();
                })));

        LinearLayout slots = SheetKit.column(a);
        LinearLayout slotHead = new LinearLayout(a);
        slotHead.setGravity(Gravity.CENTER_VERTICAL);
        slotHead.setMinimumHeight(a.dp(48));
        slotHead.addView(SheetKit.text(a, "Eigene Zeiträume", R.style.TextAppearance_Wetter_BodyLarge, 0),
                new LinearLayout.LayoutParams(0, -2, 1));
        slotHead.addView(SheetKit.button(a, "Neu", R.drawable.ic_add, false, v -> {
            addInterval(a, settings, onModeChanged);
            rebuild.run();
        }));
        slots.addView(slotHead);
        JSONArray intervals = intervals(settings);
        if (intervals.length() == 0) slots.addView(SheetKit.help(a, "Noch keiner. Zum Beispiel eine Mittagspause oder ein ruhiger Abend."));
        root.addView(rule(a, n++, slots));

        boolean twTime = settings.b("dayNight", "twilightTime", false);
        LinearLayout twTimeBox = SheetKit.column(a);
        twTimeBox.addView(SheetKit.switchRow(a, "Dämmerung zu fester Uhrzeit", "Eigene Uhrzeiten für Morgen- und Abenddämmerung.",
                twTime, on -> {
                    settings.put("dayNight", "twilightTime", on);
                    a.changed();
                    rebuild.run();
                }));
        if (twTime) {
            twTimeBox.addView(subTime(a, settings, "Morgen", "twilightMorning", "twilightMorningStart", 6 * 60,
                    "twilightMorningEnd", 7 * 60 + 30, rebuild));
            twTimeBox.addView(subTime(a, settings, "Abend", "twilightEvening", "twilightStart", 19 * 60 + 30,
                    "twilightEnd", 21 * 60 + 30, rebuild));
        }
        root.addView(rule(a, n++, twTimeBox));

        boolean legacyTime = "time".equals(settings.s("dayNight", "mode", ""));
        boolean nightTime = settings.b("dayNight", "nightTime", legacyTime);
        LinearLayout nightBox = SheetKit.column(a);
        nightBox.addView(SheetKit.switchRow(a, "Nacht zu fester Uhrzeit", "Feste Uhrzeiten für die Nacht, unabhängig vom Sonnenstand.",
                nightTime, on -> {
                    settings.put("dayNight", "nightTime", on);
                    a.changed();
                    rebuild.run();
                }));
        if (nightTime) {
            int s = settings.has("dayNight", "nightStart") ? (int) settings.d("dayNight", "nightStart", 22 * 60)
                    : (int) settings.d("dayNight", "endHour", 22) * 60;
            int e = settings.has("dayNight", "nightEnd") ? (int) settings.d("dayNight", "nightEnd", 7 * 60)
                    : (int) settings.d("dayNight", "startHour", 7) * 60;
            nightBox.addView(timeRangeRow(a, "von", s, e, (ns, ne) -> {
                settings.put("dayNight", "nightStart", ns);
                settings.put("dayNight", "nightEnd", ne);
                a.changed();
                rebuild.run();
            }));
        }
        root.addView(rule(a, n++, nightBox));

        boolean twSun = settings.b("dayNight", "twilightSun", false);
        LinearLayout twSunBox = SheetKit.column(a);
        twSunBox.addView(SheetKit.switchRow(a, "Dämmerung nach Sonnenstand",
                "Morgen- und Abenddämmerung, solange die Sonne zwischen 7° unter und 4° über dem Horizont steht.", twSun, on -> {
                    settings.put("dayNight", "twilightSun", on);
                    a.changed();
                    rebuild.run();
                }));
        if (twSun) {
            LinearLayout chips = new LinearLayout(a);
            chips.addView(SheetKit.switchRow(a, "Morgen", null, settings.b("dayNight", "dawnSun", true), on -> {
                settings.put("dayNight", "dawnSun", on);
                a.changed();
                rebuild.run();
            }), new LinearLayout.LayoutParams(0, -2, 1));
            View gap = new View(a);
            chips.addView(gap, new LinearLayout.LayoutParams(a.dp(16), 1));
            chips.addView(SheetKit.switchRow(a, "Abend", null, settings.b("dayNight", "duskSun", true), on -> {
                settings.put("dayNight", "duskSun", on);
                a.changed();
                rebuild.run();
            }), new LinearLayout.LayoutParams(0, -2, 1));
            twSunBox.addView(chips);
        }
        root.addView(rule(a, n++, twSunBox));

        root.addView(rule(a, n, SheetKit.switchRow(a, "Tag & Nacht nach Sonnenstand",
                "Nacht ab Sonnenuntergang, Tag ab Sonnenaufgang an deinem Ort.",
                settings.b("dayNight", "sun", !legacyTime), on -> {
                    settings.put("dayNight", "sun", on);
                    a.changed();
                    rebuild.run();
                })));

        // 4. Übergang
        root.addView(SheetKit.label(a, "Übergang"));
        int trans = (int) settings.d("dayNight", "transitionMin", 0);
        root.addView(SheetKit.slider(a, trans == 0 ? "Sofort wechseln" : "Weich überblenden über " + trans + " Minuten",
                0, 60, 5, trans, " min", false, v -> {
                    settings.put("dayNight", "transitionMin", Math.round(v));
                    a.changed();
                    rebuild.run();
                }, "sofort", "60 min"));
    }

    private static void select(MainActivity a, Settings settings, String mode, Runnable onModeChanged) {
        if (mode.equals(settings.activeMode())) return;
        // Dämmerung gewählt, aber keine Regel dafür an: Dämmerung nach Sonnenstand einschalten
        if (("dawn".equals(mode) || "dusk".equals(mode)) && !settings.b("dayNight", "twilightSun", false)
                && !settings.b("dayNight", "twilightTime", false)) {
            settings.put("dayNight", "twilightSun", true);
        }
        settings.setActiveMode(mode);
        applyPreviewForMode(a, settings, mode);
        onModeChanged.run();
        a.changed();
    }

    /** Karte eines Modus: Symbol, Name, heutige Zeiten; gewählt mit Bernstein-Rand. Zeiträume mit Bearbeiten. */
    private static View modeRow(MainActivity a, Settings settings, String[] m, String[] day,
                                Runnable onModeChanged, Runnable rebuild) {
        String id = m[0];
        boolean current = id.equals(settings.activeMode());
        MaterialCardView card = new MaterialCardView(a);
        card.setRadius(a.dp(14));
        card.setCardBackgroundColor(current ? 0x1FF2A007 : 0x0DFFFFFF);
        card.setStrokeColor(current ? 0xFFF2A007 : 0xFF383838);
        card.setStrokeWidth(a.dp(current ? 2 : 1));
        card.setContentDescription(m[1] + (current ? ", wird bearbeitet" : ", bearbeiten"));
        card.setOnClickListener(v -> {
            select(a, settings, id, onModeChanged);
            rebuild.run();
        });
        LinearLayout row = new LinearLayout(a);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(a.dp(14), a.dp(10), a.dp(6), a.dp(10));
        ImageView ic = new ImageView(a);
        ic.setImageResource(iconFor(id));
        ic.setImageTintList(ColorStateList.valueOf(colorFor(settings, id)));
        row.addView(ic, new LinearLayout.LayoutParams(a.dp(22), a.dp(22)));
        LinearLayout t = SheetKit.column(a);
        t.setPadding(a.dp(14), 0, a.dp(8), 0);
        t.addView(SheetKit.text(a, m[1], R.style.TextAppearance_Wetter_TitleSmall, 0));
        String r = "rain".equals(id) ? "Wenn es regnet oder gewittert" : ranges(day, id);
        t.addView(SheetKit.text(a, r != null ? "heute " + r : "heute nicht aktiv", R.style.TextAppearance_Wetter_BodySmall,
                com.google.android.material.R.attr.colorOnSurfaceVariant));
        row.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
        if (id.startsWith("slot")) {
            MaterialButton edit = new MaterialButton(a, null, com.google.android.material.R.attr.materialIconButtonStyle);
            edit.setIconResource(R.drawable.ic_more_vert);
            edit.setContentDescription(m[1] + " bearbeiten");
            edit.setOnClickListener(v -> editInterval(a, settings, id, onModeChanged, rebuild));
            row.addView(edit, new LinearLayout.LayoutParams(a.dp(44), a.dp(44)));
        }
        card.addView(row);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = a.dp(8);
        card.setLayoutParams(lp);
        return card;
    }

    /** Nummer (Vorrang) links neben einer Regel. */
    private static View rule(MainActivity a, int n, View body) {
        LinearLayout row = new LinearLayout(a);
        TextView num = SheetKit.text(a, String.valueOf(n), R.style.TextAppearance_Wetter_LabelMedium,
                com.google.android.material.R.attr.colorOnSurfaceVariant);
        num.setGravity(Gravity.CENTER);
        num.setBackgroundColor(0xFF2A2723);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(a.dp(22), a.dp(22));
        lp.topMargin = a.dp(13);
        lp.setMarginEnd(a.dp(12));
        row.addView(num, lp);
        row.addView(body, new LinearLayout.LayoutParams(0, -2, 1));
        View line = new View(a);
        line.setBackgroundColor(0xFF2E2E2E);
        LinearLayout box = SheetKit.column(a);
        box.addView(row);
        box.addView(line, new LinearLayout.LayoutParams(-1, a.dp(1)));
        return box;
    }

    private static View subTime(MainActivity a, Settings settings, String label, String onKey, String startKey, int startDef,
                                String endKey, int endDef, Runnable rebuild) {
        LinearLayout box = SheetKit.column(a);
        boolean on = settings.b("dayNight", onKey, true);
        box.addView(SheetKit.switchRow(a, label, null, on, v -> {
            settings.put("dayNight", onKey, v);
            a.changed();
            rebuild.run();
        }));
        if (on) {
            box.addView(timeRangeRow(a, "von", (int) settings.d("dayNight", startKey, startDef),
                    (int) settings.d("dayNight", endKey, endDef), (s, e) -> {
                        settings.put("dayNight", startKey, s);
                        settings.put("dayNight", endKey, e);
                        a.changed();
                        rebuild.run();
                    }));
        }
        return box;
    }

    // ---------- Eigene Zeiträume ----------

    private static void editInterval(MainActivity a, Settings settings, String id, Runnable onModeChanged, Runnable rebuild) {
        JSONArray intervals = intervals(settings);
        int idx = -1;
        for (int i = 0; i < intervals.length(); i++) {
            JSONObject it = intervals.optJSONObject(i);
            if (it != null && id.equals(it.optString("id"))) idx = i;
        }
        if (idx < 0) return;
        JSONObject it = intervals.optJSONObject(idx);
        int index = idx;
        String name = it.optString("name", "Zeitraum");
        String[] items = {"Umbenennen", "Von " + formatMin(it.optInt("startMin", 480)) + " ändern",
                "Bis " + formatMin(it.optInt("endMin", 540)) + " ändern", "Löschen"};
        new MaterialAlertDialogBuilder(a)
                .setTitle(name)
                .setItems(items, (d, which) -> {
                    Runnable done = () -> {
                        settings.put("dayNight", "intervals", intervals);
                        if (id.equals(settings.activeMode())) applyPreviewForMode(a, settings, id);
                        onModeChanged.run();
                        a.changed();
                        rebuild.run();
                    };
                    if (which == 0) {
                        promptRename(a, name, newName -> {
                            putQuiet(it, "name", newName);
                            done.run();
                        });
                    } else if (which == 1) {
                        pickTime(a, "Beginn", it.optInt("startMin", 480), mins -> {
                            putQuiet(it, "startMin", mins);
                            done.run();
                        });
                    } else if (which == 2) {
                        pickTime(a, "Ende", it.optInt("endMin", 540), mins -> {
                            putQuiet(it, "endMin", mins);
                            done.run();
                        });
                    } else {
                        intervals.remove(index);
                        settings.removeMode(id);
                        applyPreviewForMode(a, settings, settings.activeMode());
                        done.run();
                    }
                })
                .show();
    }

    private static void addInterval(MainActivity a, Settings settings, Runnable onModeChanged) {
        JSONArray intervals = intervals(settings);
        int start = 12 * 60;
        if (intervals.length() > 0) {
            JSONObject last = intervals.optJSONObject(intervals.length() - 1);
            if (last != null) start = last.optInt("endMin", start) % 1440;
        }
        String id = "slot_" + System.currentTimeMillis();
        JSONObject it = new JSONObject();
        putQuiet(it, "id", id);
        putQuiet(it, "name", "Zeitraum " + (intervals.length() + 1));
        putQuiet(it, "startMin", start);
        putQuiet(it, "endMin", (start + 60) % 1440);
        putQuiet(it, "enabled", true);
        intervals.put(it);
        settings.put("dayNight", "intervals", intervals);
        settings.setActiveMode(id);
        applyPreviewForMode(a, settings, id);
        onModeChanged.run();
        a.changed();
    }

    private static JSONArray intervals(Settings settings) {
        JSONArray arr = settings.sec("dayNight").optJSONArray("intervals");
        try {
            return arr == null ? new JSONArray() : new JSONArray(arr.toString());
        } catch (JSONException e) {
            return new JSONArray();
        }
    }

    private static void putQuiet(JSONObject o, String k, Object v) {
        try {
            o.put(k, v);
        } catch (JSONException e) {
            throw new IllegalArgumentException(k, e);
        }
    }

    private interface RangeCallback {
        void onRangeChanged(int startMin, int endMin);
    }

    private static View timeRangeRow(MainActivity a, String label, int startMin, int endMin, RangeCallback cb) {
        LinearLayout row = new LinearLayout(a);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, 0, 0, a.dp(6));
        TextView lbl = SheetKit.text(a, label, R.style.TextAppearance_Wetter_BodyMedium, 0);
        row.addView(lbl);
        row.addView(timeButton(a, formatMin(startMin), () -> pickTime(a, "Beginn", startMin, p -> cb.onRangeChanged(p, endMin))));
        row.addView(SheetKit.text(a, " bis ", R.style.TextAppearance_Wetter_BodyMedium, 0));
        row.addView(timeButton(a, formatMin(endMin), () -> pickTime(a, "Ende", endMin, p -> cb.onRangeChanged(startMin, p))));
        return row;
    }

    private static MaterialButton timeButton(MainActivity a, String text, Runnable onClick) {
        MaterialButton b = new MaterialButton(a, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        b.setText(text);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setPadding(a.dp(12), 0, a.dp(12), 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMarginStart(a.dp(8));
        b.setLayoutParams(lp);
        b.setOnClickListener(v -> onClick.run());
        return b;
    }

    private interface IntConsumer {
        void accept(int minutes);
    }

    private static void pickTime(MainActivity a, String title, int currentMin, IntConsumer done) {
        int h = ((currentMin / 60) % 24 + 24) % 24;
        int m = ((currentMin % 60) + 60) % 60;
        TimePickerDialog dlg = new TimePickerDialog(a, (view, hourOfDay, minute) -> done.accept(hourOfDay * 60 + minute), h, m, true);
        dlg.setTitle(title);
        dlg.show();
    }

    private interface StringConsumer {
        void accept(String s);
    }

    private static void promptRename(MainActivity a, String current, StringConsumer done) {
        EditText input = new EditText(a);
        input.setSingleLine(true);
        input.setText(current);
        input.setSelectAllOnFocus(true);
        FrameLayout box = new FrameLayout(a);
        int pad = a.dp(24);
        box.setPadding(pad, a.dp(8), pad, 0);
        box.addView(input);
        new MaterialAlertDialogBuilder(a)
                .setTitle("Zeitraum umbenennen")
                .setView(box)
                .setNegativeButton("Abbrechen", null)
                .setPositiveButton("Speichern", (d, w) -> {
                    String s = input.getText().toString().trim();
                    if (!s.isEmpty()) done.accept(s);
                })
                .show();
    }

    /** Tagesband 0–24 Uhr: Farbe je Modus, weißer Strich = jetzt. Antippen wählt den Modus dieser Uhrzeit. */
    static final class DayBand extends View {
        interface OnPick {
            void pick(String mode);
        }

        private final String[] day;
        private final int[] colors;
        private final String current;
        private final OnPick onPick;
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();

        DayBand(Context ctx, Settings settings, String[] day, OnPick onPick) {
            super(ctx);
            this.day = day;
            this.onPick = onPick;
            current = settings.activeMode();
            colors = new int[day.length];
            for (int i = 0; i < day.length; i++) colors[i] = colorFor(settings, day[i]);
            text.setColor(0xFFAAAAAA);
            text.setTextSize(11 * getResources().getDisplayMetrics().density);
            text.setTextAlign(Paint.Align.CENTER);
            setContentDescription("Tagesband: welcher Modus heute wann gilt. Antippen wählt den Modus dieser Uhrzeit.");
        }

        @Override
        protected void onDraw(Canvas c) {
            float d = getResources().getDisplayMetrics().density;
            float w = getWidth();
            float bandH = 26 * d;
            float step = w / day.length;
            for (int i = 0; i < day.length; i++) {
                fill.setColor(colors[i]);
                fill.setAlpha(day[i].equals(current) ? 255 : 150);
                c.drawRect(i * step, 0, (i + 1) * step + 0.5f, bandH, fill);
            }
            // aktueller Modus mit Rahmen
            fill.setStyle(Paint.Style.STROKE);
            fill.setStrokeWidth(2 * d);
            fill.setColor(0xFFFFFFFF);
            for (int i = 0; i < day.length; i++) {
                if (!day[i].equals(current)) continue;
                int j = i;
                while (j < day.length && day[j].equals(current)) j++;
                r.set(i * step + d, d, j * step - d, bandH - d);
                c.drawRoundRect(r, 3 * d, 3 * d, fill);
                i = j;
            }
            fill.setStyle(Paint.Style.FILL);
            Calendar cal = Calendar.getInstance();
            float now = (cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)) / 1440f * w;
            fill.setColor(0xFFFFFFFF);
            c.drawRect(now - d, -2 * d, now + d, bandH + 2 * d, fill);
            for (int h = 0; h <= 24; h += 6) {
                float x = Math.max(8 * d, Math.min(w - 8 * d, h / 24f * w));
                c.drawText(String.valueOf(h), x, bandH + 18 * d, text);
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if (e.getAction() == MotionEvent.ACTION_UP) {
                int i = (int) Math.max(0, Math.min(day.length - 1, e.getX() / getWidth() * day.length));
                onPick.pick(day[i]);
                performClick();
            }
            return true;
        }

        @Override
        public boolean performClick() {
            return super.performClick();
        }
    }

    // Selbsttest für Zeitbereiche, Mittelpunkte und die Zusammenfassung „heute“
    public static void main(String[] args) {
        check(inRange(8 * 60 + 30, 8 * 60, 9 * 60), "im Bereich");
        check(!inRange(9 * 60, 8 * 60, 9 * 60), "Ende ausgeschlossen");
        check(inRange(23 * 60 + 15, 22 * 60, 7 * 60) && inRange(3 * 60, 22 * 60, 7 * 60), "über Mitternacht");
        check(!inRange(12 * 60, 22 * 60, 7 * 60), "mittags keine Nacht");
        check(formatMin(9 * 60 + 5).equals("09:05"), "Format");
        String[] day = new String[288];
        for (int i = 0; i < day.length; i++) day[i] = i < 94 || i >= 223 ? "night" : "day";
        check("18:35–07:50".equals(ranges(day, "night")), "Nacht über Mitternacht: " + ranges(day, "night"));
        check("07:50–18:35".equals(ranges(day, "day")), "Tag: " + ranges(day, "day"));
        check(ranges(day, "dawn") == null, "nicht aktiv");
        System.out.println("DayNight ok");
    }

    private static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError(what);
    }
}
