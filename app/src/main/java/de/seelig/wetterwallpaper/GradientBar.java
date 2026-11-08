package de.seelig.wetterwallpaper;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.view.MotionEvent;
import android.view.View;

/** Leiste mit dem Verlauf und seinen Farbpunkten: Punkt antippen wählt ihn, Ziehen verschiebt ihn. */
final class GradientBar extends View {
    interface Listener {
        void onSelect(int index);

        /** Punkt index soll bei pos (0..1) liegen; danach set() mit der neuen Reihenfolge aufrufen. */
        void onDrag(int index, float pos);
    }

    private final float d;
    private final Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int[] colors = {Color.WHITE, Color.BLACK};
    private float[] pos = {0, 1};
    private float[] soft = {1, 1};
    private int selected;
    private int dragging = -1;
    private Listener listener;

    GradientBar(Context ctx) {
        super(ctx);
        d = getResources().getDisplayMetrics().density;
        ring.setStyle(Paint.Style.STROKE);
        ring.setShadowLayer(2 * d, 0, 0, 0x99000000);
        setContentDescription("Verlauf mit Farbpunkten, zum Verschieben ziehen");
    }

    void set(int[] colors, float[] pos, float[] soft, int selected) {
        this.colors = colors;
        this.pos = pos;
        this.soft = soft;
        this.selected = selected;
        invalidate();
    }

    void setListener(Listener l) {
        listener = l;
    }

    private float left() {
        return 14 * d;
    }

    private float span() {
        return getWidth() - 28 * d;
    }

    private float x(int i) {
        return left() + pos[i] * span();
    }

    @Override
    protected void onDraw(Canvas c) {
        float cy = getHeight() / 2f;
        float h = 9 * d;
        bar.setShader(new LinearGradient(left(), 0, left() + span(), 0, GradientStops.colors(colors),
                GradientStops.stops(pos, soft), Shader.TileMode.CLAMP));
        c.drawRoundRect(left(), cy - h, left() + span(), cy + h, h, h, bar);
        for (int i = 0; i < colors.length; i++) {
            boolean sel = i == selected;
            float r = (sel ? 11 : 8) * d;
            dot.setColor(colors[i]);
            c.drawCircle(x(i), cy, r, dot);
            ring.setColor(sel ? 0xFFF2A007 : Color.WHITE);
            ring.setStrokeWidth((sel ? 3 : 2) * d);
            c.drawCircle(x(i), cy, r, ring);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                dragging = nearest(e.getX());
                if (dragging < 0) return false;
                getParent().requestDisallowInterceptTouchEvent(true);
                if (listener != null) listener.onSelect(dragging);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (dragging < 0 || listener == null) return dragging >= 0;
                listener.onDrag(dragging, Math.max(0, Math.min(1, (e.getX() - left()) / span())));
                dragging = selected; // nach dem Neusortieren liegt der gezogene Punkt evtl. woanders
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                dragging = -1;
                return true;
            default:
                return dragging >= 0;
        }
    }

    /** Punkt unter dem Finger (bis 24 dp daneben); liegen Punkte übereinander, gewinnt der gewählte. -1 = keiner. */
    private int nearest(float tx) {
        int best = -1;
        float bestDist = 24 * d;
        for (int i = 0; i < pos.length; i++) {
            float dist = Math.abs(x(i) - tx);
            boolean closer = best < 0 ? dist <= bestDist
                    : dist < bestDist - 0.5f * d || (dist <= bestDist + 0.5f * d && i == selected);
            if (closer) {
                best = i;
                bestDist = dist;
            }
        }
        return best;
    }
}
