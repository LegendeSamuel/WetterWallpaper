package de.seelig.wetterwallpaper;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.color.MaterialColors;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Farbwerkzeug für Uhr, Widgets und Effektfarben, bewusst kurz:
 * Automatisch, Weiß, Schwarz, Farbwähler (+), danach die eigenen gemerkten Farben und die Farben aus dem Foto
 * (samt zwei passenden). Mit + gewählte Farben werden gemerkt, lange drücken entfernt sie wieder.
 */
final class ColorRow {
    private static final int MAX_SAVED = 12;

    private final MainActivity activity;
    private final Settings settings;
    private final String section;
    private final String defaultColor;
    private final LinearLayout row;
    private final TextView help;
    /** Nur Effektfarbe: fester Schlüssel und Beschriftung von "Automatisch". */
    private final String effectKey;
    private final String autoLabel;
    private int[] photo = new int[0];

    ColorRow(MainActivity activity, Settings settings, View group, String section, String defaultColor) {
        this.activity = activity;
        this.settings = settings;
        this.section = section;
        this.defaultColor = defaultColor;
        effectKey = null;
        autoLabel = "Automatisch";
        row = group.findViewById(R.id.colorRow);
        help = group.findViewById(R.id.colorHelp);
        View dayNight = group.findViewById(R.id.dayNightToggle);
        if (dayNight != null) dayNight.setVisibility(View.GONE);
        build();
    }

    /** Zeile für eine Effektfarbe (z. B. "shadowColor"); "auto" = Farbe ergibt sich aus der Schrift. */
    ColorRow(MainActivity activity, Settings settings, LinearLayout row, String section, String key, String autoLabel) {
        this.activity = activity;
        this.settings = settings;
        this.section = section;
        this.defaultColor = "auto";
        this.row = row;
        this.help = null;
        this.effectKey = key;
        this.autoLabel = autoLabel;
        build();
    }

    void setPhotoColors(int[] colors) {
        photo = PhotoColors.withHarmonies(colors);
        build();
    }

    private String key() {
        return effectKey != null ? effectKey : "color";
    }

    void build() {
        String current = settings.s(section, key(), defaultColor);
        row.removeAllViews();
        int size = activity.dp(effectKey != null ? 32 : 36);
        addSwatch("auto", autoLabel, current, size, new int[]{0xFF26344A, 0xFFEEF2FA}, false);
        boolean known = current.isEmpty() || "auto".equals(current);
        known |= addColor(0xFFFFFFFF, "Weiß", current, size, false);
        known |= addColor(0xFF111111, "Schwarz", current, size, false);
        addPickerButton(size, current);

        List<String> saved = saved();
        if (!saved.isEmpty()) addLabel("Gemerkt");
        for (String hex : saved) {
            known |= addColor(TextLayer.parseColor(hex, Color.WHITE), "Gemerkte Farbe " + hex, current, size, true);
        }
        boolean fromPhoto = false;
        for (int c : photo) fromPhoto |= String.format(Locale.ROOT, "#%06X", c & 0xFFFFFF).equalsIgnoreCase(current);
        // aktuelle Farbe kommt nirgends vor (z. B. aus einem Favoriten): trotzdem zeigen
        if (!known && !fromPhoto) addSwatch(current, "Aktuelle Farbe " + current, current, size, swatchOf(current), false);
        if (photo.length > 0) {
            addLabel("Aus dem Foto");
            for (int c : photo) addColor(c, "Farbe aus dem Foto", current, size, false);
        }
        if (help != null) {
            help.setText("auto".equals(current) || current.isEmpty()
                    ? "Automatisch: hell vor dunklem Hintergrund, dunkel vor hellem."
                    : "Mit + eine eigene Farbe wählen, sie wird gemerkt. Lange drücken entfernt eine gemerkte Farbe.");
        }
    }

    // ---------- Gemerkte Farben (für alle Elemente gleich) ----------

    private SharedPreferences prefs() {
        return activity.getSharedPreferences("colors", android.content.Context.MODE_PRIVATE);
    }

