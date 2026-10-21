package de.seelig.wetterwallpaper;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Locale;
import java.util.function.IntConsumer;

/**
 * Farbwähler wie in Photoshop: großes Feld (Sättigung nach rechts, Helligkeit nach oben), daneben der
 * Farbton-Streifen, darüber Neu | Vorher (Vorher antippen setzt zurück), darunter der Hex-Wert.
 */
final class ColorPicker {
    private ColorPicker() {
    }

    static void show(Context ctx, int initial, IntConsumer onPicked) {
        float density = ctx.getResources().getDisplayMetrics().density;
        float[] hsv = new float[3];
        Color.colorToHSV(initial, hsv);

        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(24 * density);
        box.setPadding(pad, Math.round(8 * density), pad, 0);

        // Neu | Vorher
        LinearLayout compare = new LinearLayout(ctx);
        TextView now = swatch(ctx, "Neu", density, true);
        TextView before = swatch(ctx, "Vorher", density, false);
        ((GradientDrawable) before.getBackground()).setColor(initial | 0xFF000000);
        before.setTextColor(textOn(initial));
        before.setContentDescription("Vorherige Farbe, antippen zum Zurücksetzen");
        int swatchH = Math.round(48 * density);
        compare.addView(now, new LinearLayout.LayoutParams(0, swatchH, 1));
        compare.addView(before, new LinearLayout.LayoutParams(0, swatchH, 1));
        box.addView(compare);

        Field field = new Field(ctx, hsv);
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Math.round(260 * density));
        flp.topMargin = Math.round(14 * density);
        box.addView(field, flp);

        EditText hex = new EditText(ctx);
        hex.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        hex.setFilters(new InputFilter[]{new InputFilter.LengthFilter(7)});
        hex.setSingleLine();
        hex.setTypeface(Typeface.MONOSPACE);
        hex.setGravity(Gravity.CENTER);
        hex.setContentDescription("Hex-Wert, z. B. #FF8800");
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        hlp.topMargin = Math.round(8 * density);
        box.addView(hex, hlp);

