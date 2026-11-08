package de.seelig.wetterwallpaper;

import android.graphics.Typeface;
import android.view.View;
import android.widget.TextView;

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Einstellungen für einen Text auf dem Wallpaper (Uhr oder Widget) aus layout/panel_text.xml. Je Werkzeug eine
 * Gruppe (showTool): Schrift + Stärke, Größe, Abstand, Ausrichtung, Farbe, Deckkraft, Schatten, Ebene und beim
 * Widget der Inhalt. Uhr und Widget bekommen so immer dieselben Möglichkeiten.
 */
final class TextPanel {
    /** Systemschrift-Familien; die Stärke kommt vom Regler. */
    private static final String[][] SYSTEM_FONTS = {{"Standard", "sans-serif"}, {"Schmal", "sans-serif-condensed"},
            {"Serif", "serif"}, {"Mono", "monospace"}};
    private static final String[][] DATE_FORMATS = {{"EEE, d. MMM"}, {"EEEE, d. MMMM"}, {"d. MMMM"}, {"EEE, d.M."},
            {"d.M."}, {"EEEE"}};
    /**
     * Regler 0..100 = Ebene: 0 hinter allem (nur Himmel dahinter), 100 vor allem.
     * Logarithmisch, weil Nähe-Werte im Fernen dicht liegen (Berg ~0,002, Haus ~0,02, Baum ~0,1, Straße bis 1):
     * so verteilen sich Hintergrund, Häuser, Bäume und Boden etwa gleichmäßig über den Regler.
     */
    private static final float LAYER_MIN = 0.0008f;
    private static final float LAYER_MAX = 1.02f;

    /** Was ein Panel von den anderen unterscheidet. */
    static final class Spec {
        String section;          // Abschnitt in config.json
        String noun;             // "die Uhr" / "das Widget", für Hilfetexte
        String defaultFont;      // Systemschrift, wenn nichts gewählt
        String defaultColor;     // Farbe, wenn nichts gewählt
        float sizeFrom, sizeTo, sizeStep, sizeDefault; // Größe in % der Bildbreite
        String layerSection, layerKey;                  // wo die Ebene gespeichert wird (fehlt = automatisch)
        Supplier<Float> currentLayer;                   // automatisch gewählte Ebene der geladenen Szene
        boolean widget;                                 // Widget: Großbuchstaben und Inhalt statt führender Null
        View content;                                   // eigene Ansicht fürs Werkzeug „Inhalt“ (Info-Widgets), sonst Datum & Wetter
        boolean linkDefault;                            // Standard von „Stil der Uhr übernehmen“
    }

    private final MainActivity activity;
    private final Settings settings;
    private final Spec spec;
    private final ChipGroup fontChips;
    private final Slider weight;
    private final ColorRow colors;
    private final MaterialSwitch layerAuto;
    private final Slider layerSlider;
    private final TextView linkNote;
    private final EffectTools effects;
    /** Werkzeug → seine Gruppe im Panel; sichtbar ist immer nur eine. */
    private final Map<String, View> tools = new LinkedHashMap<>();
    private String tool = "font";

