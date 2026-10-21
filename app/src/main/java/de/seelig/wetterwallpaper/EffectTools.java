package de.seelig.wetterwallpaper;

import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Werkzeuge Füllung, Kontur, Effekt und Ausblenden für Uhr oder Widget. Jedes Werkzeug ist eine Gruppe
 * (Auswahl oder Schalter) mit den Reglern des gewählten Effekts darunter. Gezeichnet wird in TextLayer.draw.
 */
final class EffectTools {
    private static final String[][] FILLS = {{"color", "Farbe"}, {"gradient", "Verlauf"}, {"glass", "Glas"},
            {"photo", "Bild"}, {"hollow", "Hohl"}};
    /** Füllarten der Kontur (Hohl ergibt dort keinen Sinn). */
    private static final String[][] OUTLINE_FILLS = {{"color", "Farbe"}, {"gradient", "Verlauf"}, {"glass", "Glas"},
            {"photo", "Bild"}};
    private static final String[][] EFFECTS = {{"none", "Aus"}, {"shadow", "Schatten"}, {"glow", "Leuchten"},
            {"neon", "Neon"}, {"long", "Langer Schatten"}, {"retro", "Retro-Versatz"}};

    private final MainActivity activity;
    private final Settings settings;
    private final String sec;
    private final boolean widget;
    /** Farbzeilen samt ihrer Gruppe, damit sie beim Neuaufbau ersetzt werden und Fotofarben bekommen. */
    private final List<Object[]> colorRows = new ArrayList<>();
    private int[] photo = new int[0];
    /** Farbzeile der Schrift (ColorRow), erscheint unter Füllung → Farbe. */
    private final View colorGroup;
    final LinearLayout fill;
    final LinearLayout outline;
    final LinearLayout effect;
    final LinearLayout fade;

    EffectTools(MainActivity activity, Settings settings, String section, boolean widget, View colorGroup) {
        this.activity = activity;
        this.colorGroup = colorGroup;
        this.settings = settings;
        this.sec = section;
        this.widget = widget;
        fill = group();
        outline = group();
        effect = group();
        fade = group();
        choice(fill, "fill", settings.s(sec, "fill", "color"), FILLS, this::fillOptions);
        String style = settings.s(sec, "shadowStyle", settings.b(sec, "shadow", true) ? "shadow" : "none");
        choice(effect, "shadowStyle", style, EFFECTS, this::effectOptions);
        toggle(outline, "outline", "Kontur zeigen", o -> {
            slider(o, "Breite", "outlineWidth", 0, 100, "%");
            choice(o, "outlineFill", settings.s(sec, "outlineFill", "color"), OUTLINE_FILLS, this::outlineOptions);
        });
        toggle(fade, "fade", "Ausblenden", o -> {
            slider(o, "Position", "fadeStart", 0, 100, "%");
            slider(o, "Übergang", "fadeLength", 0, 100, "%");
            slider(o, "Richtung", "fadeAngle", 0, 360, "°");
            help(o, "Position: ab wo die Schrift verschwindet, in Richtung des Pfeils gezählt. Richtung 90° = nach unten, 270° = nach oben.");
        });
    }

    void setPhotoColors(int[] c) {
        photo = c;
        for (Object[] r : colorRows) ((ColorRow) r[1]).setPhotoColors(c);
    }

    private LinearLayout group() {
        LinearLayout g = new LinearLayout(activity);
        g.setOrientation(LinearLayout.VERTICAL);
        g.setVisibility(View.GONE);
        return g;
    }

    private void put(String key, Object value) {
        settings.put(sec, key, value);
        activity.changed();
    }

    /** Auswahl als Chips, darunter die Regler der gewählten Möglichkeit. */
    private void choice(LinearLayout group, String key, String current, String[][] options, java.util.function.BiConsumer<LinearLayout, String> build) {
        ChipGroup chips = new ChipGroup(activity);
        chips.setSingleSelection(true);
        chips.setSelectionRequired(true);
        for (String[] o : options) {
            Chip c = activity.filterChip(chips, o[1]);
            c.setTag(o[0]);
            if (o[0].equals(current)) c.setChecked(true);
        }
        LinearLayout opts = new LinearLayout(activity);
        opts.setOrientation(LinearLayout.VERTICAL);
        group.addView(chips);
        group.addView(opts);
        chips.setOnCheckedStateChangeListener((g, ids) -> {
            Chip c = g.findViewById(g.getCheckedChipId());
            if (c == null) return;
            String v = (String) c.getTag();
            if ("shadowStyle".equals(key)) settings.put(sec, "shadow", !"none".equals(v)); // für ältere Stände
            put(key, v);
            rebuild(opts, o -> build.accept(o, v));
        });
        rebuild(opts, o -> build.accept(o, current));
    }