        boolean[] sync = {false}; // Feld und Hex-Wert gleichen sich ab, ohne sich gegenseitig neu auszulösen
        Runnable update = () -> {
            int c = Color.HSVToColor(hsv);
            ((GradientDrawable) now.getBackground()).setColor(c);
            now.setTextColor(textOn(c));
            if (!sync[0]) {
                sync[0] = true;
                hex.setText(String.format(Locale.ROOT, "#%06X", c & 0xFFFFFF));
                sync[0] = false;
            }
            field.invalidate();
        };
        field.onChange = update;
        before.setOnClickListener(v -> {
            Color.colorToHSV(initial, hsv);
            update.run();
        });
        hex.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                String t = s.toString().trim();
                if (!t.startsWith("#")) t = "#" + t;
                if (sync[0] || !t.matches("#[0-9A-Fa-f]{6}")) return;
                Color.colorToHSV(Color.parseColor(t), hsv);
                sync[0] = true; // Eingabe nicht überschreiben, während getippt wird
                update.run();
                sync[0] = false;
            }
        });
        update.run();

        new MaterialAlertDialogBuilder(ctx)
                .setTitle("Eigene Farbe")
                .setView(box)
                .setNegativeButton("Abbrechen", null)
                .setPositiveButton("Übernehmen", (d, w) -> onPicked.accept(Color.HSVToColor(hsv)))
                .show();
    }

    private static TextView swatch(Context ctx, String label, float density, boolean left) {
        TextView t = new TextView(ctx);
        t.setText(label);
        t.setGravity(Gravity.CENTER);
        t.setTextSize(14);
        GradientDrawable d = new GradientDrawable();
        float r = 14 * density;
        d.setCornerRadii(left ? new float[]{r, r, 0, 0, 0, 0, r, r} : new float[]{0, 0, r, r, r, r, 0, 0});
        t.setBackground(d);
        return t;
    }

    private static int textOn(int c) {
        return Color.luminance(c | 0xFF000000) > 0.5f ? 0xFF1C1B1F : 0xFFFFFFFF;
    }

    /** Sättigung/Helligkeit-Feld und Farbton-Streifen in einer Fläche. */
    private static final class Field extends View {
        /** Farbton von oben (360) nach unten (0) wie in Photoshop. */
        private static final int[] HUES = {0xFFFF0000, 0xFFFF00FF, 0xFF0000FF, 0xFF00FFFF, 0xFF00FF00, 0xFFFFFF00, 0xFFFF0000};
        private final float[] hsv;
        private final float d;
        private final RectF sq = new RectF();
        private final RectF strip = new RectF();
        private final RectF bar = new RectF();
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float[] pure = {0, 1, 1};
        private Shader dark;
        private Shader hues;
        private boolean onStrip;
        Runnable onChange;

        Field(Context ctx, float[] hsv) {
            super(ctx);
            this.hsv = hsv;
            d = ctx.getResources().getDisplayMetrics().density;
            ring.setStyle(Paint.Style.STROKE);
            setContentDescription("Farbfeld: links blass, rechts kräftig, oben hell, unten dunkel. Rechts der Farbton.");
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            float sw = 28 * d;
            strip.set(w - sw, 0, w, h);
            sq.set(0, 0, w - sw - 14 * d, h);
            dark = new LinearGradient(0, sq.top, 0, sq.bottom, 0x00000000, 0xFF000000, Shader.TileMode.CLAMP);
            hues = new LinearGradient(0, strip.top, 0, strip.bottom, HUES, null, Shader.TileMode.CLAMP);
        }

        @Override
        protected void onDraw(Canvas c) {
            float r = 10 * d;
            pure[0] = hsv[0];
            fill.setShader(new LinearGradient(sq.left, 0, sq.right, 0, 0xFFFFFFFF, Color.HSVToColor(pure), Shader.TileMode.CLAMP));
            c.drawRoundRect(sq, r, r, fill);
            fill.setShader(dark);
            c.drawRoundRect(sq, r, r, fill);
            fill.setShader(hues);
            c.drawRoundRect(strip, r, r, fill);
            fill.setShader(null);

            // Kreis im Feld, Balken im Streifen; schwarz und weiß umrandet, damit sie überall sichtbar sind
            float x = sq.left + hsv[1] * sq.width();
            float y = sq.top + (1 - hsv[2]) * sq.height();
            float hy = strip.top + (1 - hsv[0] / 360f) * strip.height();
            bar.set(strip.left - 3 * d, hy - 4 * d, strip.right + 3 * d, hy + 4 * d);
            for (int pass = 0; pass < 2; pass++) {
                ring.setStrokeWidth((pass == 0 ? 3 : 2) * d);
                ring.setColor(pass == 0 ? 0xFF000000 : 0xFFFFFFFF);
                c.drawCircle(x, y, 9 * d, ring);
                c.drawRoundRect(bar, 4 * d, 4 * d, ring);
            }
        }

        @SuppressLint("ClickableViewAccessibility")
        @Override
        public boolean onTouchEvent(MotionEvent e) {
            int action = e.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                onStrip = e.getX() > (sq.right + strip.left) / 2;
                getParent().requestDisallowInterceptTouchEvent(true);
                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            } else if (action != MotionEvent.ACTION_MOVE) {
                return true;
            }
            if (onStrip) {
                float h = 360f * (1 - clamp((e.getY() - strip.top) / strip.height()));
                hsv[0] = h >= 360f ? 0 : h; // oben und unten ist beides Rot
            } else {
                hsv[1] = clamp((e.getX() - sq.left) / sq.width());
                hsv[2] = 1 - clamp((e.getY() - sq.top) / sq.height());
            }
            if (onChange != null) onChange.run();
            return true;
        }

        private static float clamp(float v) {
            return Math.max(0, Math.min(1, v));
        }
    }
}
