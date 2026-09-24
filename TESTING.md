# Testing

Version: 0.1.16. The APK is a debug build for personal testing and updates earlier versions
without clearing existing widgets or preferences.

## Completed checks

- 24 September 2026, 0.1.16: 64 unit tests pass, including eight recovery policy
  tests. The native emulator suite covers fault-injected DNS failure/recovery,
  HTTP backoff without repeat downloads, cancellation, sleeping/locked guards,
  network assignment changes, recovery coalescing/limits and diagnostic privacy.
  With `-e tap true`, it additionally exercises actual JobService completion and
  recovery replacement on an Android 16 emulator, expired follow-ups, disabling
  automatic updates, and the existing widget-tap tests. These checks use fake
  responses or local cache/backoff, with no MET requests.
- The separate `-e wakeOnly true` emulator check passes: unlock fetched a real
  MET forecast through the job's assigned network and submitted it to the host.
  This verifies an active app process, not Samsung's overnight event delivery.
  Multi-day phone testing is still needed to measure the practical improvement.
- Final 0.1.16 debug app/test builds and lint pass (0 errors, 13 existing warnings).
  Installed version 0.1.16 (17) in place on the Samsung S25 Ultra, without clearing
  app data or changing the phone's permissions, network or power settings.

- 24 September 2026, 0.1.15: customization hint added in all five languages;
  debug build and translation validation passed (202 entries per translation).
  Installed on the Samsung S25 Ultra as an in-place update and confirmed
  version 0.1.15 (16), preserving existing settings. Before the GitHub push,
  a fresh export of the staged repository built the app and test APKs, passed
  all 56 unit tests and lint (0 errors), without local generated artifacts.
- 0.1.14: 56 unit tests and Android lint passed (0 errors). Theme migration,
  colours and persistence are covered by unit tests. Native emulator checks
  passed for Light, Dark and Minimal at four widget sizes, loading and retry
  states, rounded backgrounds, theme selection and custom colour dialogs,
  alongside the existing widget smoke suite. All five language layouts passed
  at 360 dp width, and the native theme previews were visually inspected.

- 18 September 2026, Play preparation for 0.1.12: release AAB build and release
  lint passed (0 errors, 15 warnings). The AAB is unsigned; Play distribution,
  release signing and the location declaration have not been verified by this
  build check. The emulator exported four native app screenshots for each of
  five languages, five feature graphics and the existing launcher mark at 512 px.
  Asset dimensions/PNG colour modes, listing character limits, translation
  completeness and archive checksums are validated by the packaging script.
  The store icon and all five feature graphics were subsequently regenerated
  with white backgrounds and black artwork/text; native export and pack checks
  passed, and the exported icon and feature graphics were visually inspected.
  Capture uses the app's labelled sample previews, without fetching forecasts
  or requesting permissions, and restores emulator display and app preferences.
- 0.1.12: added the tap-to-refresh and power-saving explanation above **Open Yr**
  in all five languages. Translation completeness (187 entries per translation),
  debug build, Android lint (no errors) and the existing native language/layout
  checks passed. The paragraph's placement was visually checked on the emulator.
- 0.1.11: all 186 translatable resource entries are present in Norwegian Bokmål,
  Swedish, Finnish and Danish, with matching numbered formatting arguments.
  `python3 scripts/check-localizations.py` checks completeness and placeholders.
- Native emulator checks switched Rainline through English and all four added
  languages using Android's actual per-app language API, then restored the
  previous choice. Each language's resources formatted successfully; settings
  text fitted at 360 dp width without clipping or ellipses. Rainfall dialogs
  accepted decimal-comma values, and displayed coordinates round-tripped through
  the input parser. English fallback, translated legacy location labels,
  preserved place names, stored error categories and elapsed times were checked.
  Screens from all four languages were visually inspected. Use the native suite
  with `-e locales true` on an Android 13+ emulator without widgets or a location.
  Android's app-language picker was also opened and its English, Dansk,
  Norsk bokmål, Suomi and Svenska choices verified without selecting a language.