    private List<String> saved() {
        List<String> out = new ArrayList<>();
        for (String s : prefs().getString("saved", "").split(",")) {
            if (s.matches("#[0-9A-Fa-f]{6}")) out.add(s.toUpperCase(Locale.ROOT));
        }
        return out;
    }

    private void remember(String hex) {
        List<String> list = saved();
        list.remove(hex);
        list.add(0, hex);
        while (list.size() > MAX_SAVED) list.remove(list.size() - 1);
        prefs().edit().putString("saved", String.join(",", list)).apply();
    }

    private void forget(String hex) {
        List<String> list = saved();
        list.remove(hex.toUpperCase(Locale.ROOT));
        prefs().edit().putString("saved", String.join(",", list)).apply();
        Toast.makeText(activity, "Farbe entfernt", Toast.LENGTH_SHORT).show();
        build();
    }

    // ---------- Bausteine ----------

    private static int[] swatchOf(String hex) {
        int c = TextLayer.parseColor("auto".equals(hex) ? "" : hex, Color.WHITE);
        return new int[]{c, c};
    }

    /** Fügt eine Farbe hinzu; gibt zurück, ob sie die aktuelle ist. */
    private boolean addColor(int c, String label, String current, int size, boolean removable) {
        String hex = String.format(Locale.ROOT, "#%06X", c & 0xFFFFFF);
        addSwatch(hex, label, current, size, new int[]{c, c}, removable);
        return hex.equalsIgnoreCase(current);
    }

    private void addSwatch(String value, String label, String current, int size, int[] colors, boolean removable) {
        View v = new View(activity);
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.BL_TR, colors);
        d.setShape(GradientDrawable.OVAL);
        boolean selected = value.equalsIgnoreCase(current);
        d.setStroke(activity.dp(selected ? 3 : 1), MaterialColors.getColor(row,
                selected ? com.google.android.material.R.attr.colorPrimary : com.google.android.material.R.attr.colorOutline));
        v.setBackground(d);
        v.setContentDescription(label + (selected ? ", ausgewählt" : "") + (removable ? ", lange drücken zum Entfernen" : ""));
        v.setOnClickListener(x -> {
            if (value.isEmpty()) settings.remove(section, key());
            else settings.put(section, key(), value);
            activity.changed();
            build();
        });
        if (removable) {
            v.setOnLongClickListener(x -> {
                forget(value);
                return true;
            });
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
        lp.setMarginEnd(activity.dp(10));
        row.addView(v, lp);
    }

    private void addLabel(String text) {
        TextView t = new TextView(activity);
        t.setText(text);
        t.setTextAppearance(R.style.TextAppearance_Wetter_LabelMedium);
        t.setTextColor(MaterialColors.getColor(row, com.google.android.material.R.attr.colorOnSurfaceVariant));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMarginStart(activity.dp(6));
        lp.setMarginEnd(activity.dp(10));
        row.addView(t, lp);
    }

    /** "+": eigene Farbe mit dem Farbwähler; sie wird gemerkt. */
    private void addPickerButton(int size, String current) {
        TextView plus = new TextView(activity);
        plus.setText("+");
        plus.setGravity(Gravity.CENTER);
        plus.setTextSize(20);
        plus.setTextColor(MaterialColors.getColor(row, com.google.android.material.R.attr.colorOnSurface));
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setStroke(activity.dp(1), MaterialColors.getColor(row, com.google.android.material.R.attr.colorOutline));
        plus.setBackground(d);
        plus.setContentDescription("Eigene Farbe wählen");
        int start = TextLayer.parseColor("auto".equals(current) ? "" : current, Color.WHITE);
        plus.setOnClickListener(x -> ColorPicker.show(activity, start, c -> {
            String hex = String.format(Locale.ROOT, "#%06X", c & 0xFFFFFF);
            settings.put(section, key(), hex);
            remember(hex);
            activity.changed();
            build();
        }));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
        lp.setMarginEnd(activity.dp(10));
        row.addView(plus, lp);
    }
}
