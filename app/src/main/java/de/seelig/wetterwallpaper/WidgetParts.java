package de.seelig.wetterwallpaper;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

import java.util.ArrayList;
import java.util.List;

/**
 * Bausteine der Info-Widgets und ihr Layout: Einträge (items) aus Teilen (parts), nebeneinander (Spalten, Teile
 * untereinander) oder gestapelt (Zeilen, Teile nebeneinander). Gezeichnet wird mit dem Paint des jeweiligen
 * Effekt-Durchgangs, damit Füllung, Kontur, Schatten usw. auch hier gelten. Maße in Vielfachen der Schriftgröße s.
 */
final class WidgetParts {
    private WidgetParts() {
    }

    interface Part {
        float width(Paint p, float s);

        float height(float s);

        void draw(Canvas c, Paint p, float x, float top, float s);
    }

    /** Inhalt eines Widgets: Einträge und Anordnung. */
    static final class Content {
        final List<List<Part>> items = new ArrayList<>();
        final boolean row;

        Content(boolean row) {
            this.row = row;
        }

        Content add(Part... parts) {
            List<Part> l = new ArrayList<>();
            for (Part p : parts) if (p != null) l.add(p);
            if (!l.isEmpty()) items.add(l);
            return this;
        }

        boolean isEmpty() {
            return items.isEmpty();
        }
    }

    // ---------- Teile ----------

    /** Text in scale × Schriftgröße; dim = etwas zurückgenommen (Beschriftungen). */
    static Part text(String t, float scale, boolean dim) {
        return new Part() {
            public float width(Paint p, float s) {
                float old = p.getTextSize();
                p.setTextSize(s * scale);
                float w = p.measureText(t);
                p.setTextSize(old);
                return w;
            }

            public float height(float s) {
                return s * scale * 1.15f;
            }

            public void draw(Canvas c, Paint p, float x, float top, float s) {
                float old = p.getTextSize();
                int alpha = p.getAlpha();
                p.setTextSize(s * scale);
                if (dim) p.setAlpha(Math.round(alpha * 0.75f));
                c.drawText(t, x, top + s * scale * 0.92f, p);
                p.setTextSize(old);
                p.setAlpha(alpha);
            }
        };
    }

    static Part icon(String kind, float scale) {
        return new Part() {
            public float width(Paint p, float s) {
                return WeatherIcon.advance(s * scale) * 0.92f;
            }

            public float height(float s) {
                return s * scale * 1.15f;
            }

            public void draw(Canvas c, Paint p, float x, float top, float s) {
                WeatherIcon.draw(c, kind, x, top + s * scale * 0.92f, s * scale, p);
            }
        };
    }

    /** Säulen 0..1 (Regen), unten bündig. */
    static Part bars(float[] values) {
        return new Part() {
            public float width(Paint p, float s) {
                return values.length * s * 0.36f;
            }

            public float height(float s) {
                return s * 1.5f;
            }

            public void draw(Canvas c, Paint p, float x, float top, float s) {
                Paint q = new Paint(p);
                float bw = s * 0.26f;
                float h = height(s);
                for (int i = 0; i < values.length; i++) {
                    float v = Math.max(0.06f, Math.min(1, values[i]));
                    float l = x + i * s * 0.36f;
                    q.setAlpha(values[i] <= 0.01f ? Math.round(p.getAlpha() * 0.3f) : p.getAlpha());
                    c.drawRoundRect(l, top + h * (1 - v), l + bw, top + h, bw * 0.3f, bw * 0.3f, q);
                }
            }
        };
    }

    /** Sonnenbogen: progress 0..1 des Tages (außerhalb = Nacht, ohne Sonne). */
    static Part arc(float progress) {
        return new Part() {
            public float width(Paint p, float s) {
                return s * 4.4f;
            }

            public float height(float s) {
                return s * 1.9f;
            }

            public void draw(Canvas c, Paint p, float x, float top, float s) {
                Paint q = new Paint(p);
                q.setStyle(Paint.Style.STROKE);
                q.setStrokeCap(Paint.Cap.ROUND);
                q.setStrokeWidth(Math.max(1.5f, s * 0.06f) + (p.getStyle() == Paint.Style.STROKE ? p.getStrokeWidth() : 0));
                float w = width(p, s);
                float h = height(s);
                RectF oval = new RectF(x, top + s * 0.2f, x + w, top + h * 2 - s * 0.2f);
                int a = p.getAlpha();
                q.setAlpha(Math.round(a * 0.35f));
                c.drawArc(oval, 180, 180, false, q);
                if (progress >= 0 && progress <= 1) {
                    q.setAlpha(a);
                    c.drawArc(oval, 180, 180 * progress, false, q);
                    double ang = Math.toRadians(180 + 180 * progress);
                    float cx = oval.centerX() + (float) Math.cos(ang) * oval.width() / 2;
                    float cy = oval.centerY() + (float) Math.sin(ang) * oval.height() / 2;
                    Paint dot = new Paint(p);
                    dot.setStyle(Paint.Style.FILL);
                    c.drawCircle(cx, cy, s * 0.22f, dot);
                }
            }
        };
    }