    TextPanel(MainActivity activity, Settings settings, View panel, Spec spec, int highlight) {
        this.activity = activity;
        this.settings = settings;
        this.spec = spec;
        String sec = spec.section;
        fontChips = panel.findViewById(R.id.fontChips);
        weight = panel.findViewById(R.id.weightSlider);
        layerAuto = panel.findViewById(R.id.depthAutoSwitch);
        layerSlider = panel.findViewById(R.id.depthSlider);
        linkNote = panel.findViewById(R.id.linkNote);
        tools.put("font", panel.findViewById(R.id.groupFont));
        tools.put("size", SliderInput.wrap(panel.findViewById(R.id.sizeSlider), null));
        tools.put("spacing", SliderInput.wrap(panel.findViewById(R.id.spacingSlider), null));
        tools.put("align", panel.findViewById(R.id.alignToggle));
        tools.put("opacity", SliderInput.wrap(panel.findViewById(R.id.opacitySlider), null));
        ((android.view.ViewGroup) weight.getParent()).addView(SliderInput.value(weight, null));
        // Füllung, Kontur, Effekt, Ausblenden: im Code gebaut, ans Panel angehängt
        effects = new EffectTools(activity, settings, sec, spec.widget, panel.findViewById(R.id.groupColor));
        android.widget.LinearLayout root = (android.widget.LinearLayout) panel;
        for (View g : new View[]{effects.fill, effects.outline, effects.effect, effects.fade}) root.addView(g);
        tools.put("fill", effects.fill);
        tools.put("outline", effects.outline);
        tools.put("shadow", effects.effect);
        tools.put("fade", effects.fade);
        tools.put("layer", panel.findViewById(R.id.groupLayer));
        View rotate = rotationTool();
        root.addView(rotate);
        tools.put("rotate", rotate);
        tools.put("content", panel.findViewById(R.id.groupContent));

        setupFont(panel);

        Slider size = panel.findViewById(R.id.sizeSlider);
        size.setValueFrom(spec.sizeFrom);
        size.setValueTo(spec.sizeTo);
        size.setStepSize(spec.sizeStep);
        size.setValue(snap(size, (float) settings.d(sec, "sizePct", spec.sizeDefault)));
        size.setLabelFormatter(v -> spec.sizeStep < 1
                ? String.format(Locale.GERMANY, "%.1f %%", v) : Math.round(v) + " %");
        size.addOnChangeListener((s, v, fromUser) -> {
            if (fromUser) put("sizePct", Math.round(v * 10) / 10.0);
        });

        Slider spacing = panel.findViewById(R.id.spacingSlider);
        spacing.setValue(snap(spacing, Math.round(settings.d(sec, "letterSpacing", 0) * 100)));
        spacing.setLabelFormatter(v -> v > 0 ? "+" + Math.round(v) : String.valueOf(Math.round(v)));
        spacing.addOnChangeListener((s, v, fromUser) -> {
            if (fromUser) put("letterSpacing", v / 100.0);
        });

        MaterialButtonToggleGroup align = panel.findViewById(R.id.alignToggle);
        String a = settings.s(sec, "align", "center");
        align.check("left".equals(a) ? R.id.alignLeft : "right".equals(a) ? R.id.alignRight : R.id.alignCenter);
        align.addOnButtonCheckedListener((g, id, checked) -> {
            if (checked) put("align", id == R.id.alignLeft ? "left" : id == R.id.alignRight ? "right" : "center");
        });

        colors = new ColorRow(activity, settings, panel.findViewById(R.id.groupColor), sec, spec.defaultColor);

        Slider opacity = panel.findViewById(R.id.opacitySlider);
        opacity.setValue(snap(opacity, Math.round(settings.d(sec, "opacity", 1) * 100)));
        opacity.setLabelFormatter(v -> Math.round(v) + " %");
        opacity.addOnChangeListener((s, v, fromUser) -> {
            if (fromUser) put("opacity", v / 100.0);
        });

        setupLayer(panel.findViewById(R.id.depthHelp), highlight);
        if (spec.content != null) {
            spec.content.setVisibility(View.GONE);
            ((android.widget.LinearLayout) panel).addView(spec.content);
            tools.put("content", spec.content);
        } else if (spec.widget) {
            setupContent(panel);
        }
    }

    private void put(String key, Object value) {
        settings.put(spec.section, key, value);
        activity.changed();
    }

    /** Drehung: Regler -180..180 Grad und Schnellwahl fuer die geraden Winkel (seitlich, kopfueber). */
    private View rotationTool() {
        android.widget.LinearLayout box = new android.widget.LinearLayout(activity);
        box.setOrientation(android.widget.LinearLayout.VERTICAL);
        box.setVisibility(View.GONE);
        Slider s = new Slider(activity);
        s.setValueFrom(-180);
        s.setValueTo(180);
        s.setStepSize(1);
        s.setContentDescription("Drehung in Grad");
        s.setValue(Math.max(-180, Math.min(180, Math.round(settings.d(spec.section, "rotation", 0)))));
        s.setLabelFormatter(v -> Math.round(v) + "°");
        s.addOnChangeListener((sl, v, fromUser) -> {
            if (fromUser) put("rotation", Math.round(v));
        });
        box.addView(SliderInput.row(s, null));
        ChipGroup quick = new ChipGroup(activity);
        for (int deg : new int[]{0, 90, 180, -90}) {
            Chip c = activity.filterChip(quick, deg + "°");
            c.setCheckable(false);
            c.setOnClickListener(v -> {
                s.setValue(deg);
                put("rotation", deg);
            });
        }
        box.addView(quick);
        return box;
    }

    // ---------- Schrift ----------

