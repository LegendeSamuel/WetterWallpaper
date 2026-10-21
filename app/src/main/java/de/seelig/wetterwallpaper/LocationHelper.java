package de.seelig.wetterwallpaper;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.util.Log;

import java.util.function.Consumer;

/** Grober Standort des Handys für Sonnen-/Mondstand und Wetter. */
final class LocationHelper {
    private static final String TAG = "WetterWallpaper";

    private LocationHelper() {
    }

    static boolean granted(Context ctx) {
        return ctx.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    /** Letzter bekannter Standort; fehlt er, wird einmal aktiv nachgefragt und das Ergebnis an onFresh gegeben. */
    static Location lastKnown(Context ctx, Consumer<Location> onFresh) {
        if (!granted(ctx)) return null;
        try {
            LocationManager lm = ctx.getSystemService(LocationManager.class);
            Location best = null;
            for (String p : lm.getProviders(true)) {
                Location l = lm.getLastKnownLocation(p);
                if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
            }
            if (best == null && onFresh != null && Build.VERSION.SDK_INT >= 30
                    && lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                lm.getCurrentLocation(LocationManager.NETWORK_PROVIDER, null, ctx.getMainExecutor(), l -> {
                    if (l != null) onFresh.accept(l);
                });
            }
            return best;
        } catch (SecurityException e) {
            Log.w(TAG, "Standort nicht verfügbar", e);
            return null;
        }
    }
}
