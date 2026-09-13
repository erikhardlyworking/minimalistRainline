# Testing

Version: 0.1.8. The APK is a debug build for personal testing and updates earlier versions
without clearing existing widgets or preferences.

## Completed checks

- Debug APK build and Android lint (no errors; advisory warnings remain for
  pinned build tools, widget previews, drawing allocations and UI localisation).
- 38 JVM tests: forecast parsing, rates/units, radar status, missing samples,
  timestamp ordering, exact two-hour clipping, retention beyond 20 minutes, partial/dry data within
  the next 90 minutes, exhausted caches, loading/error precedence, clock mismatches, coordinate
  validation/rounding, settings persistence (including padding and ARGB
  background colour, rainfall scale, highlight controls and time-axis appearance), legacy preference
  defaults, invalid rainfall settings, elapsed-age labels and HTTP retry policy. Transport tests
  cover identification/conditional headers through redirects, unsafe redirects,
  redirect loops, gzip/deflate decoding and decompressed-response size limits.
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
  the missing end of the two-hour axis, distinct open-ring/cross indicators,
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
3. Tap the placed widget and check that its settings open. Check the forecast
   update/check timestamps and elapsed ages below the graph, then tap **Open Yr**.
4. In follow mode, check **Background location → Status: enabled/disabled**.
   Tap Continue and choose **Allow all the time** on Android's Location page.
   Background access is inside the Location permission group, not a separate
   permission in the list. The fallback path is **App settings → Permissions →
   Location → Allow all the time**. Enable it if you want following to continue after two hours away from the
   app; alternatively choose a fixed place. Check the selected coordinates and
   forecast status after moving.
5. Add a second widget with a fixed place and verify independent settings.
6. Leave the phone asleep for over 20 minutes, then unlock it. Check that the
   graph advances to now and still shows remaining cached data while refreshing.
   With no upcoming cached data, expect an open ring until a result arrives;
   a failed refresh with no usable cache should show ×. Android may postpone
   wake events and updates under power saving or while the app is suspended.
7. Check errors in **Forecast status** if coverage or connectivity is missing.
   A dotted axis interval is unknown and a solid flat forecast is dry. An open
   ring means waiting for data; × means data could not be obtained. A usable
   cached graph remains visible even if the latest refresh fails.
8. Open **About → Diagnostics**, inspect the summary, and try Copy if you want
   to paste the technical details into a support request. For wake problems,
   compare the latest received wake, job start/completion, pending reason and
   last update category. Compare the graph submission time and forecast issue
   time too. The report includes a snapshot from before settings performed its
   own refresh, so that refresh does not mask the previous submission state.

Launcher-specific pinning, resize/reconfiguration, tap-through and long-running
background location/battery behaviour still need hands-on testing, including
wake-up after Android has cached or killed the app process. An emulator test
with an active app process does not establish delivery latency under Samsung
power management. Older Android versions have been checked with lint, not yet with physical devices.