- The normal native widget suite, including tap-to-refresh integration, passed
  again after language switching. The settings tests locate translated controls
  by resource values, rather than assuming the interface is in English.
- 0.1.10: on the Samsung S25 Ultra, Rainline was confirmed cached and frozen
  immediately before tapping the widget. The tap was received, a refresh
  completed with no error, and a graph was submitted in about 0.4 seconds.
  Samsung's launcher remained the focused activity. This verifies recovery
  from a user tap, not automatic recovery after unlock or a new HTTP download
  when MET's cache is still valid.
- On Samsung, touch and hold → **Settings** opened Rainline's **Configure the
  widget** screen. Returning went back to the home screen without changing
  widget settings or layout.
- 0.1.10 emulator integration sends the actual widget PendingIntent through
  the receiver and JobService. A manual refresh completes with automatic
  updates disabled, leaves a second widget untouched, honours the valid HTTP
  cache, submits the graph and clears its pending state. Invalid widget IDs
  are ignored. Native scheduler checks cover manual quota fallback, repeated
  tap coalescing and independent widget requests. Run the native suite with
  `-e tap true` on an unlocked emulator without existing Rainline widgets.
- The refresh-arrow rendering was inspected using the actual Canvas output.
  Both loading and retry remain visible with all axes hidden, have empty
  centres and differ visually. Accessibility describes the new tap action.
  The native suite passed after clearing stale preferences left in the
  interval test's isolated storage by an earlier run; setup and cleanup now
  explicitly clear only that test's preference files.
- Additional standby investigation on 14 September, using the unchanged
  0.1.9 APK: the Samsung's broadcast queue held `USER_PRESENT` for over three
  minutes while Rainline was frozen by MARs. Background location was granted.
  A subsequent user unlock submitted a fresh graph within about 0.4 seconds of
  the received screen-on event, with an alarm also arriving then. This confirms
  an intermittent delivery problem, not a corrected wake-up path.
- A separate external Android 16 emulator probe waited for Rainline to freeze
  naturally, without instrumentation. Screen-on and unlock remained deferred
  after 15 seconds on the launcher; opening settings released the unlock.
  This reproduces the delivery problem independently of Samsung. The emulator's
  swipe-lock setting was restored, and no permissions or battery settings were
  changed. See [the investigation](docs/background-updates.md) for evidence and
  the distinction from the active-process tests below.
- Debug APK build and Android lint (no errors; advisory warnings remain for
  pinned build tools, widget previews and drawing allocations).
- 53 JVM tests: forecast parsing, rates/units, radar status, missing samples,
  timestamp ordering, exact two-hour clipping, retention beyond 20 minutes, partial/dry data within
  the next 90 minutes, exhausted caches, loading/error precedence, clock mismatches, coordinate
  validation/rounding, settings persistence (including padding and ARGB
  background colour, rainfall scale, highlight controls and time-axis appearance), legacy preference
  defaults, invalid rainfall settings, elapsed-age labels and HTTP retry policy. Transport tests
  cover identification/conditional headers through redirects, unsafe redirects,
  redirect loops, gzip/deflate decoding and decompressed-response size limits.
  Coordinate input checks cover decimal points, decimal commas with a semicolon,
  ordinary place names, ambiguous input and non-finite/out-of-range coordinates.
- Refresh-policy tests cover the dry/rain defaults, rain later in the two-hour
  window, ended rain, missing radar/samples, shortened dry coverage, isolated
  rain samples, independent settings and legacy defaults, HTTP expiry, and
  measuring intervals from server checks rather than forecast issue times.
- Isolated native interval checks passed on the phone and emulator: an expired
  HTTP cache does not bypass the selected dry interval; widgets at the same
  place can choose different intervals; retry delays remain enforced; opening
  settings respects the interval; a manual cached refresh runs sooner without
  bypassing server expiry. Both interval controls are present in settings.
  The real emulator unlock/fetch/host-delivery regression also passed in 0.1.9
  with the cached server-check timestamp more than five minutes old.
