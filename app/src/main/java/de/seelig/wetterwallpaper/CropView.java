package de.seelig.wetterwallpaper;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

/** Bild im Bildschirmformat-Rahmen ziehen und zoomen; das Bild deckt den Rahmen immer ganz ab. */
final class CropView extends View {
    private static final float MAX_ZOOM = 6f;

    private final float aspect; // Breite / Höhe des Handybildschirms
    private final Matrix matrix = new Matrix();
    private final RectF frame = new RectF();
    private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint dim = new Paint();
    private final Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ScaleGestureDetector scaler;
    private final GestureDetector panner;
    private final Paint guide = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Bitmap bitmap;
    private RectF pendingCrop;
    private float guideSizePct;
    private float guideXPct;
    private float guideYPct;

    CropView(Context ctx, float aspect) {
        super(ctx);
        this.aspect = aspect;
        dim.setColor(0xB0000000);
        border.setStyle(Paint.Style.STROKE);
        border.setStrokeWidth(3 * getResources().getDisplayMetrics().density);
        border.setColor(0xFFFFFFFF);
        scaler = new ScaleGestureDetector(ctx, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector d) {
                matrix.postScale(d.getScaleFactor(), d.getScaleFactor(), d.getFocusX(), d.getFocusY());
                constrain();
                return true;
            }
        });
        panner = new GestureDetector(ctx, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float dx, float dy) {
                matrix.postTranslate(-dx, -dy);
                constrain();
                return true;
            }
        });
        setContentDescription("Bildausschnitt: mit zwei Fingern zoomen, mit einem Finger verschieben");
    }

    /** Umriss der Uhr im Rahmen, damit man beim Zuschneiden sieht, wo sie landet. */
    void setClockGuide(android.graphics.Typeface typeface, float sizePct, float xPct, float yPct) {
        guide.setTypeface(typeface);
        guide.setTextAlign(Paint.Align.CENTER);
        guideSizePct = sizePct;
        guideXPct = xPct;
        guideYPct = yPct;
        invalidate();
    }

    /** initialCrop in Bildpixeln oder null für mittig. */
    void setImage(Bitmap b, RectF initialCrop) {
        bitmap = b;
        pendingCrop = initialCrop;
        if (!frame.isEmpty()) resetMatrix();
        invalidate();
    }

    /** Sichtbarer Ausschnitt in Bildpixeln. */
    RectF cropRect() {
        Matrix inverse = new Matrix();
        matrix.invert(inverse);
        RectF r = new RectF(frame);
        inverse.mapRect(r);
        return r;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        float margin = 24 * getResources().getDisplayMetrics().density;
        float fw = w - 2 * margin;
        float fh = h - 2 * margin;
        if (fw / fh > aspect) fw = fh * aspect; else fh = fw / aspect;
        frame.set((w - fw) / 2, (h - fh) / 2, (w + fw) / 2, (h + fh) / 2);
        if (bitmap != null) resetMatrix();
    }

    private void resetMatrix() {
        RectF src = pendingCrop != null ? pendingCrop : centerCrop();
        matrix.setRectToRect(src, frame, Matrix.ScaleToFit.FILL);
        constrain();
    }

    private RectF centerCrop() {
        float w = bitmap.getWidth();
        float h = bitmap.getHeight();
        float cw = w;
        float ch = h;
        if (w / h > aspect) cw = h * aspect; else ch = w / aspect;
        return new RectF((w - cw) / 2, (h - ch) / 2, (w + cw) / 2, (h + ch) / 2);
    }

    /** Zoom begrenzen und so verschieben, dass keine Lücke im Rahmen entsteht. */
    private void constrain() {
        if (bitmap == null) return;
        float minScale = Math.max(frame.width() / bitmap.getWidth(), frame.height() / bitmap.getHeight());
        float scale = currentScale();
        float target = Math.max(minScale, Math.min(minScale * MAX_ZOOM, scale));
        if (target != scale) matrix.postScale(target / scale, target / scale, frame.centerX(), frame.centerY());
        RectF img = new RectF(0, 0, bitmap.getWidth(), bitmap.getHeight());
        matrix.mapRect(img);
        float dx = 0;
        float dy = 0;
        if (img.left > frame.left) dx = frame.left - img.left;
        if (img.right < frame.right) dx = frame.right - img.right;
        if (img.top > frame.top) dy = frame.top - img.top;
        if (img.bottom < frame.bottom) dy = frame.bottom - img.bottom;
        matrix.postTranslate(dx, dy);
        invalidate();
    }

    private float currentScale() {
        float[] v = new float[9];
        matrix.getValues(v);
        return v[Matrix.MSCALE_X];
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (!isEnabled()) return false; // während der Berechnung festhalten
        scaler.onTouchEvent(e);
        panner.onTouchEvent(e);
        return true;
    }

    @Override
    protected void onDraw(Canvas c) {
        if (bitmap == null) return;
        c.drawBitmap(bitmap, matrix, bitmapPaint);
        c.drawRect(0, 0, getWidth(), frame.top, dim);
        c.drawRect(0, frame.bottom, getWidth(), getHeight(), dim);
        c.drawRect(0, frame.top, frame.left, frame.bottom, dim);
        c.drawRect(frame.right, frame.top, getWidth(), frame.bottom, dim);
        c.drawRect(frame, border);
        if (guideSizePct > 0) {
            float x = frame.left + frame.width() * guideXPct / 100;
            float y = frame.top + frame.height() * guideYPct / 100;
            guide.setTextSize(frame.width() * guideSizePct / 100);
            guide.setStyle(Paint.Style.FILL);
            guide.setColor(0x33FFFFFF);
            c.drawText("12:45", x, y, guide);
            guide.setStyle(Paint.Style.STROKE);
            guide.setStrokeWidth(2 * getResources().getDisplayMetrics().density);
            guide.setColor(0xCCFFFFFF);
            c.drawText("12:45", x, y, guide);
        }
    }
}