    private void setupFont(View panel) {
        String sec = spec.section;
        buildFontChips();
        fontChips.setOnCheckedStateChangeListener((g, ids) -> {
            Chip c = g.findViewById(g.getCheckedChipId());
            if (c == null) return;
            String tag = (String) c.getTag();
            int w = Math.round(weight.getValue());
            if (tag.startsWith("sys:")) {
                settings.remove(sec, "font");
                settings.put(sec, "systemFont", tag.substring(4));
                settings.put(sec, "weight", w); // Familie wechseln, Stärke behalten
            } else {
                List<String> files = TextLayer.fontFiles(activity.dir());
                settings.put(sec, "font", FontFamilies.nearest(files, tag.substring(4), w));
                settings.remove(sec, "weight"); // die Datei selbst trägt die Stärke
            }
            // ✕ zum Löschen nur an der gewählten eigenen Schrift
            for (int i = 0; i < g.getChildCount(); i++) {
                if (g.getChildAt(i) instanceof Chip) ((Chip) g.getChildAt(i)).setCloseIconVisible(g.getChildAt(i) == c && tag.startsWith("fam:"));
            }
            activity.changed();
        });

        weight.setValue(snap(weight, currentWeight()));
        weight.setLabelFormatter(v -> weightName(Math.round(v)));
        weight.addOnChangeListener((s, v, fromUser) -> {
            if (!fromUser) return;
            String font = settings.s(sec, "font", "");
            List<String> files = TextLayer.fontFiles(activity.dir());
            if (!font.isEmpty() && FontFamilies.weightCount(files, font) > 1) {
                // Familie aus mehreren Dateien: zur Datei mit der nächsten Stärke wechseln
                settings.put(sec, "font", FontFamilies.nearest(files, font, Math.round(v)));
                settings.remove(sec, "weight");
            } else {
                settings.put(sec, "weight", Math.round(v));
            }
            activity.changed();
        });

        MaterialSwitch zero = panel.findViewById(R.id.zeroSwitch);
        zero.setVisibility(View.GONE); // doppelt: steht beim Uhr-Design unter „Inhalt“
        zero.setChecked(settings.s(sec, "format", "HH:mm").startsWith("HH"));
        zero.setOnCheckedChangeListener((b, on) -> put("format", on ? "HH:mm" : "H:mm"));

        MaterialSwitch upper = panel.findViewById(R.id.upperSwitch);
        upper.setVisibility(spec.widget ? View.VISIBLE : View.GONE);
        upper.setChecked(settings.b(sec, "uppercase", false));
        upper.setOnCheckedChangeListener((b, on) -> put("uppercase", on));
    }

    /** Aktuelle Stärke: gespeichert, sonst aus Dateiname bzw. Systemschrift. */
    private float currentWeight() {
        String sec = spec.section;
        if (settings.has(sec, "weight")) return (float) settings.d(sec, "weight", 400);
        String font = settings.s(sec, "font", "");
        if (!font.isEmpty()) return FontFamilies.weightOf(font);
        String sys = settings.s(sec, "systemFont", spec.defaultFont);
        if (sys.endsWith("-black")) return 900;
        if (sys.endsWith("-medium")) return 500;
        if (sys.endsWith("-light")) return 300;
        if (sys.endsWith("-thin")) return 100;
        return 400;
    }

    private static String weightName(int w) {
        String[] names = {"Hauchdünn", "Extraleicht", "Leicht", "Normal", "Mittel", "Halbfett", "Fett", "Extrafett", "Schwarz"};
        return names[Math.max(0, Math.min(8, Math.round(w / 100f) - 1))] + " " + w;
    }

