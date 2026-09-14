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
        TextView subtitle = text(configuring ? getString(R.string.configure_widget) : getString(R.string.widget_settings), 14);
        subtitle.setPadding(0, dp(3), 0, dp(22));
        content.addView(subtitle);

        if (!configuring && Updates.widgetIds(this).length > 0) {
            row(getString(R.string.editing), widgetId == 0 ? getString(R.string.widget_defaults) : widgetLabel(widgetId), this::chooseWidget);
            space(12);
        }
        boolean live = ForecastWindow.hasUpcomingData(forecast, System.currentTimeMillis())
                && !settings.locationExpired(System.currentTimeMillis());
        TextView previewLabel = text(live ? getString(R.string.preview_live) : getString(R.string.preview_sample), 10);
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
        preview.setContentDescription(live ? RainWidgetProvider.description(this, settings, forecast,
                System.currentTimeMillis(), ForecastState.DATA) : getString(R.string.preview_description));
        content.addView(preview, new LinearLayout.LayoutParams(-1, dp(146)));
        long shownAt = System.currentTimeMillis();
        ForecastCache.Entry cached = settings.hasLocation() ? new ForecastCache(this).read(
                ForecastWindow.coordinateKey(settings.latitude, settings.longitude)) : null;
        small(getString(R.string.forecast_times, UiText.describe(this, forecast == null ? 0 : forecast.updatedAt, shownAt),
                UiText.describe(this, cached == null ? 0 : cached.checkedAt, shownAt)));
        button(getString(R.string.open_yr), () -> startActivity(new Intent(this, OpenForecastActivity.class)
                .setAction(OpenForecastActivity.OPEN_YR)), false);
        space(20);

        section(getString(R.string.location));
        row(getString(R.string.location_mode), settings.follow ? getString(R.string.follow_location) : getString(R.string.fixed_place_mode), this::chooseLocationMode);
        if (settings.follow) {
            row(getString(R.string.current_location), locationLabel(settings), this::locate);
            row(getString(R.string.background_location), LocationAccess.backgroundAllowed(this) ? getString(R.string.enabled)
                    : getString(R.string.background_disabled), this::backgroundLocation);
            if (!LocationAccess.backgroundAllowed(this))
                small(getString(R.string.background_limit));
        } else {
            row(getString(R.string.fixed_place), settings.hasLocation() ? locationLabel(settings) : getString(R.string.choose_place), this::findPlace);
        }
        if (settings.follow && settings.accuracy > 2000)
            small(getString(R.string.approximate_location));

        section(getString(R.string.appearance));
        row(getString(R.string.rainfall_scale), getString(R.string.rainfall_summary, UiText.rate(this, settings.scaleMax),
                settings.highlightRain ? getString(R.string.highlight_from, UiText.rate(this, settings.highlightThreshold)) : getString(R.string.highlight_off)), () ->
                AppearanceDialogs.rainfall(this, store.get(widgetId), draft -> {
                    WidgetSettings s = store.get(widgetId);
                    s.scaleMax = draft.scaleMax; s.highlightThreshold = draft.highlightThreshold; s.highlightRain = draft.highlightRain;
                    saveSettings(s);
                }));
        row(getString(R.string.highlight_colour), ColorValue.format(settings.highlightColor), () ->
                AppearanceDialogs.highlightColour(this, store.get(widgetId), draft -> {
                    WidgetSettings s = store.get(widgetId); s.highlightColor = draft.highlightColor; saveSettings(s);
                }));
        row(getString(R.string.content_padding), getString(R.string.padding_summary,
                settings.paddingLeft, settings.paddingTop, settings.paddingRight, settings.paddingBottom), () ->
                AppearanceDialogs.padding(this, store.get(widgetId), draft -> {
                    WidgetSettings s = store.get(widgetId);
                    s.paddingLeft = draft.paddingLeft; s.paddingTop = draft.paddingTop;
                    s.paddingRight = draft.paddingRight; s.paddingBottom = draft.paddingBottom;
                    saveSettings(s);
                }));
        row(getString(R.string.background_colour), ColorValue.format(settings.backgroundColor), () ->
                AppearanceDialogs.background(this, store.get(widgetId), draft -> {
                    WidgetSettings s = store.get(widgetId); s.backgroundColor = draft.backgroundColor; saveSettings(s);
                }));
        row(getString(R.string.horizontal_guides), new String[]{getString(R.string.light_grey), getString(R.string.white), getString(R.string.hidden)}[settings.guides], () ->
                choices(getString(R.string.horizontal_guides), new String[]{getString(R.string.light_grey), getString(R.string.white), getString(R.string.hidden)}, settings.guides, chosen -> {
                    WidgetSettings s = store.get(widgetId); s.guides = chosen; saveSettings(s);
                }));
        row(getString(R.string.forecast_line), settings.normalLine ? getString(R.string.normal) : getString(R.string.thin), () ->
                choices(getString(R.string.forecast_line), new String[]{getString(R.string.thin), getString(R.string.normal)}, settings.normalLine ? 1 : 0, chosen -> {
                    WidgetSettings s = store.get(widgetId); s.normalLine = chosen == 1; saveSettings(s);
                }));
        List<String> axisParts = new ArrayList<>();
        if (settings.showAxis) axisParts.add(getString(R.string.line));
        if (settings.showTicks) axisParts.add(getString(R.string.ticks));
        if (settings.labels) axisParts.add(getString(R.string.numbers_size, settings.labelSize));
        row(getString(R.string.time_axis), axisParts.isEmpty() ? getString(R.string.hidden) : String.join(" · ", axisParts), () ->
                AppearanceDialogs.timeAxis(this, store.get(widgetId), draft -> {
                    WidgetSettings s = store.get(widgetId);
                    s.labels = draft.labels; s.labelSize = draft.labelSize; s.showAxis = draft.showAxis; s.showTicks = draft.showTicks;
                    saveSettings(s);
                }));
        small(getString(R.string.scale_help, UiText.rate(this, settings.scaleMax),
                UiText.rate(this, settings.scaleMax / 3.0), UiText.rate(this, settings.scaleMax * 2.0 / 3)));

        section(getString(R.string.updates));
        row(getString(R.string.automatic_updates), settings.automatic ? getString(R.string.enabled) : getString(R.string.disabled), () -> {
            WidgetSettings s = store.get(widgetId); s.automatic = !s.automatic; saveSettings(s);
            Updates.schedule(this);
            if (s.automatic) refresh(false);
        });
        row(getString(R.string.rain_interval), getString(R.string.every_minutes, settings.rainRefreshMinutes), () -> refreshInterval(true));
        row(getString(R.string.dry_interval), getString(R.string.every_minutes, settings.dryRefreshMinutes), () -> refreshInterval(false));
        row(getString(R.string.forecast_status), status(settings, forecast), () -> new AlertDialog.Builder(this)
                .setTitle(getString(R.string.forecast_status)).setMessage(status(settings, forecast)
                        + "\n\n" + getString(R.string.status_help))
                .setPositiveButton(getString(R.string.ok), null).show());
        Button refresh = button(Updates.busy() ? getString(R.string.updating) : getString(R.string.refresh_now), () -> refresh(true), false);
        refresh.setEnabled(!Updates.busy());
        small(getString(R.string.updates_help));

        section(getString(R.string.about));
        if (Build.VERSION.SDK_INT >= 33) {
            boolean systemLanguage = getSystemService(android.app.LocaleManager.class).getApplicationLocales().isEmpty();
            row(getString(R.string.language), systemLanguage ? getString(R.string.system_language)
                    : UiText.locale(this).getDisplayName(UiText.locale(this)), () ->
                    startActivity(new Intent(Settings.ACTION_APP_LOCALE_SETTINGS,
                            Uri.parse("package:" + getPackageName()))));
        }
        row(getString(R.string.weather_data), "MET Norway · CC BY 4.0", () -> new AlertDialog.Builder(this)
                .setTitle(getString(R.string.weather_data))
                .setMessage(getString(R.string.attribution))
                .setPositiveButton(getString(R.string.data_source), (d, which) -> openLink("https://api.met.no/weatherapi/nowcast/2.0/documentation"))
                .setNeutralButton(getString(R.string.licence), (d, which) -> openLink("https://creativecommons.org/licenses/by/4.0/"))
                .setNegativeButton(getString(R.string.close), null).show());
        row(getString(R.string.privacy), getString(R.string.privacy_summary), this::privacy);
        row(getString(R.string.diagnostics), getString(R.string.diagnostics_summary), () -> Diagnostics.show(this, widgetId, widgetBeforeRefresh));
        row(getString(R.string.open_source), getString(R.string.source_version, BuildConfig.VERSION_NAME), this::aboutSource);
        space(24);
        button(configuring ? getString(R.string.save_widget) : getString(R.string.add_widget), configuring ? this::finishConfiguration : this::pinWidget, true);
        if (!configuring) small(getString(R.string.widget_help));
        space(8);
        ScrollView current = scroll;
        current.post(() -> current.scrollTo(0, scrollY));
    }

    private String widgetLabel(int id) {
        WidgetSettings s = store.get(id);
        return getString(R.string.widget_label, UiText.place(this, s), id);
    }
    private void chooseWidget() {
        int[] ids = Updates.widgetIds(this);
        String[] labels = new String[ids.length + 1];
        int checked = ids.length;
        for (int i = 0; i < ids.length; i++) { labels[i] = widgetLabel(ids[i]); if (ids[i] == widgetId) checked = i; }
        labels[ids.length] = getString(R.string.widget_defaults);
        choices(getString(R.string.edit_widget), labels, checked, chosen -> {
            widgetId = chosen == ids.length ? 0 : ids[chosen];
            widgetBeforeRefresh = widgetId > 0 ? UpdateDiagnostics.widgetSnapshot(this, widgetId) : "";
            showPage();
        });
    }
    private String locationLabel(WidgetSettings s) {
        if (!s.hasLocation()) return getString(R.string.get_location);
        String name = UiText.place(this, s);
        return name + "\n" + UiText.coordinates(this, s.latitude, s.longitude)
                + (s.follow ? " · " + time(s.locationAt) : "");
    }
    private String status(WidgetSettings settings, Forecast forecast) {
        String error = store.error(widgetId);
        String details;
        if (!settings.hasLocation()) details = getString(R.string.choose_location);
        else if (settings.locationExpired(System.currentTimeMillis())) details = getString(R.string.location_stale);
        else if (forecast == null) details = getString(R.string.forecast_empty);
        else if ("no coverage".equals(forecast.coverage)) details = getString(R.string.radar_no_coverage);
        else if ("temporarily unavailable".equals(forecast.coverage)) details = getString(R.string.radar_unavailable);
        else if (!forecast.hasRadar()) details = getString(R.string.precipitation_unavailable);
        else if (!ForecastWindow.hasUpcomingData(forecast, System.currentTimeMillis())) details = getString(R.string.forecast_waiting);
        else if (forecast.isStale(System.currentTimeMillis())) details = getString(R.string.forecast_stored);
        else details = getString(R.string.radar_available);
        if (forecast != null) details += "\n" + getString(R.string.forecast_issued, time(forecast.updatedAt));
        if (settings.hasLocation()) {
            ForecastCache.Entry entry = new ForecastCache(this).read(ForecastWindow.coordinateKey(settings.latitude, settings.longitude));
            if (entry != null) details += "\n" + getString(R.string.last_successful_check, time(entry.checkedAt));
            if (entry != null && entry.deprecated && store.issue(widgetId) != UpdateIssue.API_DEPRECATED)
                details += "\n" + getString(R.string.error_api_deprecated);
        }
        if (!error.isEmpty()) details += "\n" + UiText.issue(this, store.issue(widgetId));
        if (Updates.pending(this, widgetId)) details += "\n" + getString(R.string.refresh_requested);
        return details;
    }
    private String time(long timestamp) {
        return UiText.time(this, timestamp);
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
        }).setNegativeButton(getString(R.string.cancel), null).show();
    }
    private void chooseLocationMode() {
        choices(getString(R.string.location_mode), new String[]{getString(R.string.follow_location), getString(R.string.fixed_place_mode)}, store.get(widgetId).follow ? 0 : 1, chosen -> {
            if (chosen == 1) { findPlace(); return; }
            WidgetSettings s = store.get(widgetId);
            s.follow = true;
            s.place = "";
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
            else new AlertDialog.Builder(this).setTitle(getString(R.string.location_choice_title))
                    .setMessage(getString(R.string.location_choice_help))
                    .setPositiveButton(getString(R.string.fixed_place), (d, which) -> findPlace())
                    .setNeutralButton(getString(R.string.app_settings), (d, which) -> appSettings())
                    .setNegativeButton(getString(R.string.cancel), null).show();
        } else if (requestCode == BACKGROUND_PERMISSION) showPage();
    }
    private void backgroundLocation() {
        if (!LocationAccess.foregroundAllowed(this)) { locate(); return; }
        if (LocationAccess.backgroundAllowed(this)) { appSettings(); return; }
        String option = Build.VERSION.SDK_INT >= 30
                ? getPackageManager().getBackgroundPermissionOptionLabel().toString() : getString(R.string.allow_all_the_time);
        new AlertDialog.Builder(this).setTitle(getString(R.string.background_location_title))
                .setMessage(getString(R.string.background_location_help, option))
                .setPositiveButton(getString(R.string.continue_button), (d, which) -> {
                    // Request background access separately, after foreground access. On
                    // Android 11+ PermissionController routes this to the location page.
                    if (Build.VERSION.SDK_INT >= 29)
                        requestPermissions(new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION}, BACKGROUND_PERMISSION);
                    else appSettings();
                }).setNeutralButton(getString(R.string.app_settings), (d, which) -> appSettings())
                .setNegativeButton(getString(R.string.not_now), null).show();
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
            labels[i] = getString(R.string.every_minutes, intervals[i]);
            if (intervals[i] == selected) checked = i;
        }
        choices(rain ? getString(R.string.rain_interval) : getString(R.string.dry_interval), labels, checked, chosen -> {
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
        input.setHint(getString(R.string.place_hint));
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        input.setPadding(dp(20), dp(16), dp(20), dp(16));
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(getString(R.string.choose_fixed_place))
                .setView(input).setPositiveButton(getString(R.string.search), null).setNegativeButton(getString(R.string.cancel), null).create();
        dialog.setOnShowListener(ignored -> {
            Button search = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            Runnable runSearch = () -> {
                String query = input.getText().toString().trim();
                if (query.isEmpty()) { input.setError(getString(R.string.enter_place)); return; }
                try {
                    double[] coordinates = ForecastWindow.parseCoordinates(query);
                    if (coordinates != null) {
                        applyPlace("", coordinates[0], coordinates[1]);
                        dialog.dismiss();
                        return;
                    }
                } catch (IllegalArgumentException invalid) { input.setError(getString(R.string.coordinate_range)); return; }
                if (!Geocoder.isPresent()) { input.setError(getString(R.string.geocoder_unavailable)); return; }
                search.setEnabled(false); search.setText(R.string.searching);
                Updates.IO.execute(() -> {
                    List<Address> addresses = new ArrayList<>();
                    String error = null;
                    try { addresses = geocode(query); }
                    catch (IOException e) { error = getString(R.string.geocoder_failed); }
                    List<Address> found = addresses;
                    String failure = error;
                    runOnUiThread(() -> {
                        if (!dialog.isShowing() || isDestroyed()) return;
                        search.setEnabled(true); search.setText(R.string.search);
                        if (failure != null || found.isEmpty()) {
                            input.setError(failure != null ? failure : getString(R.string.places_empty));
                            return;
                        }
                        String[] labels = new String[found.size()];
                        for (int i = 0; i < found.size(); i++) labels[i] = addressLabel(found.get(i));
                        new AlertDialog.Builder(this).setTitle(getString(R.string.select_place)).setItems(labels, (chooser, index) -> {
                            Address address = found.get(index);
                            applyPlace(labels[index], address.getLatitude(), address.getLongitude());
                            dialog.dismiss();
                        }).setNegativeButton(getString(R.string.cancel), null).show();
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
        Geocoder geocoder = new Geocoder(getApplicationContext(), UiText.locale(this));
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
        return UiText.coordinates(this, address.getLatitude(), address.getLongitude());
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
        Toast.makeText(this, getString(R.string.choose_then_save), Toast.LENGTH_LONG).show();
    }
    private void pinWidget() {
        WidgetSettings settings = store.get(widgetId);
        if (!settings.hasLocation()) { locateOrFind(); return; }
        AppWidgetManager manager = AppWidgetManager.getInstance(this);
        if (!manager.isRequestPinAppWidgetSupported()) {
            new AlertDialog.Builder(this).setTitle(getString(R.string.add_rainline))
                    .setMessage(getString(R.string.pin_help))
                    .setPositiveButton(getString(R.string.ok), null).show();
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
        new AlertDialog.Builder(this).setTitle(getString(R.string.privacy))
                .setMessage(getString(R.string.privacy_help))
                .setPositiveButton(getString(R.string.met_privacy), (d, which) -> openLink("https://www.met.no/en/About-us/privacy"))
                .setNegativeButton(getString(R.string.close), null).show();
    }
    private void aboutSource() {
        new AlertDialog.Builder(this).setTitle("Rainline " + BuildConfig.VERSION_NAME)
                .setMessage(getString(R.string.source_help) + LicenseText.MIT)
                .setPositiveButton(getString(R.string.source_code), (d, which) -> {
                    if (BuildConfig.SOURCE_URL.startsWith("https://")) openLink(BuildConfig.SOURCE_URL);
                    else new AlertDialog.Builder(this).setTitle(getString(R.string.source_code))
                            .setMessage(getString(R.string.source_included))
                            .setPositiveButton(getString(R.string.ok), null).show();
                }).setNegativeButton(getString(R.string.close), null).show();
    }
    private void openLink(String url) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
        catch (ActivityNotFoundException e) { Toast.makeText(this, getString(R.string.no_browser), Toast.LENGTH_SHORT).show(); }
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
