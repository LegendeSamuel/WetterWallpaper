package de.seelig.wetterwallpaper;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.RecordingCanvas;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;

import com.google.android.material.color.MaterialColors;

/**
 * Milchglas-Fläche über der Vorschau: zeichnet den Ausschnitt der Vorschau hinter sich weichgezeichnet
 * (PreviewView.backdrop(), kleines Abbild) und darüber eine halbtransparente Tönung mit feiner Kante.
 */
public final class GlassCard extends FrameLayout {
    private final RenderNode node = new RenderNode("glass");
    private final Paint image = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint tint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int[] here = new int[2];
    private final int[] there = new int[2];
    private final float blur;
    private float radius;
    private PreviewView source;

    public GlassCard(Context ctx, AttributeSet attrs) {
        super(ctx, attrs);
        float d = getResources().getDisplayMetrics().density;
        blur = 22 * d;
        radius = 20 * d; // radius-xl der Seelig-Designs-Panels
        setWillNotDraw(false);
        int surface = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurfaceContainer);
        tint.setColor((surface & 0xFFFFFF) | 0x8C000000); // ~55 % deckend: Bild bleibt erkennbar, Text lesbar
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(d);
        edge.setColor(0x2EFFFFFF);
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), radius);
            }
        });
        setClipToOutline(true);
    }

    /** Vorschau, deren Abbild hinter dem Glas erscheint. */
    void setSource(PreviewView source) {
        this.source = source;
        invalidate();
    }

    void setRadius(float px) {
        radius = px;
        invalidateOutline();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        Bitmap b = source != null ? source.backdrop() : null;
        int w = getWidth();
        int h = getHeight();
        // Unschärfe per RenderEffect erst ab Android 12; davor nur die Tönung
        if (b != null && c.isHardwareAccelerated() && source.getWidth() > 0
                && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            getLocationInWindow(here);
            source.getLocationInWindow(there);
            float scale = source.getWidth() / (float) b.getWidth();
            node.setPosition(0, 0, w, h);
            node.setRenderEffect(RenderEffect.createBlurEffect(blur, blur, Shader.TileMode.CLAMP));
            RecordingCanvas rc = node.beginRecording();
            rc.translate(there[0] - here[0], there[1] - here[1]);
            rc.scale(scale, scale);
            rc.drawBitmap(b, 0, 0, image);
            node.endRecording();
            c.drawRenderNode(node);
        }
        c.drawRoundRect(0, 0, w, h, radius, radius, tint);
        c.drawRoundRect(0.5f, 0.5f, w - 0.5f, h - 0.5f, radius, radius, edge);
    }
}