    void buildFontChips() {
        String sec = spec.section;
        fontChips.removeAllViews();
        String font = settings.s(sec, "font", "");
        String sysCurrent = systemFamily(settings.s(sec, "systemFont", spec.defaultFont));
        String famCurrent = font.isEmpty() ? null : FontFamilies.familyOf(font);
        for (String[] f : SYSTEM_FONTS) {
            Chip c = activity.filterChip(fontChips, f[0]);
            c.setTag("sys:" + f[1]);
            c.setTypeface(Typeface.create(f[1], Typeface.NORMAL));
            if (famCurrent == null && f[1].equals(sysCurrent)) c.setChecked(true);
        }
        // eigene Schriften als Familien; Tag "fam:<eine Datei der Familie>", die Stärke wählt der Regler
        List<String> files = TextLayer.fontFiles(activity.dir());
        for (Map.Entry<String, List<String>> fam : FontFamilies.group(files).entrySet()) {
            String sample = FontFamilies.nearest(fam.getValue(), fam.getValue().get(0), 400);
            Typeface t = new Typeface.Builder(new File(activity.dir(), sample)).build();
            if (t == null) continue;
            Chip c = activity.filterChip(fontChips, fam.getKey());
            c.setTag("fam:" + sample);
            c.setTypeface(t);
            c.setCloseIconContentDescription("Schrift " + fam.getKey() + " löschen");
            c.setOnCloseIconClickListener(v -> activity.confirmDeleteFont(fam.getKey(), fam.getValue()));
            c.setCloseIconVisible(fam.getKey().equals(famCurrent));
            if (fam.getKey().equals(famCurrent)) c.setChecked(true);
        }
        Chip add = (Chip) activity.getLayoutInflater().inflate(R.layout.chip_action, fontChips, false);
        add.setText("Schrift hinzufügen");
        add.setCheckable(false);
        add.setOnClickListener(v -> activity.pickFontFor(this));
        fontChips.addView(add);
    }

    /** "sans-serif-black" → "sans-serif": die Stärke steckt im Regler. */
    private static String systemFamily(String sys) {
        if (sys.startsWith("sans-serif") && !sys.startsWith("sans-serif-condensed")) return "sans-serif";
        return sys;
    }

    static String prettyFontName(String file) {
        return FontFamilies.familyOf(file);
    }

    // ---------- Ebene ----------

    /** Nach dem Laden neuer Ebenen: im Automatik-Modus zeigt der Regler, wohin die App den Text gesetzt hat. */
    void onSceneLoaded() {
        float t = spec.currentLayer.get();
        if (layerAuto.isChecked() && !Float.isNaN(t)) layerSlider.setValue(thresholdToSlider(t));
    }

