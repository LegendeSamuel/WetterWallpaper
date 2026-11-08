package de.seelig.wetterwallpaper;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeProvider;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.slider.Slider;

import java.util.Locale;
import java.util.function.Consumer;

/** Zahl rechts neben einem Regler: zeigt den Wert, antippen öffnet ein Zahlenfeld für einen genauen Wert. */
final class SliderInput {
    private SliderInput() {
    }

    /** Regler s an seinem Platz durch eine Zeile [Regler | Zahl] ersetzen; die Zeile übernimmt Sichtbarkeit und Maße. */
    static LinearLayout wrap(Slider s, Consumer<Float> then) {
        ViewGroup parent = (ViewGroup) s.getParent();
        int index = parent.indexOfChild(s);
        ViewGroup.LayoutParams lp = s.getLayoutParams();
        parent.removeView(s);
        LinearLayout row = row(s, then);
        row.setVisibility(s.getVisibility());
        s.setVisibility(View.VISIBLE);
        parent.addView(row, index, lp);
        return row;
    }

    /** Neue Zeile [Regler | Zahl]. */
    static LinearLayout row(Slider s, Consumer<Float> then) {
        LinearLayout row = new LinearLayout(s.getContext());
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(s, new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(value(s, then));
        return row;
    }

    /**
     * Wertanzeige für s. Ein getippter Wert läuft wie Ziehen durch die Listener des Reglers (fromUser = true),
     * then (darf null sein) ist für Regler, die erst beim Loslassen speichern.
     */
    static TextView value(Slider s, Consumer<Float> then) {
        Context ctx = s.getContext();
        float d = ctx.getResources().getDisplayMetrics().density;
        TextView t = new TextView(ctx);
        t.setTextAppearance(R.style.TextAppearance_Wetter_LabelLarge);
        t.setGravity(Gravity.CENTER);
        t.setMinWidth(Math.round(52 * d));
        t.setPadding(Math.round(8 * d), Math.round(6 * d), Math.round(8 * d), Math.round(6 * d));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(8 * d);
        bg.setStroke(Math.round(d), 0x66FFFFFF);
        t.setBackground(bg);
        Runnable show = () -> t.setText(format(s, s.getValue()));
        show.run();
        s.addOnChangeListener((sl, v, fromUser) -> show.run());
        t.setContentDescription("Wert eintippen: " + s.getContentDescription());
        t.setOnClickListener(v -> {
            if (s.isEnabled()) ask(s, then);
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMarginStart(Math.round(4 * d));
        t.setLayoutParams(lp);
        return t;
    }

    private static void ask(Slider s, Consumer<Float> then) {
        Context ctx = s.getContext();
        boolean decimal = s.getStepSize() > 0 && s.getStepSize() < 1;
        EditText e = new EditText(ctx);
        e.setInputType(InputType.TYPE_CLASS_NUMBER
                | (decimal ? InputType.TYPE_NUMBER_FLAG_DECIMAL : 0)
                | (s.getValueFrom() < 0 ? InputType.TYPE_NUMBER_FLAG_SIGNED : 0));
        e.setText(format(s, s.getValue()));
        e.setSelectAllOnFocus(true);
        FrameLayout box = new FrameLayout(ctx);
        int pad = Math.round(20 * ctx.getResources().getDisplayMetrics().density);
        box.setPadding(pad, 0, pad, 0);
        box.addView(e);
        CharSequence title = s.getContentDescription();
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(ctx)
                .setTitle(title != null ? title : "Wert")
                .setMessage("Von " + format(s, s.getValueFrom()) + " bis " + format(s, s.getValueTo()))
                .setView(box)
                .setNegativeButton("Abbrechen", null)
                .setPositiveButton("Übernehmen", (dlg, w) -> {
                    Float v = parse(e.getText().toString());
                    if (v != null) set(s, v, then);
                })
                .create();
        if (dialog.getWindow() != null) dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        dialog.show();
        e.requestFocus();
    }

    /** Auf Bereich und Raster bringen und setzen, als hätte man gezogen. */
    private static void set(Slider s, float v, Consumer<Float> then) {
        float from = s.getValueFrom();
        float step = s.getStepSize();
        float x = Math.max(from, Math.min(s.getValueTo(), v));
        if (step > 0) x = Math.round((from + Math.round((x - from) / step) * step) * 100) / 100f;
        // Material löst bei „Wert setzen“ der Bedienungshilfen dieselben Listener aus wie beim Ziehen (Material 1.12)
        Bundle args = new Bundle();
        args.putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE, x);
        AccessibilityNodeProvider p = s.getAccessibilityNodeProvider();
        boolean done = p != null && p.performAction(0, android.R.id.accessibilityActionSetProgress, args);
        if (!done) s.setValue(x); // unverändert oder ältere Bibliothek: wenigstens anzeigen
        if (then != null) then.accept(x);
    }

    private static Float parse(String text) {
        try {
            return Float.parseFloat(text.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String format(Slider s, float v) {
        if (s.getStepSize() > 0 && s.getStepSize() < 1) return String.format(Locale.GERMANY, "%.1f", v);
        return String.valueOf(Math.round(v));
    }
}
