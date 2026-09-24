# Changelog

## 0.1.15

- Added a short hint below the themes explaining that appearance can be
  customized further down the settings screen, in all five app languages.

## 0.1.14

- Compact Light, Dark and Minimal theme previews below Open Yr.
- Light is the default for new installations. Existing widgets and saved
  defaults retain their appearance; Minimal restores white on transparent.
- Independently adjustable rain-line and axis colours, using a picker or hex,
  plus configurable rounded corners.
- Theme changes preserve location, update intervals, rainfall scale and
  highlight threshold. All appearance settings remain independently editable.
- Theme controls and descriptions translated into all five app languages.

## 0.1.5

- A local diagnostic summary with explicit copying and no coordinates, place
  names, device IDs, request URLs or raw errors.
- Repository URL in the default User-Agent and in-app source link.
- Bounded MET HTTPS redirects, explicit gzip/deflate support, persistent
  API-deprecation notices and service-wide 403/429 retry delays per device.
- Randomized update timing and no new background fetches while locked/asleep.

## 0.1.4

- Horizontal guides are hidden by default; saved guide choices are preserved.
- Adjustable time-label size with a preview, space for larger numbers and
  automatic fitting on narrow widgets.
- Independent visibility controls for the time-axis line, ticks and numbers.
  Hiding them preserves the rain curve, including dry intervals.
- Widget accessibility descriptions reflect visible axis elements.

## 0.1.3

- Configurable graph ceiling, defaulting to 3 mm/h. Two guides divide the
  graph into thirds, marking 1 and 2 mm/h at the default scale.
- Rain at or above 2.5 mm/h is red by default. The threshold and highlight
  colour are configurable per widget; highlighting can be disabled.
- Colour changes and ceiling clipping occur at the interpolated crossing
  time. Highlighting uses the actual rate, including above the ceiling.
- Updated settings and widget picker previews to show the new scale and colour.

## 0.1.2

- Transparent background by default, including the widget picker preview.
- A Transparent button in the background colour dialog; saved colours remain
  unchanged on upgrade.

## 0.1.1

- Independently adjustable left, top, right and bottom content padding with
  a preview; large padding adapts to small widgets.
- Per-widget background colour with a visual picker, hex entry and opacity.
- Removed the highest horizontal guide; the two lower guides remain.
- Brighter and longer five-minute ticks, matching the major tick stroke.
- Existing location and appearance settings are retained on upgrade.

## 0.1.0

- Initial two-hour precipitation widget and native settings app.
- MET Norway Nowcast API, cache-aware background updates, current/fixed places.
- Resizing, per-widget appearance, and tap to open Yr.