    private void setupLayer(TextView help, int highlight) {
        String sec = spec.layerSection;
        String key = spec.layerKey;
        float threshold = (float) settings.d(sec, key, Double.NaN);
        boolean isAuto = Float.isNaN(threshold);
        layerAuto.setChecked(isAuto);
        if (!isAuto) layerSlider.setValue(thresholdToSlider(threshold));
        Runnable showMode = () -> {
            layerSlider.setEnabled(!layerAuto.isChecked());
            help.setText(layerAuto.isChecked()
                    ? "Die App schaut, was hinter " + dative(spec.noun) + " liegt. Was deutlich näher ist, schiebt sich davor."
                    : "Beim Ziehen wird eingefärbt, was vor " + dative(spec.noun) + " liegt, z. B. vor einem Haus, aber hinter einem Baum.");
        };
        showMode.run();
        layerAuto.setOnCheckedChangeListener((b, on) -> {
            if (on) settings.remove(sec, key);
            else settings.put(sec, key, sliderToThreshold(layerSlider.getValue()));
            showMode.run();
            activity.changed();
        });
        layerSlider.setLabelFormatter(v -> v >= 99 ? "vor allem" : v <= 1 ? "hinter allem" : Math.round(v) + " %");
        PreviewView preview = activity.preview();
        layerSlider.addOnChangeListener((sl, v, fromUser) -> {
            if (fromUser) preview.showLayerHighlight(sliderToThreshold(v), highlight);
        });
        // erst beim Loslassen (bzw. nach dem Eintippen) speichern: jede Änderung berechnet die Bildebenen neu
        java.util.function.Consumer<Float> save = v -> {
            settings.put(sec, key, sliderToThreshold(v));
            activity.changed();
            activity.later(preview::hideLayerHighlight, 900);
        };
        layerSlider.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
            @Override
            public void onStartTrackingTouch(Slider sl) {
                preview.showLayerHighlight(sliderToThreshold(sl.getValue()), highlight);
            }

            @Override
            public void onStopTrackingTouch(Slider sl) {
                save.accept(sl.getValue());
            }
        });
        SliderInput.wrap(layerSlider, save);
    }

    /** "die Uhr" → "der Uhr", "das Widget" → "dem Widget". */
    private static String dative(String noun) {
        return noun.replace("die ", "der ").replace("das ", "dem ");
    }

    // ---------- Inhalt (nur Widget) ----------

    private void setupContent(View panel) {
        String sec = spec.section;
        ChipGroup formats = panel.findViewById(R.id.formatChips);
        String format = settings.s(sec, "format", "all");
        for (String[] f : new String[][]{{"all", "Datum und Wetter"}, {"date_only", "Nur Datum"}, {"weather_only", "Nur Wetter"}}) {
            Chip c = activity.filterChip(formats, f[1]);
            c.setTag(f[0]);
            if (f[0].equals(format)) c.setChecked(true);
        }
        formats.setOnCheckedStateChangeListener((g, ids) -> {
            Chip c = g.findViewById(g.getCheckedChipId());
            if (c != null) put("format", c.getTag());
        });

        ChipGroup dates = panel.findViewById(R.id.dateChips);
        String dateFormat = settings.s(sec, "dateFormat", DATE_FORMATS[0][0]);
        Date now = new Date();
        for (String[] f : DATE_FORMATS) {
            Chip c = activity.filterChip(dates, new SimpleDateFormat(f[0], Locale.GERMANY).format(now));
            c.setTag(f[0]);
            if (f[0].equals(dateFormat)) c.setChecked(true);
        }
        dates.setOnCheckedStateChangeListener((g, ids) -> {
            Chip c = g.findViewById(g.getCheckedChipId());
            if (c != null) put("dateFormat", c.getTag());
        });

        bindSwitch(panel, R.id.twoLinesSwitch, "twoLines");
        bindSwitch(panel, R.id.iconsSwitch, "lineIcons");
        bindSwitch(panel, R.id.glassSwitch, "glass");
        MaterialSwitch link = bindSwitch(panel, R.id.linkSwitch, "linkClock");
        link.setOnCheckedChangeListener((b, on) -> {
            put("linkClock", on);
            showTool(tool);
        });

        android.widget.LinearLayout gc = panel.findViewById(R.id.groupContent);
        if (gc != null) {
            com.google.android.material.button.MaterialButton rm = new com.google.android.material.button.MaterialButton(
                    activity, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
            rm.setText("Datum & Wetter entfernen");
            rm.setOnClickListener(v -> {
                if (activity.widgets() != null) activity.widgets().confirmRemove("widget");
            });
            android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(-2, -2);
            lp.topMargin = activity.dp(12);
            gc.addView(rm, lp);
        }
    }

    private MaterialSwitch bindSwitch(View panel, int id, String key) {
        MaterialSwitch s = panel.findViewById(id);
        s.setChecked(settings.b(spec.section, key, false));
        s.setOnCheckedChangeListener((b, on) -> put(key, on));
        return s;
    }

    // ---------- Werkzeuge ----------

    /** Nur die Gruppe dieses Werkzeugs zeigen. */
    void showTool(String tool) {
        this.tool = tool;
        for (Map.Entry<String, View> t : tools.entrySet()) {
            t.getValue().setVisibility(t.getKey().equals(tool) ? View.VISIBLE : View.GONE);
        }
        boolean linked = spec.widget && settings.b(spec.section, "linkClock", spec.linkDefault)
                && java.util.Set.of("font", "color", "fill", "outline", "shadow").contains(tool);
        linkNote.setVisibility(linked ? View.VISIBLE : View.GONE);
        View group = tools.get(tool);
        if (group != null) group.setAlpha(linked ? 0.45f : 1f);
    }

    void setPhotoColors(int[] c) {
        colors.setPhotoColors(c);
        effects.setPhotoColors(c);
    }

    String section() {
        return spec.section;
    }

    static float sliderToThreshold(float v) {
        return (float) (LAYER_MIN * Math.pow(LAYER_MAX / LAYER_MIN, v / 100f));
    }

    static float thresholdToSlider(float threshold) {
        double v = Math.log(threshold / LAYER_MIN) / Math.log(LAYER_MAX / LAYER_MIN) * 100;
        return Math.round(Math.max(0, Math.min(100, v)));
    }

    /** Auf Bereich und Raster des Reglers bringen (sonst wirft der Slider). */
    private static float snap(Slider s, float v) {
        float step = s.getStepSize();
        float clamped = Math.max(s.getValueFrom(), Math.min(s.getValueTo(), v));
        if (step <= 0) return clamped;
        float snapped = s.getValueFrom() + Math.round((clamped - s.getValueFrom()) / step) * step;
        return Math.round(snapped * 100) / 100f;
    }
}
