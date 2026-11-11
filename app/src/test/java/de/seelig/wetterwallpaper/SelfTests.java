package de.seelig.wetterwallpaper;

import org.junit.Test;

/**
 * Die Selbsttests der reinen Rechenklassen als JUnit-Tests. Die Prüfungen bleiben in den main-Methoden
 * (der PC-Harness in tools/pc_harness und {@code java Klasse.java} nutzen sie weiter); hier werden sie
 * nur aufgerufen. Schlägt eine Prüfung fehl, wirft main einen AssertionError und der Test scheitert.
 */
public class SelfTests {
    private static final String[] NO_ARGS = {};

    @Test public void dayNight() throws Exception { DayNight.main(NO_ARGS); }
    @Test public void edgeRefine() throws Exception { EdgeRefine.main(NO_ARGS); }
    @Test public void fontFamilies() throws Exception { FontFamilies.main(NO_ARGS); }
    @Test public void frost() throws Exception { Frost.main(NO_ARGS); }
    @Test public void gradientStops() throws Exception { GradientStops.main(NO_ARGS); }
    @Test public void guides() throws Exception { Guides.main(NO_ARGS); }
    @Test public void photoColors() throws Exception { PhotoColors.main(NO_ARGS); }
    @Test public void presets() throws Exception { Presets.main(NO_ARGS); }
    @Test public void rainOnGlass() throws Exception { RainOnGlass.main(NO_ARGS); }
    @Test public void skyMask() throws Exception { SkyMask.main(NO_ARGS); }
    @Test public void skyPosition() throws Exception { SkyPosition.main(NO_ARGS); }
    @Test public void skyTimes() throws Exception { SkyTimes.main(NO_ARGS); }
    @Test public void vegetation() throws Exception { Vegetation.main(NO_ARGS); }
    @Test public void weather() throws Exception { Weather.main(NO_ARGS); }
}
