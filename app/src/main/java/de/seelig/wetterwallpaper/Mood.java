package de.seelig.wetterwallpaper;

import android.graphics.Color;
import android.graphics.ColorMatrix;

/** Licht- und Farbstimmung für eine Sonnenhöhe, interpoliert zwischen festen Stützpunkten. */
final class Mood {
    final float elevation;
    final int skyTop;
    final int skyHorizon;
    /** Wie stark der echte Foto-Himmel über dem gerenderten liegt (0..1). */
    final float photoSky;
    final float brightness;
    final float saturation;
    final int tint;
    final float tintStrength;
    final int haze;
    final float hazeAlpha;
    final float stars;
    final int glow;
    final float glowAlpha;
    /** 0 = Tag, 1 = Nacht, z. B. für die Uhrfarbe. */
    final float night;

    Mood(float elevation, int skyTop, int skyHorizon, float photoSky, float brightness, float saturation,
         int tint, float tintStrength, int haze, float hazeAlpha, float stars, int glow, float glowAlpha,
         float night) {
        this.elevation = elevation;
        this.skyTop = skyTop;
        this.skyHorizon = skyHorizon;
        this.photoSky = photoSky;
        this.brightness = brightness;
        this.saturation = saturation;
        this.tint = tint;
        this.tintStrength = tintStrength;
        this.haze = haze;
        this.hazeAlpha = hazeAlpha;
        this.stars = stars;
        this.glow = glow;
        this.glowAlpha = glowAlpha;
        this.night = night;
    }

    // Stützpunkte nach Sonnenhöhe in Grad, aufsteigend
    private static final Mood[] KEYS = {
            new Mood(-18, 0xFF050816, 0xFF0D1630, 0f, 0.28f, 0.45f, 0xFF6F86C8, 0.60f, 0xFF0B1430, 0.35f, 1.00f, 0xFF000000, 0f, 1f),
            new Mood(-8, 0xFF0F1A40, 0xFF3B4A86, 0f, 0.45f, 0.60f, 0xFF7D8FD6, 0.50f, 0xFF2C3A70, 0.45f, 0.50f, 0xFFFF9A6B, 0.15f, 0.8f),
            new Mood(-2, 0xFF2A3570, 0xFFFF8C5A, 0.15f, 0.65f, 0.90f, 0xFFFF9E70, 0.35f, 0xFFC77A7A, 0.40f, 0.05f, 0xFFFF7A40, 0.70f, 0.4f),
            new Mood(3, 0xFF4A78C0, 0xFFFFC07A, 0.50f, 0.90f, 1.10f, 0xFFFFC28A, 0.30f, 0xFFF0B890, 0.30f, 0f, 0xFFFFD27A, 0.60f, 0f),
            new Mood(12, 0xFF3A7BD5, 0xFFCFE6F5, 1f, 1f, 1f, 0xFFFFFFFF, 0f, 0xFFDFEAF5, 0.10f, 0f, 0xFFFFF0C0, 0.15f, 0f),
    };

    static Mood forElevation(double elevation) {
        if (elevation <= KEYS[0].elevation) return KEYS[0];
        for (int i = 1; i < KEYS.length; i++) {
            Mood b = KEYS[i];
            if (elevation <= b.elevation) {
                Mood a = KEYS[i - 1];
                return lerp(a, b, (float) ((elevation - a.elevation) / (b.elevation - a.elevation)));
            }
        }
        return KEYS[KEYS.length - 1];
    }

    private static Mood lerp(Mood a, Mood b, float t) {
        return new Mood(f(a.elevation, b.elevation, t), c(a.skyTop, b.skyTop, t), c(a.skyHorizon, b.skyHorizon, t),
                f(a.photoSky, b.photoSky, t), f(a.brightness, b.brightness, t), f(a.saturation, b.saturation, t),
                c(a.tint, b.tint, t), f(a.tintStrength, b.tintStrength, t), c(a.haze, b.haze, t),
                f(a.hazeAlpha, b.hazeAlpha, t), f(a.stars, b.stars, t), c(a.glow, b.glow, t),
                f(a.glowAlpha, b.glowAlpha, t), f(a.night, b.night, t));
    }

    static float f(float a, float b, float t) {
        return a + (b - a) * t;
    }

    static int c(int a, int b, float t) {
        return Color.argb(Math.round(f(Color.alpha(a), Color.alpha(b), t)), Math.round(f(Color.red(a), Color.red(b), t)),
                Math.round(f(Color.green(a), Color.green(b), t)), Math.round(f(Color.blue(a), Color.blue(b), t)));
    }

    /** Grau einer geschlossenen Wolkendecke bei diesem Licht. */
    int overcast() {
        float f = 0.15f + 0.85f * Math.min(1f, brightness);
        return Color.rgb(Math.round(0xA4 * f), Math.round(0xAD * f), Math.round(0xB8 * f));
    }

    /**
     * Farbmatrix fürs Foto: Sättigung, dann Helligkeit mit Farbstich.
     * extraLight z. B. +Mondlicht oder -Wolken, saturationFactor z. B. < 1 bei trübem Wetter.
     * darkness: wie viel der natürlichen Abdunklung ankommt (1 = realistisch, kleiner = Motiv bleibt sichtbar).
     */
    ColorMatrix grade(float extraLight, float saturationFactor, float darkness) {
        ColorMatrix m = new ColorMatrix();
        m.setSaturation(saturation * saturationFactor);
        float k = tintStrength;
        float b = 1 - (1 - brightness * (1 + extraLight)) * darkness;
        ColorMatrix scale = new ColorMatrix();
        scale.setScale(b * (1 - k + k * Color.red(tint) / 255f),
                b * (1 - k + k * Color.green(tint) / 255f),
                b * (1 - k + k * Color.blue(tint) / 255f), 1f);
        m.postConcat(scale);
        return m;
    }
}
