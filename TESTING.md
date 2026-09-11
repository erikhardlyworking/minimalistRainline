# Testing

Version: 0.1.5. The APK is a debug build for personal testing and updates earlier versions
without clearing existing widgets or preferences.

## Completed checks

- Debug APK build and Android lint (no errors; advisory warnings remain for
  pinned build tools, widget previews, drawing allocations and UI localisation).
- 29 JVM tests: forecast parsing, rates/units, radar status, missing samples,
  timestamp ordering, exact two-hour clipping, staleness, coordinate
  validation/rounding, settings persistence (including padding and ARGB
  background colour, rainfall scale, highlight controls and time-axis appearance), legacy preference
  defaults, invalid rainfall settings and HTTP retry policy. Transport tests
  cover identification/conditional headers through redirects, unsafe redirects,
  redirect loops, gzip/deflate decoding and decompressed-response size limits.
- Device smoke tests on a Samsung SM-S938B running Android 16 / API 36:
  Canvas rendering at 130×64, 240×110, 400×100 and 180×240 dp,
  bitmap memory bounds, RemoteViews inflation, unavailable-data rendering,
  settings startup and native layout capture. The 0.1.5 checks also cover
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
- Diagnostic tests use private sentinel values to check that coordinates,
  place names, raw errors and request URLs never reach the report. The Copy
  button was not pressed by automation, preserving the device clipboard.
- MET forecast retrieval for central Oslo, JSON parsing and reuse of its
  unexpired response passed with the updated client.
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
3. Tap the placed widget and check that Yr opens.
4. Enable optional background location if you want the widget to follow you.
   Check the selected coordinates and forecast status after moving.
5. Add a second widget with a fixed place and verify independent settings.
6. With the phone awake, compare the graph after several refresh cycles.
   Android may postpone updates under power saving or while locked.
7. Check errors in **Forecast status** if coverage or connectivity is missing.
   A dotted axis interval is unknown, a solid flat forecast is dry, and ×
   flags unavailable/stale data or a refresh problem.
8. Open **About → Diagnostics**, inspect the summary, and try Copy if you want
   to paste the technical details into a support request.

Launcher-specific pinning, resize/reconfiguration, tap-through and long-running
background location/battery behaviour still need hands-on testing, including
resuming from the new locked/asleep fetch gate. Older
Android versions have been checked with lint, not yet with physical devices.
