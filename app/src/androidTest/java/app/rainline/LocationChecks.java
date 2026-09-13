package app.rainline;

import android.Manifest;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

/** Reproduces background-location-disabled behaviour without changing device permissions. */
final class LocationChecks {
    static void run(Context context) throws Exception {
        if (Build.VERSION.SDK_INT < 29) return;
        Context isolated = new ContextWrapper(context) {
            @Override public SharedPreferences getSharedPreferences(String name, int mode) {
                return super.getSharedPreferences("location-checks-" + name, mode);
            }
            @Override public int checkSelfPermission(String permission) {
                return Manifest.permission.ACCESS_BACKGROUND_LOCATION.equals(permission)
                        ? PackageManager.PERMISSION_DENIED : PackageManager.PERMISSION_GRANTED;
            }
        };
        try {
            SettingsStore store = new SettingsStore(isolated);
            long now = System.currentTimeMillis();
            store.updateFollowedLocation(60, 10, now - 90 * Forecast.MINUTE, 100);
            WidgetSettings recent = LocationAccess.resolve(isolated, false);
            check(recent.hasLocation() && recent.locationAt == now - 90 * Forecast.MINUTE,
                    "Recent saved position should work without background permission and retain its real age");
            store.updateFollowedLocation(60, 10, now - 121 * Forecast.MINUTE, 100);
            try {
                LocationAccess.resolve(isolated, false);
                throw new AssertionError("An old position must not silently be treated as current");
            } catch (UpdateIssue.Failure e) {
                check(e.issue == UpdateIssue.LOCATION_STALE, "Background permission issue should have a clear category");
                check(e.getMessage().contains("Background location is disabled"), "Permission explanation missing");
            }
        } finally { context.deleteSharedPreferences("location-checks-widgets"); }
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