- Device smoke tests on a Samsung SM-S938B running Android 16 / API 36:
  Canvas rendering at 130×64, 240×110, 400×100 and 180×240 dp,
  bitmap memory bounds, RemoteViews inflation, unavailable-data rendering,
  settings startup and native layout capture. The checks also cover
  transparent defaults at every tested size,
  large padding on a small widget, coloured/transparent bitmap backgrounds,
  hex validation, applying a colour with opacity, and cancelling padding edits.
- Device pixel checks cover rising/falling threshold crossings, exact threshold
  equality, ceiling crossings, custom ceilings and highlight colours, disabling
  highlights, and thresholds above the ceiling using the real unclipped rate.
  Rainfall dialog checks cover invalid numbers, decimal comma input, applying
  the ceiling/threshold/toggle together, and editing highlight colour independently.
- Time-axis dialog checks cover applying number size and independent visibility
  controls. Bitmap checks verify that hiding every axis element preserves the
  rain curve, including dry forecasts. Maximum-size labels were rendered at
  130×64 and 320×100 dp and visually checked for clipping and overlap.
- Isolated on-device HTTP/cache checks cover 203 warning persistence, reuse
  before Expires, 304 validation with exact server validators, clearing the
  warning on 200, and 429 backoff across locations. No user cache is replaced
  by these simulated responses.
- Native rendering checks cover a 31-minute-old forecast shifted to now,
  the missing end of the two-hour axis, distinct open-ring/refresh-arrow indicators,
  and accessibility descriptions. A labelled rendering of these states was
  visually inspected.
- HTTP/cache checks also verify retaining useful samples through a radar-outage
  response and subsequent 304, preserving the new response validators, stopping
  fallback display when its samples expire, and clearing it when radar recovers.
- An opt-in Android 16 / API 36 emulator test creates a temporary widget and
  temporarily enables a swipe lock screen. It requires a real System UI unlock
  event, verifies that the former `NOT_EXPORTED` registration misses the same
  event, and checks that the corrected receiver submits an aligned graph.
  A fresh cache consumes no wake-job quota. After a second sleep/wake cycle with
  an expired cache, it checks a completed MET fetch at the public Oslo test
  location, the submitted forecast timestamp and delivery to an AppWidgetHost
  without opening settings. This passed in 0.1.8. An initial run timed out on
  the live fetch; a rerun with expanded failure diagnostics passed. The initial
  fetch failure's cause was not captured. Test widget/cache and the emulator's
  lock-screen setting are removed/restored afterward. The earlier 0.1.7 test
  had the lock screen disabled and therefore missed the unlock receiver bug.
  Screen control is explicitly prohibited by this test on physical devices.
- Simulated scheduler checks verify expedited-quota rejection with an immediate
  regular fallback, promotion of a queued ordinary job, deduplication of repeated
  wakes, and diagnostic reporting when scheduling is rejected altogether.
- Isolated permission checks reproduce background-location-disabled behaviour:
  a 90-minute-old saved fix is reused with its real timestamp; a fix older than
  two hours returns the explicit location-stale category and permission guidance.
  Tests do not change the phone's permissions.
- Verified the 0.1.8 permission setup on the Samsung SM-S938B / Android 16:
  **Background location → Continue** opens Android's **Location permission**
  page directly, where **Allow all the time** is available. The fallback
  **App settings → Permissions → Location** reaches the same page. Both paths
  were checked without changing the existing **Allow only while using the app**
  selection. Background access is an option within Location, not a separate
  item in the permissions list.
- Native settings checks verify the Open Yr control and removal of the time-axis
  explanation. The settings layout was visually inspected on the emulator.
- Diagnostic tests use private sentinel values to check that coordinates,
  place names, raw errors and request URLs never reach the report. The Copy
  button was not pressed by automation, preserving the device clipboard.
- Live MET forecast retrieval for central Oslo, JSON parsing and unexpired
  response reuse passed in 0.1.5. The 0.1.6 cache changes were checked with
  isolated simulated responses, without adding live API requests to the tests.
- Visual inspection of the diagnostic dialog. The time-axis dialog, large
  numbers and curve-only layout were also inspected in 0.1.4.
  Captures come from Android's actual view hierarchy with labelled sample rain.

