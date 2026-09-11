package app.rainline;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

public final class OpenForecastActivity extends Activity {
    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int id = getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0);
        WidgetSettings settings = new SettingsStore(this).get(id);
        if (!settings.hasLocation() || settings.locationExpired(System.currentTimeMillis())) {
            startActivity(new Intent(this, SettingsActivity.class).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id));
        } else {
            Updates.enqueue(this);
            Intent yr = getPackageManager().getLaunchIntentForPackage("no.nrk.yr");
            try {
                startActivity(yr != null ? yr : new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.yr.no/en")));
            } catch (ActivityNotFoundException | SecurityException e) {
                try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.yr.no/en"))); }
                catch (ActivityNotFoundException noBrowser) { startActivity(new Intent(this, SettingsActivity.class)); }
            }
        }
        finish();
    }
}
