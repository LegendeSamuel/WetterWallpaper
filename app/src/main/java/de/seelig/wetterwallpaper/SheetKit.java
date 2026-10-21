package de.seelig.wetterwallpaper;

import android.content.res.ColorStateList;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Bausteine für die Blätter (Einstellungen, Modus & Zeiten): aufklappbare Abschnitte, Schalter mit (i),
 * Regler, Chip-Reihen und das Abzeichen „gilt für …“. Erklärtexte stehen hinter dem (i), nicht dauerhaft da.
 */
final class SheetKit {
    /** Aufgeklappte Abschnitte, über das Schließen des Blatts hinaus gemerkt. */
    private static final Set<String> OPEN = new HashSet<>(Set.of("scene"));

    private SheetKit() {
    }

    static LinearLayout column(MainActivity a) {
        LinearLayout l = new LinearLayout(a);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    static TextView text(MainActivity a, String s, int style, int colorAttr) {
        TextView t = new TextView(a);
        t.setText(s);
        t.setTextAppearance(style);
        if (colorAttr != 0) t.setTextColor(MaterialColors.getColor(a.findViewById(R.id.root), colorAttr));
        return t;
    }

    static TextView heading(MainActivity a, String s) {
        TextView t = text(a, s, R.style.TextAppearance_Wetter_TitleLarge, com.google.android.material.R.attr.colorPrimary);
        t.setPadding(0, a.dp(4), 0, a.dp(8));
        return t;
    }

    static TextView label(MainActivity a, String s) {
        TextView t = text(a, s, R.style.TextAppearance_Wetter_LabelLarge, com.google.android.material.R.attr.colorOnSurfaceVariant);
        t.setPadding(0, a.dp(14), 0, a.dp(6));
        return t;
    }

    static TextView help(MainActivity a, String s) {
        TextView t = text(a, s, R.style.TextAppearance_Wetter_BodySmall, com.google.android.material.R.attr.colorOnSurfaceVariant);
        t.setPadding(0, a.dp(2), 0, a.dp(6));
        return t;
    }

    /** „● gilt für Nacht“ in Bernstein: diese Einstellung wird pro Modus gespeichert. */
    static CharSequence badgeText(String mode) {
        SpannableStringBuilder b = new SpannableStringBuilder("● gilt für " + mode);
        b.setSpan(new ForegroundColorSpan(0xFFF2A007), 0, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return b;
    }

    static TextView badge(MainActivity a, String mode) {
        TextView t = text(a, "", R.style.TextAppearance_Wetter_LabelMedium, 0);
        t.setText(badgeText(mode));
        t.setPadding(a.dp(8), 0, 0, 0);
        return t;
    }

    /** Kleines (i): klappt den Erklärtext darunter auf und zu. */
    private static View infoButton(MainActivity a, TextView helpView, String title) {
        ImageView i = new ImageView(a);
        i.setImageResource(R.drawable.ic_info);
        i.setImageTintList(ColorStateList.valueOf(0xFFAAAAAA));
        int p = a.dp(10);
        i.setPadding(p, p, p, p);
        i.setContentDescription("Erklärung zu " + title);
        i.setBackgroundResource(android.R.drawable.list_selector_background);
        i.setOnClickListener(v -> helpView.setVisibility(helpView.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));
        return i;
    }

    /** Titel, optional (i) mit Erklärung, Schalter rechts. */
    static LinearLayout switchRow(MainActivity a, String title, String helpText, boolean checked, Consumer<Boolean> onChange) {
        LinearLayout box = column(a);
        LinearLayout row = new LinearLayout(a);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(a.dp(48));
        TextView t = text(a, title, R.style.TextAppearance_Wetter_BodyLarge, 0);
        row.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
        TextView h = helpText != null ? help(a, helpText) : null;
        if (h != null) {
            h.setVisibility(View.GONE);
            row.addView(infoButton(a, h, title), new LinearLayout.LayoutParams(a.dp(40), a.dp(40)));
        }
        MaterialSwitch s = new MaterialSwitch(a);
        s.setChecked(checked);
        s.setContentDescription(title);
        s.setOnCheckedChangeListener((b, on) -> onChange.accept(on));
        row.addView(s);
        box.addView(row);
        if (h != null) box.addView(h);
        return box;
    }

    /** Beschrifteter Regler, Änderung erst beim Loslassen (spart Neuberechnungen) oder live. */
    static LinearLayout slider(MainActivity a, String title, float from, float to, float step, float value,
                               String unit, boolean live, Consumer<Float> onChange, String lo, String hi) {
        LinearLayout box = column(a);
        box.addView(text(a, title, R.style.TextAppearance_Wetter_BodyMedium, 0));
        Slider s = new Slider(a);
        s.setValueFrom(from);
        s.setValueTo(to);
        s.setStepSize(step);
        float v = Math.max(from, Math.min(to, value));
        if (step > 0) v = from + Math.round((v - from) / step) * step;
        s.setValue(v);
        s.setContentDescription(title);
        s.setLabelFormatter(x -> Math.round(x) + unit);
        if (live) {
            s.addOnChangeListener((sl, x, fromUser) -> {
                if (fromUser) onChange.accept(x);
            });
        } else {
            s.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
                @Override
                public void onStartTrackingTouch(Slider sl) {
                }

                @Override
                public void onStopTrackingTouch(Slider sl) {
                    onChange.accept(sl.getValue());
                }
            });
        }
        box.addView(s);
        if (lo != null) {
            LinearLayout ends = new LinearLayout(a);
            ends.setPadding(a.dp(16), 0, a.dp(16), 0);
            TextView l = help(a, lo);
            ends.addView(l, new LinearLayout.LayoutParams(0, -2, 1));
            ends.addView(help(a, hi));
            box.addView(ends);
        }
        return box;
    }