## Try on your home screen

1. Open Rainline. Choose your location, then tap **Add widget**.
2. Resize the widget to your preferred size and check five-minute ticks,
   30/60/90 labels and line weight. Guides now default to Hidden; existing
   choices are preserved. Under Appearance → Time axis, try the number-size
   slider and hiding each axis element independently. Under Appearance,
   try Rainfall scale and highlight (3 mm/h ceiling, 2.5 mm/h threshold by
   default), including turning highlights off and choosing another highlight
   colour. Also try Content padding on each edge and Background colour with the picker,
   hex entry and opacity. Existing colours remain saved; use Transparent and
   Apply to clear an existing background. Check that Apply updates the selected widget.
3. Tap the placed widget and check that it refreshes without leaving the home
   screen. Long-press and choose **Settings/Reconfigure**, or open Rainline.
   Check the forecast update/check timestamps and elapsed ages below the graph,
   then tap **Open Yr**.
4. In follow mode, check **Background location → Status: enabled/disabled**.
   Tap Continue and choose **Allow all the time** on Android's Location page.
   Background access is inside the Location permission group, not a separate
   permission in the list. The fallback path is **App settings → Permissions →
   Location → Allow all the time**. Enable it if you want following to continue after two hours away from the
   app; alternatively choose a fixed place. Check the selected coordinates and
   forecast status after moving.
   Under **Updates**, configure **When rain is forecast** and **When no rain is
   forecast** independently. Defaults are 5 and 15 minutes. Returning from
   standby or opening settings should not fetch again before the applicable
   interval has elapsed. **Refresh now** may run sooner, subject to MET's cache.
5. Add a second widget with a fixed place and verify independent settings.
6. Leave the phone asleep for over 20 minutes, then unlock it. Check that the
   graph advances to now and still shows remaining cached data while refreshing.
   With no upcoming cached data, expect an open ring until a result arrives;
   a failed refresh with no usable cache should show a circular refresh arrow.
   Tap to retry without opening settings. Android may postpone
   wake events and updates under power saving or while the app is suspended.
7. Check errors in **Forecast status** if coverage or connectivity is missing.
   A dotted axis interval is unknown and a solid flat forecast is dry. An open
   ring means waiting for data; a refresh arrow means data could not be obtained. A usable
   cached graph remains visible even if the latest refresh fails.
8. Open **About → Diagnostics**, inspect the summary, and try Copy if you want
   to paste the technical details into a support request. For wake problems,
   compare the latest received wake, job start/completion, pending reason and
   last update category. Compare the graph submission time and forecast issue
   time too. The report includes a snapshot from before settings performed its
   own refresh, so that refresh does not mask the previous submission state.
9. On Android 13+, open **About → Language** and choose one of the supported
   languages. Check settings, rainfall inputs, status messages and widget
   accessibility descriptions. Choose the system default to follow the phone
   again. Earlier Android versions follow the phone's language automatically.

Launcher-specific pinning, resize/reconfiguration, tap-through and long-running
background location/battery behaviour still need hands-on testing, including
wake-up after Android has killed the app process. The external probe now
reproduces cached-process deferral; the instrumented test described above
cannot cover that condition. Older Android versions have been checked with
lint, not yet with physical devices.

## Reproduce deferred unlock delivery

Use an Android emulator with a debuggable Rainline installation, no configured
location or placed Rainline widgets, and no PIN/password. Widgets are omitted
so their scheduled alarms cannot activate the app during the observation.

```sh
python3 scripts/verify-cached-unlock.py --adb "$ANDROID_HOME/platform-tools/adb" --serial emulator-5554
```

The probe allows up to 150 seconds for natural freezing, then unlocks and
observes delivery for 15 seconds before opening settings. An emulator that
never freezes is reported as inconclusive. A run that receives the unlock
while cached reports that outcome instead of treating deferral as universal.
The script refuses physical devices, restores the previous swipe-lock setting,
and makes no weather requests. It verifies event delivery, not data retrieval
or widget rendering. Do not run instrumentation concurrently: it keeps the
application active and changes the condition being tested.
