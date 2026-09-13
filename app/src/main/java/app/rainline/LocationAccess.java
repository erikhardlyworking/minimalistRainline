package app.rainline;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public final class LocationAccess {
    private LocationAccess() {}
    public static boolean foregroundAllowed(Context context) {
        return context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }
    public static boolean backgroundAllowed(Context context) {
        return foregroundAllowed(context) && (Build.VERSION.SDK_INT < 29
                || context.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED);
    }

    /** Called only on the single IO executor. A request lasts at most 15 seconds. */
    @SuppressLint("MissingPermission")
    public static WidgetSettings resolve(Context context, boolean foreground) throws IOException {
        SettingsStore store = new SettingsStore(context);
        WidgetSettings saved = store.lastLocation();
        if (!foregroundAllowed(context)) throw new IOException("Allow location access or choose a fixed place.");
        if (!foreground && !backgroundAllowed(context)) {
            if (saved.hasLocation() && !saved.locationExpired(System.currentTimeMillis())) return saved;
            throw UpdateIssue.LOCATION_STALE.failure("Background location is disabled and the saved position is too old. Open Rainline, enable background location, or choose a fixed place.");
        }
        LocationManager manager = context.getSystemService(LocationManager.class);
        if (manager == null) throw new IOException("Location is unavailable on this device.");
        Location best = null;
        boolean fine = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        try {
            for (String provider : manager.getProviders(true)) {
                if (!fine && LocationManager.GPS_PROVIDER.equals(provider)) continue;
                Location candidate = manager.getLastKnownLocation(provider);
                if (candidate != null && (best == null || candidate.getTime() > best.getTime())) best = candidate;
            }
            // Reuse a recent fix rather than turning on GPS for every refresh.
            if (!foreground && best == null && saved.hasLocation()
                    && System.currentTimeMillis() - saved.locationAt <= 30 * Forecast.MINUTE
                    && !saved.locationExpired(System.currentTimeMillis())) return saved;
            if (best == null || age(best) > (foreground ? 2 : 30) * Forecast.MINUTE) {
                AtomicReference<Location> result = new AtomicReference<>();
                CountDownLatch latch = new CountDownLatch(1);
                LocationListener listener = new LocationListener() {
                    @Override public void onLocationChanged(Location location) { result.set(location); latch.countDown(); }
                    @Override public void onStatusChanged(String provider, int status, Bundle extras) { }
                    @Override public void onProviderEnabled(String provider) { }
                    @Override public void onProviderDisabled(String provider) { }
                };
                try {
                    boolean requested = false;
                    for (String provider : new String[]{LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER}) {
                        if (LocationManager.GPS_PROVIDER.equals(provider) && !fine) continue;
                        if (manager.isProviderEnabled(provider)) {
                            manager.requestSingleUpdate(provider, listener, Looper.getMainLooper());
                            requested = true;
                        }
                    }
                    if (requested) latch.await(15, TimeUnit.SECONDS);
                    if (result.get() != null) best = result.get();
                } finally { manager.removeUpdates(listener); }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Location update interrupted.");
        } catch (SecurityException e) {
            throw new IOException("Location permission changed. Open Rainline to check it.");
        } catch (IllegalArgumentException e) {
            throw new IOException("No location provider is available.");
        }
        if (best == null || age(best) > 30 * Forecast.MINUTE || age(best) < -Forecast.MINUTE) {
            // Background location is throttled by Android. Keep a bounded, already known fix
            // instead of preventing a weather refresh whenever a new fix cannot be obtained.
            if (!foreground && saved.hasLocation() && !saved.locationExpired(System.currentTimeMillis())) return saved;
            throw new IOException("No recent location. Enable device location or choose a fixed place.");
        }
        saved.latitude = best.getLatitude();
        saved.longitude = best.getLongitude();
        saved.locationAt = best.getTime();
        saved.accuracy = best.hasAccuracy() ? best.getAccuracy() : 0;
        store.updateFollowedLocation(saved.latitude, saved.longitude, saved.locationAt, saved.accuracy);
        return saved;
    }
    private static long age(Location location) {
        return TimeUnit.NANOSECONDS.toMillis(android.os.SystemClock.elapsedRealtimeNanos() - location.getElapsedRealtimeNanos());
    }
}