    /** Mondscheibe in der Phase (0 Neumond, 0.5 Vollmond), zunehmend rechts beleuchtet. */
    static Part moon(double phase) {
        return new Part() {
            public float width(Paint p, float s) {
                return s * 1.7f;
            }

            public float height(float s) {
                return s * 1.7f;
            }

            public void draw(Canvas c, Paint p, float x, float top, float s) {
                float r = s * 0.8f;
                float cx = x + s * 0.85f;
                float cy = top + s * 0.85f;
                Paint q = new Paint(p);
                q.setStyle(Paint.Style.FILL);
                int a = p.getAlpha();
                q.setAlpha(Math.round(a * 0.18f));
                c.drawCircle(cx, cy, r, q);
                q.setAlpha(a);
                RectF circle = new RectF(cx - r, cy - r, cx + r, cy + r);
                float rx = r * (float) Math.abs(Math.cos(2 * Math.PI * phase));
                RectF oval = new RectF(cx - rx, cy - r, cx + rx, cy + r);
                Path lit = new Path();
                lit.moveTo(cx, cy - r);
                boolean waxing = phase < 0.5;
                boolean crescent = phase < 0.25 || phase > 0.75;
                lit.arcTo(circle, -90, waxing ? 180 : -180);
                // Schattengrenze von unten nach oben: bei der Sichel auf der hellen Seite, sonst gegenüber
                boolean viaRight = waxing == crescent;
                lit.arcTo(oval, 90, viaRight ? -180 : 180);
                lit.close();
                if (phase > 0.03 && phase < 0.97) c.drawPath(lit, q);
            }
        };
    }

    /** Ring für Anteile (Akku), 0..1. */
    static Part ring(float fraction) {
        return new Part() {
            public float width(Paint p, float s) {
                return s * 1.6f;
            }

            public float height(float s) {
                return s * 1.6f;
            }

            public void draw(Canvas c, Paint p, float x, float top, float s) {
                Paint q = new Paint(p);
                q.setStyle(Paint.Style.STROKE);
                q.setStrokeCap(Paint.Cap.ROUND);
                q.setStrokeWidth(s * 0.16f + (p.getStyle() == Paint.Style.STROKE ? p.getStrokeWidth() : 0));
                float inset = s * 0.12f;
                RectF o = new RectF(x + inset, top + inset, x + s * 1.6f - inset, top + s * 1.6f - inset);
                int a = p.getAlpha();
                q.setAlpha(Math.round(a * 0.25f));
                c.drawArc(o, 0, 360, false, q);
                q.setAlpha(a);
                c.drawArc(o, -90, 360 * Math.max(0, Math.min(1, fraction)), false, q);
            }
        };
    }

    // ---------- Layout ----------

    private static float itemW(List<Part> item, boolean row, Paint p, float s) {
        float w = 0;
        for (Part part : item) w = row ? Math.max(w, part.width(p, s)) : w + part.width(p, s);
        return row ? w : w + Math.max(0, item.size() - 1) * s * 0.35f;
    }

    private static float itemH(List<Part> item, boolean row, float s) {
        float h = 0;
        for (Part part : item) h = row ? h + part.height(s) : Math.max(h, part.height(s));
        return row ? h + Math.max(0, item.size() - 1) * s * 0.12f : h;
    }

    /** Größe des ganzen Inhalts (Breite, Höhe). */
    static float[] measure(Content content, Paint p, float s) {
        float w = 0;
        float h = 0;
        for (List<Part> item : content.items) {
            float iw = itemW(item, content.row, p, s);
            float ih = itemH(item, content.row, s);
            if (content.row) {
                w += iw;
                h = Math.max(h, ih);
            } else {
                w = Math.max(w, iw);
                h += ih;
            }
        }
        int n = content.items.size();
        if (content.row) w += Math.max(0, n - 1) * s * 0.75f;
        else h += Math.max(0, n - 1) * s * 0.3f;
        return new float[]{w, h};
    }

    /** Zeichnet ab (left, top); align (left/center/right) richtet gestapelte Zeilen innerhalb der Breite aus. */
    static void draw(Canvas c, Content content, Paint p, float s, float left, float top, String align) {
        float[] size = measure(content, p, s);
        float x = left;
        float y = top;
        for (List<Part> item : content.items) {
            float iw = itemW(item, content.row, p, s);
            float ih = itemH(item, content.row, s);
            if (content.row) {
                // Spalte: Teile untereinander, waagrecht mittig, das ganze Item senkrecht mittig
                float py = top + (size[1] - ih) / 2;
                for (Part part : item) {
                    part.draw(c, p, x + (iw - part.width(p, s)) / 2, py, s);
                    py += part.height(s) + s * 0.12f;
                }
                x += iw + s * 0.75f;
            } else {
                // Zeile: Teile nebeneinander, senkrecht mittig zur Zeile
                float px = "right".equals(align) ? left + size[0] - iw : "left".equals(align) ? left : left + (size[0] - iw) / 2;
                for (Part part : item) {
                    part.draw(c, p, px, y + (ih - part.height(s)) / 2, s);
                    px += part.width(p, s) + s * 0.35f;
                }
                y += ih + s * 0.3f;
            }
        }
    }
}
