package app.rainline;

import android.content.Context;
import android.content.SharedPreferences;

public final class SettingsStore {
    private final SharedPreferences prefs;
    public SettingsStore(Context context) {
        prefs = context.getSharedPreferences("widgets", Context.MODE_PRIVATE);
    }
    public WidgetSettings get(int id) {
        String defaults = prefs.getString("widget.0", "{}");
        return WidgetSettings.fromJson(prefs.getString("widget." + id, defaults));
    }
    public boolean contains(int id) { return prefs.contains("widget." + id); }
    public void put(int id, WidgetSettings settings) {
        prefs.edit().putString("widget." + id, settings.toJson()).apply();
    }
    public void delete(int id) {
        prefs.edit().remove("widget." + id).remove("error." + id).remove("issue." + id).remove("attempt." + id).apply();
    }
    public void status(int id, String error, long attemptedAt) {
        status(id, error, error.isEmpty() ? UpdateIssue.NONE : UpdateIssue.UNKNOWN_ERROR, attemptedAt);
    }
    public void status(int id, String error, UpdateIssue issue, long attemptedAt) {
        prefs.edit().putString("error." + id, error).putString("issue." + id, issue.name()).putLong("attempt." + id, attemptedAt).apply();
    }
    public UpdateIssue issue(int id) {
        try { return UpdateIssue.valueOf(prefs.getString("issue." + id, error(id).isEmpty() ? "NONE" : "UNKNOWN_ERROR")); }
        catch (IllegalArgumentException e) { return UpdateIssue.UNKNOWN_ERROR; }
    }
    public String error(int id) { return prefs.getString("error." + id, ""); }
    public long attemptedAt(int id) { return prefs.getLong("attempt." + id, 0); }
    public void updateFollowedLocation(double lat, double lon, long time, float accuracy) {
        prefs.edit().putString("last.location", lat + "," + lon)
                .putLong("last.location.time", time).putFloat("last.location.accuracy", accuracy).apply();
    }
    public WidgetSettings lastLocation() {
        WidgetSettings s = new WidgetSettings();
        String[] parts = prefs.getString("last.location", "").split(",");
        if (parts.length == 2) {
            try { s.latitude = Double.parseDouble(parts[0]); s.longitude = Double.parseDouble(parts[1]); }
            catch (NumberFormatException ignored) { }
        }
        s.locationAt = prefs.getLong("last.location.time", 0);
        s.accuracy = prefs.getFloat("last.location.accuracy", 0);
        return s;
    }
}
