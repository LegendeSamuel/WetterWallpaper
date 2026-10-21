package de.seelig.wetterwallpaper;

import android.Manifest;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.BatteryManager;
import android.provider.CalendarContract;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Akku und nächster Kalendertermin für die Widgets; beides zwischengespeichert, der Kalender im Hintergrund. */
final class DeviceInfo {
    private static final long BATTERY_MS = 60_000;
    private static final long EVENT_MS = 10 * 60_000;

    private final Context ctx;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private long batteryAt;
    private int battery = -1;
    private boolean charging;
    private volatile String[] event; // {Zeit, Titel} oder null
    private volatile long eventAt;

    DeviceInfo(Context ctx) {
        this.ctx = ctx.getApplicationContext();
    }

    /** Akkustand 0..100, -1 unbekannt. */
    int battery() {
        long now = System.currentTimeMillis();
        if (now - batteryAt > BATTERY_MS) {
            batteryAt = now;
            Intent i = ctx.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED)); // klebrig, kein Empfänger nötig
            if (i != null) {
                int level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
                battery = level < 0 ? -1 : Math.round(level * 100f / scale);
                int status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL;
            }
        }
        return battery;
    }

    boolean charging() {
        return charging;
    }

    static boolean calendarGranted(Context ctx) {
        return ctx.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED;
    }

    /** Nächster Termin der kommenden 7 Tage als {Zeit, Titel}; null ohne Termin oder Berechtigung. want=false: nicht abfragen. */
    String[] nextEvent(boolean want) {
        if (!want) return null;
        long now = System.currentTimeMillis();
        if (now - eventAt > EVENT_MS) {
            eventAt = now;
            io.execute(this::queryEvent);
        }
        return event;
    }

    private void queryEvent() {
        if (!calendarGranted(ctx)) {
            event = null;
            return;
        }
        long now = System.currentTimeMillis();
        Uri.Builder b = CalendarContract.Instances.CONTENT_URI.buildUpon();
        ContentUris.appendId(b, now);
        ContentUris.appendId(b, now + 7 * 86400_000L);
        String[] cols = {CalendarContract.Instances.BEGIN, CalendarContract.Instances.TITLE, CalendarContract.Instances.ALL_DAY};
        try (Cursor c = ctx.getContentResolver().query(b.build(), cols, null, null, CalendarContract.Instances.BEGIN + " ASC")) {
            String[] found = null;
            while (c != null && c.moveToNext()) {
                long begin = c.getLong(0);
                boolean allDay = c.getInt(2) != 0;
                if (begin < now && !allDay) continue; // schon begonnen
                found = new String[]{when(begin, allDay, now), c.getString(1) == null ? "Termin" : c.getString(1)};
                break;
            }
            event = found;
        } catch (RuntimeException e) {
            Log.w("WetterWallpaper", "Kalender nicht lesbar", e);
        }
    }

    /** "14:00" heute, "Fr 14:00" später, "Fr" bei ganztägig. */
    private static String when(long begin, boolean allDay, long now) {
        Calendar a = Calendar.getInstance();
        a.setTimeInMillis(now);
        Calendar e = Calendar.getInstance();
        e.setTimeInMillis(begin);
        boolean today = a.get(Calendar.YEAR) == e.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == e.get(Calendar.DAY_OF_YEAR);
        if (allDay) return today ? "Heute" : new SimpleDateFormat("EEE", Locale.GERMANY).format(new Date(begin));
        return new SimpleDateFormat(today ? "HH:mm" : "EEE HH:mm", Locale.GERMANY).format(new Date(begin));
    }
}