    /** Schalter, darunter immer die Regler (wirken, sobald eingeschaltet). */
    private void toggle(LinearLayout group, String key, String label, Consumer<LinearLayout> build) {
        MaterialSwitch s = new MaterialSwitch(activity);
        s.setText(label);
        s.setChecked(settings.b(sec, key, false));
        s.setOnCheckedChangeListener((b, on) -> put(key, on));
        LinearLayout opts = new LinearLayout(activity);
        opts.setOrientation(LinearLayout.VERTICAL);
        group.addView(s);
        group.addView(opts);
        rebuild(opts, build);
    }

    private void rebuild(LinearLayout opts, Consumer<LinearLayout> build) {
        opts.removeAllViews();
        colorRows.removeIf(r -> r[0] == opts);
        build.accept(opts);
    }

    /** Über Milchglas, Bild und Hohl kann zusätzlich ein Verlauf liegen. */
    private void gradientOverlay(LinearLayout o) {
        MaterialSwitch s = new MaterialSwitch(activity);
        s.setText("Verlauf darüberlegen");
        s.setChecked(settings.b(sec, "gradientOverlay", false));
        LinearLayout sub = new LinearLayout(activity);
        sub.setOrientation(LinearLayout.VERTICAL);
        Runnable show = () -> {
            sub.removeAllViews();
            if (!settings.b(sec, "gradientOverlay", false)) return;
            stops(sub, "gradientColors");
            slider(sub, "Richtung", "gradientAngle", 0, 360, "°");
            slider(sub, "Stärke", "gradientMix", 0, 100, "%");
        };
        s.setOnCheckedChangeListener((b, on) -> {
            put("gradientOverlay", on);
            show.run();
        });
        o.addView(s);
        o.addView(sub);
        show.run();
    }

    private void fillOptions(LinearLayout o, String fill) {
        switch (fill) {
            case "gradient":
                help(o, "Farbpunkte: antippen zum Ändern, lange drücken zum Entfernen, + für einen weiteren (bis 8).");
                stops(o, "gradientColors");
                slider(o, "Richtung", "gradientAngle", 0, 360, "°");
                break;
            case "glass":
                slider(o, "Milchig", "glassFrost", 0, 100, "%");
                help(o, "0 % = klares Glas, nach rechts wird es milchiger.");
                slider(o, "Kante", "glassEdge", 0, 100, "%");
                gradientOverlay(o);
                break;
            case "photo":
                slider(o, "Helligkeit", "photoBright", 0, 100, "%");
                gradientOverlay(o);
                break;
            case "hollow":
                slider(o, "Linienbreite", "hollowWidth", 0, 100, "%");
                gradientOverlay(o);
                break;
            default:
                if (colorGroup.getParent() != null) ((ViewGroup) colorGroup.getParent()).removeView(colorGroup);
                colorGroup.setVisibility(View.VISIBLE);
                o.addView(colorGroup);
        }
    }

    private void outlineOptions(LinearLayout o, String fill) {
        switch (fill) {
            case "gradient":
                stops(o, "outlineGradientColors");
                slider(o, "Richtung", "outlineGradientAngle", 0, 360, TextLayer.fx("gradientAngle"), "°");
                break;
            case "glass":
                slider(o, "Milchig", "outlineGlassFrost", 0, 100, TextLayer.fx("glassFrost"), "%");
                break;
            case "photo":
                slider(o, "Helligkeit", "outlinePhotoBright", 0, 100, TextLayer.fx("photoBright"), "%");
                break;
            default:
                color(o, "outlineColor", "Automatisch (Kontrast zur Schrift)");
        }
    }

    private void effectOptions(LinearLayout o, String style) {
        switch (style) {
            case "shadow": {
                int[] d = widget ? TextLayer.WIDGET_SHADOW : TextLayer.CLOCK_SHADOW;
                color(o, "shadowColor", "Automatisch (passend zur Schrift)");
                slider(o, "Weichheit", "shadowBlur", 0, 100, d[0], "%");
                slider(o, "Abstand", "shadowDistance", 0, 100, d[1], "%");
                slider(o, "Richtung", "shadowAngle", 0, 360, 90, "°");
                slider(o, "Stärke", "shadowStrength", 0, 100, d[2], "%");
                break;
            }
            case "glow":
                color(o, "glowColor", "In der Schriftfarbe");
                slider(o, "Größe", "glowSize", 0, 100, "%");
                slider(o, "Stärke", "glowStrength", 0, 100, "%");
                break;
            case "neon":
                color(o, "neonColor", "Standard (Eisblau)");
                slider(o, "Größe", "neonSize", 0, 100, "%");
                slider(o, "Stärke", "neonStrength", 0, 100, "%");
                help(o, "Bei Füllung „Farbe“ bekommt die Schrift einen hellen Kern in der Neonfarbe.");
                break;
            case "long":
                color(o, "longColor", "Automatisch (passend zur Schrift)");
                slider(o, "Länge", "longLength", 0, 100, "%");
                slider(o, "Richtung", "longAngle", 0, 360, "°");
                slider(o, "Stärke", "longStrength", 0, 100, "%");
                break;
            case "retro":
                color(o, "retroColor1", "Standard (Koralle)");
                color(o, "retroColor2", "Standard (Hellblau)");
                slider(o, "Abstand", "retroDistance", 0, 100, "%");
                slider(o, "Richtung", "retroAngle", 0, 360, "°");
                break;
            default:
                break;
        }
    }

