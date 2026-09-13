# Background refresh investigation

Reviewed 13 September 2026 for Rainline 0.1.8.

A graph becoming shorter proves that the cached bitmap was redrawn against
the current time. It does not prove that Android started a weather job, that
a usable location was available, or that MET returned a newer forecast.
Opening the settings activity makes the process active and can obtain a fresh
foreground location, so it can recover from several different problems.

## Findings

The 0.1.8 investigation found an application bug in addition to the platform
limits below. The runtime receiver used `RECEIVER_NOT_EXPORTED`. That accepts
screen-on from the system UID, but rejects unlock broadcasts sent by System UI
under its own UID. The connected Samsung uses a separate System UI UID, and
Rainline's diagnostics recorded screen-on but no unlock events. Screen-on can
arrive while the lock screen is still showing; the activity gate correctly
rejects a fetch then, but the subsequent unlock never reached the app.

The receiver now uses `RECEIVER_EXPORTED` for its protected Android actions.
Ordinary apps cannot send these protected broadcasts. This follows Android's
[guidance for system broadcasts from privileged apps with separate UIDs](https://developer.android.com/develop/background-work/background-tasks/broadcasts#context-registered-receivers).
[System UI's keyguard code](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/packages/SystemUI/src/com/android/systemui/keyguard/KeyguardViewMediator.java)
shows where the unlock broadcast originates. Exporting the runtime receiver
fixes sender rejection; it does not make a killed process receive broadcasts.

The previous emulator test had the lock screen disabled and accepted either
screen-on or unlock. It therefore missed this defect. The revised test enables
the emulator's swipe lock screen temporarily and requires `USER_PRESENT`.
It registers a second receiver using the old flag to compare both registrations
against the same event. It also listens through an `AppWidgetHost`, without
opening settings, and checks that fetched data is submitted to the host.

The background-location setup also incorrectly skipped the runtime request on
Android 11+. It now requests background access separately after foreground
access, allowing PermissionController to show the location-specific page.
The explanation uses Android's localized option label and spells out the
fallback path: App settings → Permissions → Location → Allow all the time.
Background access is an option inside the Location permission group. See
[incremental location requests](https://developer.android.com/develop/sensors-and-location/location/permissions/runtime#request-only-foreground)
and [background-location guidance](https://developer.android.com/develop/sensors-and-location/location/permissions/background).

Widget snapshot creation and submission are serialized so a concurrent render
cannot submit an older snapshot after a newer one. Local diagnostics now store
the latest submitted graph's time origin, forecast issue time and state for
each widget. The settings screen also captures those values before its own
refresh, so opening settings does not erase that diagnostic comparison.
A successful `updateAppWidget` call establishes submission to Android, not
proof that a particular launcher has painted the image on screen.

1. **Screen-on is not a reliable process wake-up mechanism.** Android 14 and
   later may defer `ACTION_SCREEN_ON` while an app process is cached. A runtime
   receiver disappears when its process is killed. A manifest declaration
   cannot be used to receive `ACTION_SCREEN_ON` instead. See the official
   [broadcast lifecycle guidance](https://developer.android.com/develop/background-work/background-tasks/broadcasts#android_14)
   and [screen-on API](https://developer.android.com/reference/android/content/Intent#ACTION_SCREEN_ON).
2. **Zero minimum latency does not make an ordinary job immediate.** Android
   can still delay ordinary jobs for resource optimisation or other constraints.
   Expedited jobs provide stronger scheduling guarantees and bypass the job
   optimisation delay, but remain subject to quota, connectivity and load.
   Scheduling can fail when expedited quota is exhausted. See
   [JobInfo.Builder](https://developer.android.com/reference/android/app/job/JobInfo.Builder#setExpedited(boolean)).
3. **Location permission is independent of job execution.** Without background
   location permission, follow mode reuses the last foreground position for
   up to two hours. Beyond that, Rainline needs a new position or a fixed place.
   Even with permission, Android throttles background location updates to a
   few deliveries per hour. Waiting for a fresh GPS result before every weather
   request is therefore inappropriate. See
   [background location limits](https://developer.android.com/about/versions/oreo/background-location-limits).
4. **A queued job is not evidence of a completed refresh.** The 0.1.6 emulator
   test verified delivery and queuing with an unexpired cache. It did not
   establish successful network retrieval after standby. The revised test
   expires the cache, performs another sleep/wake cycle, and waits for both
   the server-check timestamp and completed update result to change.

## Scheduling approach retained from 0.1.7

- Preserve usable cached data and realign the graph on delivered wake and
  minute events. Repainting does not request weather on every minute tick.
- Request an expedited, network-constrained job on Android 12+ when a delivered
  wake or recovery event needs data. Promote a waiting ordinary job; leave an
  already running or expedited job alone. Fall back to an ordinary job without
  an added delay when expedited scheduling is rejected. Older Android versions
  use ordinary jobs. An unexpired cache or active HTTP retry delay suppresses
  unnecessary wake jobs for an already configured, recent location.
- Retain the non-wakeup alarm, periodic job and widget callbacks as recovery
  paths. All background fetches still check that the device is awake and
  unlocked; expedited scheduling does not authorise fetching during standby.
- Reuse recent location fixes. When Android cannot supply a new background fix,
  a saved fix remains usable within the existing two-hour limit. The settings
  show background-location permission explicitly and explain what disabling it
  means. A fixed place remains the option that needs no location permission.
- Make a widget tap open its settings and foreground refresh path. Show the
  actual forecast issue time and last server-check time, both with elapsed age.
- Keep local timestamps for the latest received wake, queued job, start,
  completion and stop. Diagnostics also show Android's pending-job reasons and
  restrictions using the [JobScheduler diagnostics API](https://developer.android.com/reference/android/app/job/JobScheduler#getPendingJobReasons(int)).
  These are fixed local fields, not an uploaded event log.

## Alternatives and limits

Replacing JobScheduler with WorkManager would not remove the relevant Doze or
standby limits: WorkManager uses JobScheduler for this work. See
[Doze and App Standby](https://developer.android.com/training/monitoring-device-state/doze-standby).

Doing the whole update inside `BroadcastReceiver.goAsync()` would bypass the
job scheduling step only after a broadcast is delivered. It would not revive
a killed receiver. Its execution deadline includes the asynchronous work;
Rainline's location lookup and HTTP request can exceed that deadline on a slow
connection. Android recommends scheduling longer widget work instead. See
[widget update guidance](https://developer.android.com/develop/ui/views/appwidgets/advanced).

An ongoing foreground service is a materially different product choice: it
requires a notification and a valid service type. A special-use declaration
also needs Play review. It is not the default for this minimal widget. See
[foreground services](https://developer.android.com/develop/background-work/services/fgs)
and [service types](https://developer.android.com/develop/background-work/services/fgs/service-types#special-use).

There is no promise of an update within 20 seconds on every launcher and power
mode. The automated wake test runs with an instrumented app process and cannot
establish Samsung's delivery timing after process freezing or termination.
Those conditions still require device testing; the expanded diagnostics make
it possible to distinguish event delivery, job scheduling, location and HTTP
failures when they occur.