    /** Einfachauswahl als Chips; {tag, Beschriftung}. Umbrechend, damit nichts versteckt ist. */
    static ChipGroup choice(MainActivity a, String[][] options, String current, Consumer<String> onPick) {
        ChipGroup g = new ChipGroup(a);
        g.setSingleSelection(true);
        g.setSelectionRequired(true);
        for (String[] o : options) {
            Chip c = a.filterChip(g, o[1]);
            c.setTag(o[0]);
            if (o[0].equals(current)) c.setChecked(true);
        }
        g.setOnCheckedStateChangeListener((gr, ids) -> {
            Chip c = gr.findViewById(gr.getCheckedChipId());
            if (c != null) onPick.accept((String) c.getTag());
        });
        return g;
    }

    /** Dieselbe Auswahl in einer Zeile zum Wischen. */
    static HorizontalScrollView choiceLine(MainActivity a, String[][] options, String current, Consumer<String> onPick) {
        HorizontalScrollView h = new HorizontalScrollView(a);
        h.setHorizontalScrollBarEnabled(false);
        ChipGroup g = choice(a, options, current, onPick);
        g.setSingleLine(true);
        h.addView(g);
        return h;
    }

    static MaterialButton button(MainActivity a, String text, int iconRes, boolean filled, View.OnClickListener click) {
        MaterialButton b = new MaterialButton(a, null, filled
                ? com.google.android.material.R.attr.materialButtonStyle
                : com.google.android.material.R.attr.materialButtonOutlinedStyle);
        b.setText(text);
        if (iconRes != 0) b.setIconResource(iconRes);
        b.setOnClickListener(click);
        return b;
    }

    /**
     * Aufklappbarer Abschnitt: Symbol, Titel, kurze Zusammenfassung, optional Abzeichen. Der Inhalt wird erst beim
     * Aufklappen gebaut; neue Unterpunkte kommen einfach in body.
     */
    static LinearLayout section(MainActivity a, String id, int iconRes, String title, String summary, String badgeMode,
                                Supplier<View> body) {
        LinearLayout box = column(a);
        LinearLayout head = new LinearLayout(a);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setMinimumHeight(a.dp(56));
        head.setBackgroundResource(android.R.drawable.list_selector_background);
        ImageView ic = new ImageView(a);
        ic.setImageResource(iconRes);
        ic.setImageTintList(ColorStateList.valueOf(0xFFF2A007));
        head.addView(ic, new LinearLayout.LayoutParams(a.dp(22), a.dp(22)));
        LinearLayout titles = column(a);
        titles.setPadding(a.dp(14), a.dp(8), a.dp(8), a.dp(8));
        TextView t = text(a, title, R.style.TextAppearance_Wetter_TitleMedium, 0);
        titles.addView(t);
        if (summary != null) titles.addView(text(a, summary, R.style.TextAppearance_Wetter_BodySmall,
                com.google.android.material.R.attr.colorOnSurfaceVariant));
        head.addView(titles, new LinearLayout.LayoutParams(0, -2, 1));
        if (badgeMode != null) head.addView(badge(a, badgeMode));
        ImageView chev = new ImageView(a);
        chev.setImageResource(R.drawable.ic_expand);
        chev.setImageTintList(ColorStateList.valueOf(0xFFAAAAAA));
        head.addView(chev, new LinearLayout.LayoutParams(a.dp(22), a.dp(22)));
        box.addView(head);

        LinearLayout content = column(a);
        content.setPadding(a.dp(36), 0, 0, a.dp(14));
        box.addView(content);
        Runnable show = () -> {
            boolean open = OPEN.contains(id);
            content.removeAllViews();
            if (open) content.addView(body.get());
            content.setVisibility(open ? View.VISIBLE : View.GONE);
            chev.setRotation(open ? 180 : 0);
            head.setContentDescription(title + (open ? ", zuklappen" : ", aufklappen"));
        };
        head.setOnClickListener(v -> {
            if (!OPEN.remove(id)) OPEN.add(id);
            show.run();
        });
        show.run();

        View line = new View(a);
        line.setBackgroundColor(0xFF2E2E2E);
        box.addView(line, new LinearLayout.LayoutParams(-1, a.dp(1)));
        return box;
    }
}