    /** Farbpunkte des Verlaufs als Kreise; der erste ist anfangs die Schriftfarbe ("text"). */
    private void stops(LinearLayout o, String key) {
        java.util.List<String> list = new java.util.ArrayList<>();
        org.json.JSONArray a = settings.array(sec, key);
        if (a != null && a.length() >= 2) for (int i = 0; i < a.length(); i++) list.add(a.optString(i, "text"));
        else {
            list.add("text");
            list.add(settings.s(sec, "gradientColor", "#BFD8FF").equals("auto") ? "#BFD8FF" : settings.s(sec, "gradientColor", "#BFD8FF"));
        }
        HorizontalScrollView scroll = new HorizontalScrollView(activity);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, activity.dp(8), 0, activity.dp(4));
        scroll.addView(row);
        o.addView(scroll);
        Runnable[] redraw = new Runnable[1];
        redraw[0] = () -> {
            row.removeAllViews();
            int size = activity.dp(34);
            for (int i = 0; i < list.size(); i++) {
                final int idx = i;
                String v = list.get(i);
                int col = "text".equals(v) ? 0xFFEEF2FA : TextLayer.parseColor(v, 0xFFFFFFFF);
                View dot = new View(activity);
                android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
                d.setShape(android.graphics.drawable.GradientDrawable.OVAL);
                d.setColor(col);
                d.setStroke(activity.dp("text".equals(v) ? 3 : 1), 0x80FFFFFF);
                dot.setBackground(d);
                dot.setContentDescription("Farbpunkt " + (i + 1) + ("text".equals(v) ? ", Schriftfarbe" : ""));
                dot.setOnClickListener(x -> ColorPicker.show(activity, col, c -> {
                    list.set(idx, String.format(java.util.Locale.ROOT, "#%06X", c & 0xFFFFFF));
                    saveStops(list, key);
                    redraw[0].run();
                }));
                dot.setOnLongClickListener(x -> {
                    if (list.size() > 2) {
                        list.remove(idx);
                        saveStops(list, key);
                        redraw[0].run();
                    }
                    return true;
                });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
                lp.setMarginEnd(activity.dp(10));
                row.addView(dot, lp);
            }
            if (list.size() < 8) {
                TextView plus = new TextView(activity);
                plus.setText("+");
                plus.setTextSize(20);
                plus.setGravity(Gravity.CENTER);
                plus.setContentDescription("Farbpunkt hinzufügen");
                android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
                d.setShape(android.graphics.drawable.GradientDrawable.OVAL);
                d.setStroke(activity.dp(1), 0x80FFFFFF);
                plus.setBackground(d);
                plus.setOnClickListener(x -> {
                    String last = list.get(list.size() - 1);
                    list.add("text".equals(last) ? "#FFFFFF" : last);
                    saveStops(list, key);
                    redraw[0].run();
                });
                row.addView(plus, new LinearLayout.LayoutParams(size, size));
            }
        };
        redraw[0].run();
    }

    private void saveStops(java.util.List<String> list, String key) {
        put(key, new org.json.JSONArray(list));
    }

    private void help(LinearLayout o, String text) {
        TextView t = new TextView(activity);
        t.setText(text);
        t.setTextAppearance(R.style.TextAppearance_Wetter_BodySmall);
        t.setPadding(0, activity.dp(6), 0, activity.dp(2));
        o.addView(t);
    }

    private void color(LinearLayout o, String key, String autoLabel) {
        HorizontalScrollView scroll = new HorizontalScrollView(activity);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, activity.dp(8), 0, activity.dp(4));
        scroll.addView(row);
        o.addView(scroll);
        ColorRow colors = new ColorRow(activity, settings, row, sec, key, autoLabel);
        colors.setPhotoColors(photo);
        colorRows.add(new Object[]{o, colors});
    }

    private void slider(LinearLayout o, String label, String key, int from, int to, String unit) {
        slider(o, label, key, from, to, TextLayer.fx(key), unit);
    }

    /** Beschrifteter Regler; der Wert erscheint beim Ziehen. */
    private void slider(LinearLayout o, String label, String key, int from, int to, int def, String unit) {
        LinearLayout line = new LinearLayout(activity);
        line.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = new TextView(activity);
        name.setText(label);
        name.setTextAppearance(R.style.TextAppearance_Wetter_LabelLarge);
        name.setMinWidth(activity.dp(84));
        Slider s = new Slider(activity);
        s.setValueFrom(from);
        s.setValueTo(to);
        s.setStepSize(1);
        s.setTickVisible(false);
        s.setContentDescription(label);
        s.setValue(Math.max(from, Math.min(to, Math.round(settings.d(sec, key, def)))));
        s.setLabelFormatter(v -> Math.round(v) + " " + unit);
        s.addOnChangeListener((sl, v, fromUser) -> {
            if (fromUser) put(key, Math.round(v));
        });
        line.addView(name);
        line.addView(s, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        o.addView(line);
    }
}
