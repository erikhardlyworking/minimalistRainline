package app.rainline;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.location.Address;
import android.location.Geocoder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public final class SettingsActivity extends Activity {
    private static final int LOCATION_PERMISSION = 20, BACKGROUND_PERMISSION = 21;
    private SettingsStore store;
    private int widgetId;
    private String widgetBeforeRefresh = "";
    private boolean configuring, wasConfigured, saved;
    private ScrollView scroll;
    private LinearLayout content;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean resumed;
    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (!resumed) return;
            showPage();
            WidgetSettings s = store.get(widgetId);
            if (s.automatic && s.hasLocation() && (!s.follow || LocationAccess.foregroundAllowed(SettingsActivity.this)))
                refresh(false);
            handler.postDelayed(this, 60_000);
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        store = new SettingsStore(this);
        configuring = AppWidgetManager.ACTION_APPWIDGET_CONFIGURE.equals(getIntent().getAction());
        if (configuring) setResult(RESULT_CANCELED);
        int requested = getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0);
        widgetId = state != null ? state.getInt("widgetId", requested) : requested;
        int[] ids = Updates.widgetIds(this);
        if (!configuring && widgetId == 0 && state == null && ids.length > 0) widgetId = ids[0];
        if (widgetId > 0) {
            AppWidgetProviderInfo info = AppWidgetManager.getInstance(this).getAppWidgetInfo(widgetId);
            if (info == null || !new ComponentName(this, RainWidgetProvider.class).equals(info.provider)) {
                if (configuring) { finish(); return; }
                widgetId = 0;
            }
        } else if (configuring) { finish(); return; }
        wasConfigured = state != null ? state.getBoolean("wasConfigured") : store.contains(widgetId);
        if (!store.contains(widgetId)) store.put(widgetId, store.get(0));
        showPage();
    }

    @Override protected void onResume() {
        super.onResume();
        if (store == null || isFinishing()) return;
        resumed = true;
        widgetBeforeRefresh = widgetId > 0 ? UpdateDiagnostics.widgetSnapshot(this, widgetId) : "";
        showPage();
        RainWidgetProvider.renderAll(this);
        Updates.schedule(this);
        WidgetSettings settings = store.get(widgetId);
        if (settings.automatic && settings.hasLocation() && (!settings.follow || LocationAccess.foregroundAllowed(this)))
            refresh(false);
        handler.removeCallbacks(ticker);
        handler.postDelayed(ticker, 60_000);
    }
    @Override protected void onPause() {
        resumed = false;
        handler.removeCallbacks(ticker);
        super.onPause();
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putInt("widgetId", widgetId);
        state.putBoolean("wasConfigured", wasConfigured);
        super.onSaveInstanceState(state);
    }
    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (configuring && !wasConfigured && !saved && isFinishing() && store != null) store.delete(widgetId);
        super.onDestroy();
    }

    private void showPage() {
        if (isFinishing() || isDestroyed()) return;
        int scrollY = scroll == null ? 0 : scroll.getScrollY();
        WidgetSettings settings = store.get(widgetId);
        Forecast forecast = RainWidgetProvider.forecast(this, settings);
        scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.BLACK);
        scroll.setFillViewport(true);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24), dp(18), dp(24), dp(24));
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        setContentView(scroll);
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            int top, bottom, left, right;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars()
                        | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
                top = bars.top; bottom = bars.bottom; left = bars.left; right = bars.right;
            } else {
                top = insets.getSystemWindowInsetTop(); bottom = insets.getSystemWindowInsetBottom();
                left = insets.getSystemWindowInsetLeft(); right = insets.getSystemWindowInsetRight();
            }
            view.setPadding(left, top, right, bottom);
            return insets;
        });
        scroll.requestApplyInsets();

        TextView title = text("Rainline", 32);
        title.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        content.addView(title);
        TextView subtitle = text(configuring ? "Configure the widget" : "Widget settings", 14);
        subtitle.setPadding(0, dp(3), 0, dp(22));
        content.addView(subtitle);

        if (!configuring && Updates.widgetIds(this).length > 0) {
            row("Editing", widgetId == 0 ? "Defaults for new widgets" : widgetLabel(widgetId), this::chooseWidget);
            space(12);
        }
        boolean live = ForecastWindow.hasUpcomingData(forecast, System.currentTimeMillis())
                && !settings.locationExpired(System.currentTimeMillis());
        TextView previewLabel = text(live ? "YOUR FORECAST · NEXT TWO HOURS" : "APPEARANCE PREVIEW · SAMPLE RAIN", 10);
        previewLabel.setLetterSpacing(.10f);
        content.addView(previewLabel);
        View preview = new View(this) {
            @Override protected void onDraw(Canvas canvas) {
                long now = System.currentTimeMillis();
                WidgetSettings appearance = store.get(widgetId);
                Forecast current = RainWidgetProvider.forecast(SettingsActivity.this, appearance);
                boolean currentIsLive = ForecastWindow.hasUpcomingData(current, now)
                        && !appearance.locationExpired(now);
                if (!currentIsLive) {
                    current = Forecast.example(now);
                    appearance.follow = false; // Preview has no dependency on location permission.
                }
                ChartRenderer.draw(canvas, getWidth(), getHeight(), getResources().getDisplayMetrics().density,
                        appearance, current, now, ForecastState.DATA);
            }
        };
        preview.setContentDescription(live ? RainWidgetProvider.description(settings, forecast,
                System.currentTimeMillis(), ForecastState.DATA) : "Sample precipitation graph. Appearance preview only.");
        content.addView(preview, new LinearLayout.LayoutParams(-1, dp(146)));
        long shownAt = System.currentTimeMillis();
        ForecastCache.Entry cached = settings.hasLocation() ? new ForecastCache(this).read(
                ForecastWindow.coordinateKey(settings.latitude, settings.longitude)) : null;
        small("Forecast updated: " + ForecastTimes.describe(forecast == null ? 0 : forecast.updatedAt, shownAt)
                + "\nLast checked: " + ForecastTimes.describe(cached == null ? 0 : cached.checkedAt, shownAt));
        button("Open Yr", () -> startActivity(new Intent(this, OpenForecastActivity.class)
                .setAction(OpenForecastActivity.OPEN_YR)), false);
        space(20);

        section("Location");
        row("Location mode", settings.follow ? "Follow my location" : "Use a fixed place", this::chooseLocationMode);
        if (settings.follow) {
            row("Current location", locationLabel(settings), this::locate);
            row("Background location", LocationAccess.backgroundAllowed(this) ? "Status: enabled"
                    : "Status: disabled\nTap to enable following from the home screen", this::backgroundLocation);
            if (!LocationAccess.backgroundAllowed(this))
                small("Without background access, the saved position is used for up to two hours. After that, open Rainline for a fresh position or use a fixed place. A fixed place updates without location permission.");
        } else {
            row("Fixed place", settings.hasLocation() ? locationLabel(settings) : "Choose a place", this::findPlace);
        }
        if (settings.follow && settings.accuracy > 2000)
            small("This location is approximate. Precise location gives a more local rain forecast.");

        section("Appearance");
        row("Rainfall scale and highlight", "Ceiling " + AppearanceDialogs.rateLabel(settings.scaleMax) + " mm/h · "
                + (settings.highlightRain ? "highlight from " + AppearanceDialogs.rateLabel(settings.highlightThreshold) + " mm/h" : "highlight off"), () ->
                AppearanceDialogs.rainfall(this, store.get(widgetId), draft -> {
                    WidgetSettings s = store.get(widgetId);
                    s.scaleMax = draft.scaleMax; s.highlightThreshold = draft.highlightThreshold; s.highlightRain = draft.highlightRain;
                    saveSettings(s);
                }));
        row("Rain highlight colour", ColorValue.format(settings.highlightColor), () ->
                AppearanceDialogs.highlightColour(this, store.get(widgetId), draft -> {
                    WidgetSettings s = store.get(widgetId); s.highlightColor = draft.highlightColor; saveSettings(s);
                }));
        row("Content padding", String.format(Locale.getDefault(), "Left %d · top %d · right %d · bottom %d dp",
                settings.paddingLeft, settings.paddingTop, settings.paddingRight, settings.paddingBottom), () ->
                AppearanceDialogs.padding(this, store.get(widgetId), draft -> {
                    WidgetSettings s = store.get(widgetId);
                    s.paddingLeft = draft.paddingLeft; s.paddingTop = draft.paddingTop;
                    s.paddingRight = draft.paddingRight; s.paddingBottom = draft.paddingBottom;
                    saveSettings(s);
                }));
        row("Background colour", ColorValue.format(settings.backgroundColor), () ->
                AppearanceDialogs.background(this, store.get(widgetId), draft -> {
                    WidgetSettings s = store.get(widgetId); s.backgroundColor = draft.backgroundColor; saveSettings(s);
                }));
        row("Horizontal guides", new String[]{"Light grey", "White", "Hidden"}[settings.guides], () ->
                choices("Horizontal guides", new String[]{"Light grey", "White", "Hidden"}, settings.guides, chosen -> {
                    WidgetSettings s = store.get(widgetId); s.guides = chosen; saveSettings(s);
                }));
        row("Forecast line", settings.normalLine ? "Normal" : "Thin", () ->
                choices("Forecast line", new String[]{"Thin", "Normal"}, settings.normalLine ? 1 : 0, chosen -> {
                    WidgetSettings s = store.get(widgetId); s.normalLine = chosen == 1; saveSettings(s);
                }));
        List<String> axisParts = new ArrayList<>();
        if (settings.showAxis) axisParts.add("Line");
        if (settings.showTicks) axisParts.add("Ticks");
        if (settings.labels) axisParts.add("Numbers · size " + settings.labelSize);
        row("Time axis", axisParts.isEmpty() ? "Hidden" : String.join(" · ", axisParts), () ->
                AppearanceDialogs.timeAxis(this, store.get(widgetId), draft -> {
                    WidgetSettings s = store.get(widgetId);
                    s.labels = draft.labels; s.labelSize = draft.labelSize; s.showAxis = draft.showAxis; s.showTicks = draft.showTicks;
                    saveSettings(s);
                }));
        small("The rainfall scale is 0–" + AppearanceDialogs.rateLabel(settings.scaleMax) + " mm/h. Guides mark "
                + AppearanceDialogs.rateLabel(settings.scaleMax / 3.0) + " and " + AppearanceDialogs.rateLabel(settings.scaleMax * 2.0 / 3)
                + " mm/h; a small upward mark means rain exceeds the scale.");

        section("Updates");
        row("Automatic updates", settings.automatic ? "Status: enabled" : "Status: disabled", () -> {
            WidgetSettings s = store.get(widgetId); s.automatic = !s.automatic; saveSettings(s);
            Updates.schedule(this);
            if (s.automatic) refresh(false);
        });
        row("When rain is forecast", "Every " + settings.rainRefreshMinutes + " minutes", () -> refreshInterval(true));
        row("When no rain is forecast", "Every " + settings.dryRefreshMinutes + " minutes", () -> refreshInterval(false));
        row("Forecast status", status(settings, forecast), () -> new AlertDialog.Builder(this)
                .setTitle("Forecast status").setMessage(status(settings, forecast)
                        + "\n\nA dotted part of the axis has no forecast data. An open ring means waiting for data; × means data could not be obtained."
                        + "\n\nStored forecasts stay visible while they contain any valid intervals in the next 90 minutes. The two-hour axis advances with the current time, leaving missing intervals empty.")
                .setPositiveButton("OK", null).show());
        Button refresh = button(Updates.busy() ? "Updating…" : "Refresh now", () -> refresh(true), false);
        refresh.setEnabled(!Updates.busy());
        small("Rain anywhere in the next two hours uses the rain interval. The slower dry interval needs at least 90 minutes of continuous dry data; missing data uses the rain interval. Intervals start at the last successful server check and also apply after wake or unlock. The graph can advance without downloading data. Updates pause while locked or asleep, and Android may delay them. Refresh now checks sooner while respecting the weather service’s cache.");

        section("About");
        row("Weather data", "MET Norway · CC BY 4.0", () -> new AlertDialog.Builder(this)
                .setTitle("Weather data")
                .setMessage("Based on data from MET Norway, licensed under Creative Commons Attribution 4.0.\n\nRainline draws the precipitation rates as a line, interpolates between samples, and crops the forecast to the next two hours. It is an independent app and is not endorsed by MET Norway, Yr or NRK.")
                .setPositiveButton("Data source", (d, which) -> openLink("https://api.met.no/weatherapi/nowcast/2.0/documentation"))
                .setNeutralButton("Licence", (d, which) -> openLink("https://creativecommons.org/licenses/by/4.0/"))
                .setNegativeButton("Close", null).show());
        row("Privacy", "No ads, analytics or accounts", this::privacy);
        row("Diagnostics", "Preview and copy a local summary", () -> Diagnostics.show(this, widgetId, widgetBeforeRefresh));
        row("Open source", "MIT licence · Version " + BuildConfig.VERSION_NAME, this::aboutSource);
        space(24);
        button(configuring ? "Save widget" : "Add widget", configuring ? this::finishConfiguration : this::pinWidget, true);
        if (!configuring) small("You can also add Rainline from your launcher’s widget picker. Long-press the placed widget to resize it or open its settings where supported.");
        space(8);
        ScrollView current = scroll;
        current.post(() -> current.scrollTo(0, scrollY));
    }

    private String widgetLabel(int id) {
        WidgetSettings s = store.get(id);
        return (s.follow ? "Current location" : s.place.isEmpty() ? "Fixed place" : s.place) + " · widget " + id;
    }
    private void chooseWidget() {
        int[] ids = Updates.widgetIds(this);
        String[] labels = new String[ids.length + 1];
        int checked = ids.length;
        for (int i = 0; i < ids.length; i++) { labels[i] = widgetLabel(ids[i]); if (ids[i] == widgetId) checked = i; }
        labels[ids.length] = "Defaults for new widgets";
        choices("Edit widget", labels, checked, chosen -> {
            widgetId = chosen == ids.length ? 0 : ids[chosen];
            widgetBeforeRefresh = widgetId > 0 ? UpdateDiagnostics.widgetSnapshot(this, widgetId) : "";
            showPage();
        });
    }
    private String locationLabel(WidgetSettings s) {
        if (!s.hasLocation()) return "Tap to get your location";
        String name = s.place.isEmpty() ? "Selected place" : s.place;
        return name + "\n" + String.format(Locale.getDefault(), "%.4f, %.4f", s.latitude, s.longitude)
                + (s.follow ? " · " + time(s.locationAt) : "");
    }
    private String status(WidgetSettings settings, Forecast forecast) {
        String error = store.error(widgetId);
        String details;
        if (!settings.hasLocation()) details = "Choose a location";
        else if (settings.locationExpired(System.currentTimeMillis())) details = "Location is out of date";
        else if (forecast == null) details = "No forecast downloaded yet";
        else if ("no coverage".equals(forecast.coverage)) details = "No radar coverage at this location";
        else if ("temporarily unavailable".equals(forecast.coverage)) details = "Radar temporarily unavailable";
        else if (!forecast.hasRadar()) details = "Precipitation data unavailable";
        else if (!ForecastWindow.hasUpcomingData(forecast, System.currentTimeMillis())) details = "Waiting for data for the next 90 minutes";
        else if (forecast.isStale(System.currentTimeMillis())) details = "Showing stored forecast at the current time";
        else details = "Radar coverage available";
        if (forecast != null) details += "\nForecast issued " + time(forecast.updatedAt);
        if (settings.hasLocation()) {
            ForecastCache.Entry entry = new ForecastCache(this).read(ForecastWindow.coordinateKey(settings.latitude, settings.longitude));
            if (entry != null) details += "\nLast successful check " + time(entry.checkedAt);
            if (entry != null && entry.deprecated && store.issue(widgetId) != UpdateIssue.API_DEPRECATED)
                details += "\nMET is retiring this API version. Check for a Rainline update.";
        }
        if (!error.isEmpty()) details += "\n" + error;
        if (Updates.pending(this, widgetId)) details += "\nRefresh requested";
        return details;
    }
    private String time(long timestamp) {
        if (timestamp == 0) return "never";
        return DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.getDefault())
                .withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(timestamp));
    }
    private void saveSettings(WidgetSettings s) {
        store.put(widgetId, s);
        RainWidgetProvider.render(this, widgetId);
        showPage();
    }
    private interface Choice { void chosen(int index); }
    private void choices(String title, String[] labels, int checked, Choice action) {
        new AlertDialog.Builder(this).setTitle(title).setSingleChoiceItems(labels, checked, (dialog, which) -> {
            dialog.dismiss(); action.chosen(which);
        }).setNegativeButton("Cancel", null).show();
    }
    private void chooseLocationMode() {
        choices("Location mode", new String[]{"Follow my location", "Use a fixed place"}, store.get(widgetId).follow ? 0 : 1, chosen -> {
            if (chosen == 1) { findPlace(); return; }
            WidgetSettings s = store.get(widgetId);
            s.follow = true;
            s.place = "Current location";
            s.latitude = Double.NaN; s.longitude = Double.NaN; s.locationAt = 0;
            store.status(widgetId, "", 0);
            saveSettings(s);
            locate();
        });
    }
    private void locate() {
        if (!LocationAccess.foregroundAllowed(this)) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, LOCATION_PERMISSION);
        } else refresh(true);
    }
    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == LOCATION_PERMISSION) {
            if (LocationAccess.foregroundAllowed(this)) refresh(true);
            else new AlertDialog.Builder(this).setTitle("Choose how to locate your forecast")
                    .setMessage("You can use a fixed place without location permission, or allow location in Android’s app settings.")
                    .setPositiveButton("Fixed place", (d, which) -> findPlace())
                    .setNeutralButton("App settings", (d, which) -> appSettings())
                    .setNegativeButton("Cancel", null).show();
        } else if (requestCode == BACKGROUND_PERMISSION) showPage();
    }
    private void backgroundLocation() {
        if (!LocationAccess.foregroundAllowed(this)) { locate(); return; }
        if (LocationAccess.backgroundAllowed(this)) { appSettings(); return; }
        String option = Build.VERSION.SDK_INT >= 30
                ? getPackageManager().getBackgroundPermissionOptionLabel().toString() : "Allow all the time";
        new AlertDialog.Builder(this).setTitle("Follow your location in the background")
                .setMessage("Rainline uses your location during forecast updates so the widget follows you even when the app is closed.\n\nThe coordinates are sent directly to MET Norway to fetch local weather. Rainline stores the selected location and a small weather cache on this device, with no analytics or tracking service.\n\nChoose “" + option + "” in Android’s Location permission screen. Background access is an option inside Location, not a separate permission in the list.\n\nIf Android no longer shows a permission request, use App settings → Permissions → Location. You can keep using a fixed place without this permission.")
                .setPositiveButton("Continue", (d, which) -> {
                    // Request background access separately, after foreground access. On
                    // Android 11+ PermissionController routes this to the location page.
                    if (Build.VERSION.SDK_INT >= 29)
                        requestPermissions(new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION}, BACKGROUND_PERMISSION);
                    else appSettings();
                }).setNeutralButton("App settings", (d, which) -> appSettings())
                .setNegativeButton("Not now", null).show();
    }
    private void appSettings() {
        startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())));
    }
    private void refreshInterval(boolean rain) {
        int[] intervals = WidgetSettings.REFRESH_INTERVALS;
        String[] labels = new String[intervals.length];
        WidgetSettings current = store.get(widgetId);
        int selected = rain ? current.rainRefreshMinutes : current.dryRefreshMinutes;
        int checked = 0;
        for (int i = 0; i < intervals.length; i++) {
            labels[i] = "Every " + intervals[i] + " minutes";
            if (intervals[i] == selected) checked = i;
        }
        choices(rain ? "When rain is forecast" : "When no rain is forecast", labels, checked, chosen -> {
            WidgetSettings settings = store.get(widgetId);
            if (rain) settings.rainRefreshMinutes = intervals[chosen];
            else settings.dryRefreshMinutes = intervals[chosen];
            saveSettings(settings);
            Updates.schedule(this);
            if (settings.automatic) refresh(false);
        });
    }
    private void refresh(boolean manual) {
        if (Updates.busy()) return;
        WidgetSettings s = store.get(widgetId);
        if (s.follow && !LocationAccess.foregroundAllowed(this)) { locate(); return; }
        if (!s.follow && !s.hasLocation()) { findPlace(); return; }
        Updates.refreshFromSettings(this, widgetId, manual, () -> {
            if (!isFinishing() && !isDestroyed()) showPage();
        });
        showPage();
    }

    private void findPlace() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(Color.LTGRAY);
        input.setHint("Place name or latitude, longitude");
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        input.setPadding(dp(20), dp(16), dp(20), dp(16));
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Choose a fixed place")
                .setView(input).setPositiveButton("Search", null).setNegativeButton("Cancel", null).create();
        dialog.setOnShowListener(ignored -> {
            Button search = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            Runnable runSearch = () -> {
                String query = input.getText().toString().trim();
                if (query.isEmpty()) { input.setError("Enter a place or coordinates"); return; }
                String[] coordinates = query.split("[,;]");
                if (coordinates.length == 2) {
                    try {
                        double lat = Double.parseDouble(coordinates[0].trim());
                        double lon = Double.parseDouble(coordinates[1].trim());
                        if (!ForecastWindow.isCoordinate(lat, lon)) { input.setError("Latitude must be −90 to 90; longitude −180 to 180"); return; }
                        applyPlace("Fixed location", lat, lon);
                        dialog.dismiss();
                        return;
                    } catch (NumberFormatException ignoredNumber) { /* A place such as Oslo, Norway. */ }
                }
                if (!Geocoder.isPresent()) { input.setError("Place search is unavailable. Enter latitude, longitude."); return; }
                search.setEnabled(false); search.setText(R.string.searching);
                Updates.IO.execute(() -> {
                    List<Address> addresses = new ArrayList<>();
                    String error = null;
                    try { addresses = geocode(query); }
                    catch (IOException e) { error = "Place search unavailable. Try latitude, longitude."; }
                    List<Address> found = addresses;
                    String failure = error;
                    runOnUiThread(() -> {
                        if (!dialog.isShowing() || isDestroyed()) return;
                        search.setEnabled(true); search.setText(R.string.search);
                        if (failure != null || found.isEmpty()) {
                            input.setError(failure != null ? failure : "No places found. Try a more specific name or coordinates.");
                            return;
                        }
                        String[] labels = new String[found.size()];
                        for (int i = 0; i < found.size(); i++) labels[i] = addressLabel(found.get(i));
                        new AlertDialog.Builder(this).setTitle("Select a place").setItems(labels, (chooser, index) -> {
                            Address address = found.get(index);
                            applyPlace(labels[index], address.getLatitude(), address.getLongitude());
                            dialog.dismiss();
                        }).setNegativeButton("Cancel", null).show();
                    });
                });
            };
            search.setOnClickListener(v -> runSearch.run());
            input.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_SEARCH && search.isEnabled()) { runSearch.run(); return true; }
                return false;
            });
        });
        dialog.show();
    }
    @SuppressWarnings("deprecation")
    private List<Address> geocode(String query) throws IOException {
        Geocoder geocoder = new Geocoder(getApplicationContext(), Locale.getDefault());
        if (Build.VERSION.SDK_INT < 33) {
            List<Address> result = geocoder.getFromLocationName(query, 5);
            return result == null ? new ArrayList<>() : result;
        }
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<List<Address>> result = new AtomicReference<>();
        geocoder.getFromLocationName(query, 5, new Geocoder.GeocodeListener() {
            @Override public void onGeocode(List<Address> addresses) { result.set(addresses); latch.countDown(); }
            @Override public void onError(String message) { latch.countDown(); }
        });
        try { if (!latch.await(15, TimeUnit.SECONDS)) throw new IOException("Place search timed out"); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException("Search interrupted", e); }
        if (result.get() == null) throw new IOException("Place search failed");
        return result.get();
    }
    private String addressLabel(Address address) {
        String line = address.getMaxAddressLineIndex() >= 0 ? address.getAddressLine(0) : null;
        if (line != null) return line;
        return String.format(Locale.getDefault(), "%.4f, %.4f", address.getLatitude(), address.getLongitude());
    }
    private void applyPlace(String name, double lat, double lon) {
        WidgetSettings settings = store.get(widgetId);
        settings.follow = false;
        settings.latitude = lat; settings.longitude = lon;
        settings.place = name;
        settings.locationAt = 0; settings.accuracy = 0;
        store.status(widgetId, "", 0);
        saveSettings(settings);
        refresh(true);
    }
    private void finishConfiguration() {
        if (!store.get(widgetId).hasLocation()) { locateOrFind(); return; }
        saved = true;
        RainWidgetProvider.render(this, widgetId);
        Updates.schedule(this);
        Updates.enqueue(this);
        setResult(RESULT_OK, new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId));
        finish();
    }
    private void locateOrFind() {
        if (store.get(widgetId).follow) locate(); else findPlace();
        Toast.makeText(this, "Choose a location, then save your widget.", Toast.LENGTH_LONG).show();
    }
    private void pinWidget() {
        WidgetSettings settings = store.get(widgetId);
        if (!settings.hasLocation()) { locateOrFind(); return; }
        AppWidgetManager manager = AppWidgetManager.getInstance(this);
        if (!manager.isRequestPinAppWidgetSupported()) {
            new AlertDialog.Builder(this).setTitle("Add Rainline")
                    .setMessage("Long-press an empty area on your home screen, open Widgets, and choose Rainline.")
                    .setPositiveButton("OK", null).show();
            return;
        }
        store.put(0, settings); // Also used if the launcher invokes the normal configuration flow.
        Intent callback = new Intent(this, PinReceiver.class)
                .setData(Uri.parse("rainline://pin/" + System.currentTimeMillis()))
                .putExtra("settings", settings.toJson());
        // The launcher fills in the new widget ID; this explicit callback must be mutable.
        PendingIntent pending = PendingIntent.getBroadcast(this, 0, callback,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
        manager.requestPinAppWidget(new ComponentName(this, RainWidgetProvider.class), null, pending);
    }
    private void privacy() {
        new AlertDialog.Builder(this).setTitle("Privacy")
                .setMessage("Rainline has no accounts, ads, analytics or tracking SDKs.\n\nForecast requests send coordinates rounded to four decimals directly to api.met.no over HTTPS. MET Norway records IP addresses and request coordinates in its API access logs.\n\nLocation access is optional: you can enter a fixed place or coordinates. Following your location while the app is closed requires optional background location access.\n\nPlace-name searches use Android’s geocoding service. Its provider receives your search text; entering coordinates avoids that search.\n\nSettings and a small cache of recent forecast locations are stored on your device. Rainline excludes these from cloud backup and device transfer. Uninstalling clears them.\n\nDiagnostics are generated locally. Copy places the displayed technical summary on the clipboard; Rainline never uploads it.")
                .setPositiveButton("MET privacy policy", (d, which) -> openLink("https://www.met.no/en/About-us/privacy"))
                .setNegativeButton("Close", null).show();
    }
    private void aboutSource() {
        new AlertDialog.Builder(this).setTitle("Rainline " + BuildConfig.VERSION_NAME)
                .setMessage("Rainline is open-source software under the MIT licence.\n\nCopyright © 2026 erikhardlyworking.\n\n" + LicenseText.MIT)
                .setPositiveButton("Source code", (d, which) -> {
                    if (BuildConfig.SOURCE_URL.startsWith("https://")) openLink(BuildConfig.SOURCE_URL);
                    else new AlertDialog.Builder(this).setTitle("Source code")
                            .setMessage("The complete source code is included alongside this test APK.")
                            .setPositiveButton("OK", null).show();
                }).setNegativeButton("Close", null).show();
    }
    private void openLink(String url) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
        catch (ActivityNotFoundException e) { Toast.makeText(this, "No browser is installed.", Toast.LENGTH_SHORT).show(); }
    }
    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private TextView text(String value, int sp) {
        TextView view = new TextView(this);
        view.setText(value); view.setTextColor(Color.WHITE); view.setTextSize(sp);
        view.setFontFeatureSettings("kern");
        return view;
    }
    private void section(String title) {
        TextView label = text(title.toUpperCase(Locale.ROOT), 11);
        label.setLetterSpacing(.14f);
        label.setPadding(0, dp(26), 0, dp(8));
        if (Build.VERSION.SDK_INT >= 28) label.setAccessibilityHeading(true);
        content.addView(label);
    }
    private void small(String value) {
        TextView view = text(value, 12);
        view.setPadding(0, dp(9), 0, 0);
        view.setLineSpacing(dp(3), 1);
        content.addView(view);
    }
    private void space(int height) { content.addView(new View(this), new LinearLayout.LayoutParams(1, dp(height))); }
    private void row(String title, String detail, Runnable action) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(13), 0, dp(13));
        row.setMinimumHeight(dp(64));
        row.setClickable(true); row.setFocusable(true);
        android.util.TypedValue background = new android.util.TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, background, true);
        row.setBackgroundResource(background.resourceId);
        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        TextView name = text(title, 16);
        labels.addView(name);
        TextView value = text(detail, 12);
        value.setPadding(0, dp(4), 0, 0);
        value.setLineSpacing(dp(2), 1);
        labels.addView(value);
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        TextView chevron = text("›", 22);
        chevron.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        chevron.setPadding(dp(12), 0, 0, 0);
        row.addView(chevron);
        row.setContentDescription(title + ". " + detail);
        row.setOnClickListener(v -> action.run());
        content.addView(row, new LinearLayout.LayoutParams(-1, -2));
        View divider = new View(this); divider.setBackgroundColor(Color.WHITE);
        content.addView(divider, new LinearLayout.LayoutParams(-1, 1));
    }
    private Button button(String label, Runnable action, boolean primary) {
        Button button = new Button(this);
        button.setText(label); button.setTextSize(15); button.setAllCaps(false);
        button.setTextColor(primary ? Color.BLACK : Color.WHITE);
        button.setMinimumHeight(dp(52));
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(primary ? Color.WHITE : Color.BLACK);
        shape.setCornerRadius(dp(8));
        shape.setStroke(dp(1), Color.WHITE);
        button.setBackground(shape);
        button.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(52));
        params.topMargin = dp(14);
        content.addView(button, params);
        return button;
    }
}
