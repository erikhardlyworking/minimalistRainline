# Rainline

A small, open-source Android home-screen widget for the next two hours of
precipitation. A transparent background, a thin white line with optional rain
highlights, and no weather icons,
cards, ads, accounts or analytics.

[Source](https://github.com/erikhardlyworking/minimalistRainline) ·
[Issues and maintainer contact](https://github.com/erikhardlyworking/minimalistRainline/issues)

## Try the first version

Requires Android 8.0 or newer.

1. Build the debug APK using the instructions below, then install
   **app/build/outputs/apk/debug/app-debug.apk**. Builds signed with the same
   key update earlier versions in place, keeping existing widgets and settings.
2. Open **Rainline**. Tap **Current location** and allow location access, or
   choose **Location mode → Use a fixed place**.
3. A fixed place can be searched by name or entered as **latitude, longitude**.
   For example, **59.9139, 10.7522** selects central Oslo.
4. Tap **Add widget**, or find Rainline in your launcher's widget picker.
5. Long-press the widget to resize it. Launchers with widget reconfiguration
   support also expose its settings. Settings are always available in the app.

To follow your location from the home screen, open **Background location** in
Rainline and choose **Allow all the time** in Android's permissions. This is
optional. Without it, the app uses the position last obtained while open;
after two hours it requires a new fix. A fixed place needs no location permission.
**Background location** explicitly shows **Status: enabled** or **Status:
disabled**. Enabling automatic updates does not grant this separate permission.
Android can throttle background location even when permission is enabled;
Rainline reuses recent fixes and keeps the existing two-hour age limit.
Approximate location works, but precise location is preferable for local radar.

Tap the widget to open its Rainline settings and foreground refresh path.
Below the graph, **Forecast updated** and **Last checked** show their times and
elapsed ages. **Open Yr** opens the installed Yr app (no.nrk.yr), with the Yr
website as a fallback. Widget setup is labelled **Configure the widget**.

## Reading the graph

- Left edge: now. Right edge: two hours from now.
- Labels: **30, 60, 90** minutes. Major ticks every 30 minutes; visible white
  ticks every **5 minutes**, slightly shorter than the major ticks. The axis
  line, ticks and numbers can each be hidden independently.
- The default scale is **0–3 mm/h**. Guides are hidden by default;
  when enabled, they mark **1 and 2 mm/h**.
  Small upward marks indicate precipitation above the selected ceiling.
- Rain at or above **2.5 mm/h** is red by default; lower rates stay white.
  The colour changes at the interpolated threshold crossing, including when
  the curve is capped at the ceiling. This is a visual highlight, not an alert.
- A solid flat line is a dry forecast. A **dotted interval means missing data**;
  Rainline never extends the last sample or treats missing rainfall as zero.
- Stored forecasts stay visible while any valid interval overlaps the next
  **90 minutes**. The axis stays at two hours and follows the current time;
  past samples drop off and missing future intervals remain empty. There is
  no fixed 20-minute cutoff.
- A small **open ring** means waiting for data when no usable forecast remains.
  **×** means data could not be obtained, including location or radar problems.
  A failed refresh does not add an error marker over a usable cached graph.
  Open Rainline's forecast status for details.
- If MET reports a radar outage, the cache retains the previous useful forecast
  separately from the new HTTP response and its validators. The retained
  forecast is used only while its timestamped samples still apply.
- The settings preview uses clearly labelled sample rain until live data is
  available. The actual home-screen widget never shows sample weather.

The live MET response can end before the full two-hour window. The remainder
is dotted. Missing samples are not joined with an invented line.

## Settings

Location and appearance are per widget. The app can switch between placed
widgets and defaults for new widgets. Options include following your location
or selecting a fixed place, grey/white/hidden guides, thin/normal forecast
stroke, time labels, and automatic updates. Font edges use normal Android antialiasing.

**Time axis** has separate switches for the axis line, ticks and numbers, plus
a live preview and number-size slider (8–24, default 11). Larger numbers get
more space below the graph and shrink on very narrow widgets to prevent
overlap. Turning all three switches off shows only the rain curve (and any
enabled horizontal guides or data-status marker). A dry forecast still draws
as a flat curve; missing forecast intervals remain gaps.

**Horizontal guides** defaults to Hidden. Previously saved guide choices are
preserved; choose Hidden explicitly to remove them from an existing widget.

**Rainfall scale and highlight** sets the graph ceiling and highlight threshold
independently (0.1–100 mm/h). The scale stays fixed at your chosen value;
the two guides divide it into thirds. Turn highlighting off to keep the entire
forecast line white. Existing widgets receive the new 3 mm/h ceiling and
2.5 mm/h highlight defaults when upgrading from 0.1.2.

**Rain highlight colour** offers a visual colour picker, hue slider and
**#RRGGBB** hex entry. It defaults to red. The colour preview uses labelled
sample rain and a 2.5 mm/h threshold; the widget uses your saved threshold.

**Content padding** adjusts the left, top, right and bottom edges independently
from 0–48 dp, with a live sample preview. Padding is reduced proportionally if
needed to keep a very small widget readable. This controls spacing inside the
widget; the launcher may reserve additional space around it.

**Background colour** includes a visual saturation/brightness picker, hue
slider, opacity slider, and hex entry. The default is fully transparent;
previously saved colours are preserved. Tap **Transparent**, then **Apply**
to remove an existing widget's background. Use **#RRGGBB** for opaque colours or
**#AARRGGBB** with opacity first. For example, **#000000** is opaque black,
**#80000000** is half-transparent black and **#00000000** is fully transparent.
Background and rain highlight colours are independent. A checkerboard in the dialog preview indicates
transparency; it is not drawn into the actual widget.

Apply commits an appearance edit; Cancel leaves the widget untouched.

**Diagnostics**, under About, previews a local summary of the app and Android
versions, device model, widget dimensions, permissions, power/network state,
forecast age and fixed update-error categories. It also shows the latest wake
event and job timestamps, expedited scheduling outcomes and Android's reasons
for delaying a job. Copy puts the displayed summary
on the clipboard; the app never sends it. Coordinates, place names, device IDs,
request URLs and raw exception messages are excluded.

## Updates and battery

Rainline combines a best-effort five-to-six-minute **non-wakeup, inexact alarm** with
a network-constrained **15-minute JobScheduler job**. Alarms first request a
weather job, then redraw cached data against the current time without waiting for the network. Both respect Android's
background scheduling. There are no exact alarms, foreground services,
persistent notifications or battery-optimization exemptions.

Background network requests require an automatic widget and an awake, unlocked
phone. Requests pause while the phone is locked or asleep; redraws can still
use cached data. Random delays spread scheduled requests across installations.
The gate checks device activity, not whether the launcher is currently showing
the widget. An already running request can finish after the screen locks.

A runtime listener requests an expedited refresh on Android 12+ when screen-on
or unlock is delivered and the forecast is due for checking, then immediately
redraws the cached graph. A waiting ordinary job is promoted; already running
or expedited work is left alone. If expedited quota is exhausted, an ordinary
job is scheduled without an added delay. Older Android versions use ordinary
jobs. Valid HTTP cache entries and retry delays avoid unnecessary wake jobs
when the location is configured and recent.
While the process is running and the phone is awake and unlocked, minute
broadcasts also advance the graph without requesting weather.
Android can defer [screen broadcasts](https://developer.android.com/develop/background-work/background-tasks/broadcasts#android_14)
in a cached process; listeners also stop when the process is killed. The
existing alarm, periodic job and widget callbacks remain as recovery paths.
An instant refresh on every unlock is therefore not guaranteed. The
[background update investigation](docs/background-updates.md) explains the
platform limits, alternatives, location behaviour and testing limits.

Actual intervals can be longer under Doze, power saving, launcher suspension
or manufacturer restrictions. A bitmap already held by a suspended launcher
cannot age itself: an old graph may remain visible until Android permits the
next redraw. The settings screen shows the forecast issue time and last
successful server check. Opening the app and **Refresh now** request an update.

Automatic updates can be disabled. Cached graphs still redraw on the inexact
alarm and delivered minute/wake events so their time axis can advance when
Android allows it.

Requests use MET's **Expires**, **If-Modified-Since** and **ETag** headers, including
manual refreshes. Coordinates are rounded to four decimals. There is backoff
for network/server failures and **Retry-After** responses. Widgets at the same
rounded coordinates share cached weather.

The HTTP client handles gzip/deflate and up to four HTTPS redirects within
api.met.no. HTTP 203 responses remain usable but display an API-deprecation
warning. HTTP 403/429 backoff applies across all locations on the device.

## Build

Native Java/Android views and RemoteViews; no runtime third-party dependencies.

- JDK 17
- Android SDK platform 36 and build tools 35.0.0
- Gradle 8.14 (checked-in wrapper, verified distribution checksum)
- Android Gradle Plugin 8.11.1

Set ANDROID_HOME or create an untracked local.properties with
sdk.dir=/path/to/android/sdk, then:

~~~sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
~~~

The APK is written to **app/build/outputs/apk/debug/app-debug.apk**.

Device smoke tests exercise the real Canvas renderer at four widget sizes,
RemoteViews inflation, missing-data rendering, and settings activity startup:

~~~sh
./gradlew :app:assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -r app.rainline.test/app.rainline.SmokeInstrumentation
~~~

The custom runner reports **INSTRUMENTATION_CODE: -1** on success and **0** on
failure. It does not need any location permissions.

Add **-e live true** to the instrumentation command to also fetch a live
central-Oslo forecast and verify that a second request reuses its unexpired
cache. This sends a request from the test device to MET Norway.

Unit tests cover live API field names and units, missing and negative rates,
radar availability, malformed timestamps, forecast clipping/interpolation,
cached forecast time windows, loading/error states, coordinates, settings persistence, redirects, compression and HTTP
retry policy. Device checks also exercise cache revalidation, deprecation,
shared throttling and exclusion of private fields from diagnostics.

## Source and future releases

Source code and artwork created for the app are MIT licensed. MET weather
data is separately licensed under [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/).
The in-app About screen includes source attribution, the data licence,
the graph transformations and Rainline's independent status.

This first version is a locally signed **debug build**, not a Play Store release.
Before a Play release, finalize the application ID and signing key and complete
the store listing and policy declarations. The repository URL is already the
default MET contact and in-app source link. These can be overridden for a fork:

~~~sh
./gradlew :app:bundleRelease \
  -PmetContact='https://github.com/YOUR-ACCOUNT/YOUR-REPOSITORY' \
  -PsourceUrl='https://github.com/YOUR-ACCOUNT/YOUR-REPOSITORY'
~~~

The test build identifies itself as
**Rainline/0.1.7 (https://github.com/erikhardlyworking/minimalistRainline)**.
The metContact property is sent in the User-Agent; sourceUrl controls the
in-app repository link. These values are public, not secrets.

Public release also needs release signing, current Play Console declarations,
review of the background-location permission for this core feature, and testing
on additional Android versions/launchers. UI text is English in this first
version; it should be extracted fully for translation before localization.

MET's [API terms](https://docs.api.met.no/doc/TermsOfService) recommend a caching
proxy for mobile apps and require an agreement above 20 requests/second across
an application's installations. This test client still accesses MET directly.
Before broad distribution, resolve the proxy arrangement and whether its
device-activity gate sufficiently meets MET's idle-app guidance. No proxy is
deployed by this repository.

Local design reference images are excluded from source archives and ignored
by Git because they contain the original user's personal home-screen content.

## Data and documentation

- [MET Nowcast API](https://api.met.no/weatherapi/nowcast/2.0/documentation)
- [Data model](https://docs.api.met.no/doc/nowcast/datamodel.html)
- [API terms](https://docs.api.met.no/doc/TermsOfService)
- [Data licensing](https://docs.api.met.no/doc/License)
- [Privacy](PRIVACY.md)

The service covers radar-supported locations in Norway, Sweden, Finland and
Denmark; availability depends on local radar coverage. Rainline uses MET's
API directly. It does not scrape Yr or include Yr branding.
