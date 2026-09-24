# Background refresh investigation

Reviewed 24 September 2026; includes a Samsung capture on 0.1.15 and recovery hardening in 0.1.16.

A graph becoming shorter proves that the cached bitmap was redrawn against
the current time. It does not prove that Android started a weather job, that
a usable location was available, or that MET returned a newer forecast.
Opening the settings activity makes the process active and can obtain a fresh
foreground location, so it can recover from several different problems.

## Recovery hardening in 0.1.16

The recorded 0.1.15 trace below remains evidence of deferred event delivery,
not a reproduced network failure. This release fixes the separate recovery
weaknesses identified while reviewing that trace:

- Workers return a fixed completion category to JobService, including cache not
  due, asleep/locked, transient failure, server backoff, permanent failure and
  cancellation. Temporary automatic failures schedule a separate ordinary,
  network-constrained job (1103), leaving the periodic schedule intact.
- A burst permits at most two follow-ups: 60–90 seconds, then 120–150 seconds
  after completion. Later MET backoff wins, followed by up to 30 seconds of
  jitter. Attempt and ten-minute expiry live in JobInfo extras so a process
  restart cannot reset their bounds. A delayed, expired recovery does no work.
  Known permission/location/coverage/403/TLS/invalid-data failures do not start
  a rapid retry. The normal periodic/wake/tap paths remain independent.
- A due job that runs before unlock, or a delivered screen-on event while
  locked, preserves one catch-up. If that attempt still finds the phone locked
  or asleep, it stops. The worker checks power/lock state again after resolving
  location. A running network request can still finish after the screen locks.
- Recovery checks the currently placed/enabled widgets and their cache/cadence.
  Disabling automatic updates cancels recovery. Repeated events coalesce a
  queued burst instead of resetting its deadline. Manual taps remain one-shot
  and never enable automatic updates implicitly.
- On API 28+ requests use the job's assigned network, not a process-wide binding.
  API 34+ assignment changes update a per-run reference for later connections.
  A lost assignment becomes a retryable failure, with no silent default-network
  fallback. Android 8 retains its default-network behavior.
- Local diagnostics record fixed completion/recovery categories and the latest
  network-change timestamp. No location, raw exception, event history or remote
  diagnostic reporting was added. Stopped work cannot overwrite a replacement
  job's completion or initiate another recovery burst.

Validation includes fault-injected DNS failure → backoff → successful cached
forecast, cancellation, sleep gates, retry bounds, coalescing, server delay,
assigned-network loss/reassignment and existing cache/cadence tests. The actual
Android 16 JobService was exercised with a sleeping widget, preserved follow-up,
server-backoff wait, replacement recovery job and fresh-cache no-op. A separate
emulator unlock fetched a real MET forecast through the assigned network and
submitted it to the widget host. These active-process tests cannot establish
that Samsung will deliver broadcasts promptly after an overnight freeze; that
remains the purpose of the phone trial. No foreground service, ongoing
notification, exact alarm, permission or power-setting change was introduced.

## Follow-up on 24 September 2026 (0.1.15)

The user reported that the morning forecast can take minutes to appear, while
another clock widget keeps ticking. A widget tap refreshes promptly. Read-only
ADB observation reproduced an automatic delay on the Samsung S25 Ultra without
opening Rainline, tapping its widget, or changing permissions or power settings.

All times below are UTC:

- At 20:58:47 the device was transitioning out of dozing. The periodic job ran
  and completed in roughly 21 ms, redrawing the old forecast without changing
  the forecast-attempt timestamp. This is consistent with the awake/unlocked
  guard skipping work during the transition; the old diagnostics did not record
  which guard caused the skip, so it is not proof of the precise check.
- By 20:58:52 the device was awake. Rainline subsequently remained cached and
  frozen, with its broadcast queue reporting `INFINITE_DEFER`. Its recorded
  screen-on and unlock times still referred to an earlier unlock. The user
  confirmed that the home-screen graph still ended around 60 minutes.
- At 21:00:42.850 Android delivered screen-on; unlock followed at .881 and the
  app's alarm/system receiver at .903. A prompt job was scheduled at .866. The
  refresh job completed roughly 0.28 seconds after starting, and the forecast
  cache recorded a new check at 21:00:42.937. No widget tap occurred in this
  interval. The user then confirmed that the widget had updated.
- Afterward the phone was unlocked, background location permission was granted,
  the app's standby bucket was Active, and no HTTP backoff was active. Active
  standby classification therefore did not prevent this cached-process delay.

This capture supports delayed event delivery, rather than a slow download, as
the cause of this particular approximately two-minute wait. The alarm and wake
broadcasts arrived together; the capture cannot establish which event caused
Android to release the app. It also does not establish the cause of every
previous morning delay. Diagnostics retain only the latest timestamps.

Android explicitly documents deferred screen broadcasts for cached apps in its
[broadcast guidance](https://developer.android.com/develop/background-work/background-tasks/broadcasts#android_14).
A clock is not an equivalent test: Android supports
[TextClock in RemoteViews](https://developer.android.com/reference/android/widget/RemoteViews),
so a launcher-hosted clock can keep time without waking its provider app or
requesting network data. The particular clock app was not inspected.

### Separate recovery weaknesses found in the source

These are findings for follow-up work, not fixes present in 0.1.15:

1. Pending refresh jobs already require network connectivity, and
   `RefreshJobService.onStopJob()` requests rescheduling when Android stops a
   running job (including lost network constraints). Missing connectivity
   before the job starts therefore need not lose that job. However, an I/O,
   DNS or timeout exception caught inside `Updates.refreshOne()` becomes a
   stored error; the completion callback still calls `jobFinished(params,
   false)`. No failure outcome reaches JobService. A transient failure can thus
   leave recovery to the next alarm, periodic job, received wake or user tap.
   MET's local one-minute transport backoff only gates later requests; it does
   not itself schedule one. A retryable worker outcome should reach JobService
   so [Android's rescheduling and backoff](https://developer.android.com/reference/android/app/job/JobService#jobFinished(android.app.job.JobParameters,%20boolean))
   can preserve the request. Permanent configuration failures, valid cache,
   server Retry-After and the sleep gate need separate handling.
2. The HTTP client currently opens connections on the default network. On API
   28+, a job should instead use
   [the network assigned in JobParameters](https://developer.android.com/reference/android/app/job/JobParameters#getNetwork()).
   Android documents that the assigned and default networks can differ.
   Using the assigned network, and its API 34+ change callback, would make
   Wi-Fi/mobile handovers more robust. This is a correctness improvement, not
   evidence that a handover caused the observed delay.
3. Jobs that run before the phone is fully unlocked deliberately skip data
   fetching, but also finish without preserving an immediate catch-up request.
   A bounded recovery path for that transition is worth testing. Simply
   retrying every skipped periodic job indefinitely while asleep would add
   unnecessary work, and a retry still cannot guarantee immediate execution.
   Recording a fixed completion outcome (success, asleep/locked, cache not due,
   retryable failure) would distinguish these cases in future diagnostics.

The current architecture remains appropriate for a widget without an ongoing
notification: network-constrained jobs, cache-aware updates, inexact non-wakeup
alarms, and an explicit tap action. Hardening the recovery paths above can
improve reliability after execution is granted. It cannot make a deferred
screen/unlock broadcast arrive promptly. WorkManager or a process-local network
callback would not provide that missing guarantee. Samsung's Never sleeping
apps option remains an optional user-controlled experiment, not a demonstrated
fix for this cached-process behavior. No foreground service or notification
mode is proposed for the requested minimal widget.

## Findings

### Frozen-process failure confirmed on the Samsung

On 14 September, read-only inspection of the Samsung S25 Ultra running Android
16 and Rainline 0.1.9 captured the failure without opening Rainline. The app
process was cached and frozen. Samsung's broadcast queue reported
`INFINITE_DEFER`, `FRZ by MARs`, and a `USER_PRESENT` unlock event held for
3 minutes 23 seconds. No immediate refresh job was pending. Foreground and
background location permissions were both granted. The app was in the active
standby bucket, so this observation does not establish that it was on Samsung's
Sleeping apps or Deep sleeping apps list.

The next user-performed unlock, leaving the launcher visible without tapping
Rainline, succeeded: screen-on was received at 10:45:20.830 Europe/Oslo, the
refresh job started at 10:45:20.913, and the new graph was submitted at
10:45:21.201. An alarm/system callback also arrived during that sequence.
It may have activated the process and released the queued broadcasts; the
timestamps alone do not establish which callback caused the activation.

These captures establish intermittent event-delivery failure before the
refresh job is scheduled. They do not reconstruct the earlier morning's exact
cross indicator: the latest diagnostic fields had already been replaced after
the user opened settings. Submission timestamps establish a call to Android's
widget host, not a measured display-paint time.

Android's [cached-app freezer documentation](https://source.android.com/docs/core/perf/cached-apps-freezer)
explains that runtime broadcasts queue while a process is frozen. Starting an
activity or job, or receiving an eligible manifest intent, can activate it.
[System UI's unlock broadcast](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/packages/SystemUI/src/com/android/systemui/keyguard/KeyguardViewMediator.java)
explicitly uses `DEFERRAL_POLICY_UNTIL_ACTIVE`. Opening settings therefore
provides the activation that an unlock event alone may not provide. Exporting
the receiver fixes sender rejection but cannot override this delivery policy.

The instrumented unlock test below exercises an active process. It cannot
verify recovery from freezing because instrumentation changes process
importance. The external emulator probe in
[`scripts/verify-cached-unlock.py`](../scripts/verify-cached-unlock.py) instead
waits for natural freezing before unlocking, inspects the held broadcast, and
then opens settings to check whether activation releases it. It does not
change permissions, battery policy or the system freezer configuration.
On the Android 16 emulator this reproduced the same behaviour: after 15
seconds on the unlocked launcher, Rainline was still frozen and both
`SCREEN_ON` and `USER_PRESENT` were deferred. Opening settings released the
unlock event. This probe isolates delivery, with no widgets or configured
location, so it does not make a weather request or test host rendering.

### Earlier application fixes

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
- In 0.1.7–0.1.9, a widget tap opens settings and its foreground refresh path. Show the
  actual forecast issue time and last server-check time, both with elapsed age.
- Keep local timestamps for the latest received wake, queued job, start,
  completion and stop. Diagnostics also show Android's pending-job reasons and
  restrictions using the [JobScheduler diagnostics API](https://developer.android.com/reference/android/app/job/JobScheduler#getPendingJobReasons(int)).
  These are fixed local fields, not an uploaded event log.

## Configurable cadence in 0.1.9

Automatic requests now require the widget's selected interval to have elapsed
since the last successful HTTP check. Defaults are 5 minutes with rain and
15 minutes when dry; both offer 5, 10, 15, 30 and 60 minutes independently.
Rain anywhere in the remaining two-hour window selects the rain interval.
Dry classification requires continuous zero-rate coverage for the next
90 minutes; missing radar or insufficient near-term coverage stays on the rain
interval. An old retained forecast can still be drawn during a radar outage,
while the newest response's radar status controls the request cadence.

The same policy gates normal/wake jobs, periodic worker execution and automatic
settings refreshes. The worker rechecks after queueing and after resolving the
location. Each coordinate cache keeps its own last-check time, allowing widgets
at the same place to reuse a completed request. Different widget intervals
remain independent. Manual refresh bypasses the app interval, but all requests
still honour MET's Expires and retry delays as required by its
[terms](https://docs.api.met.no/doc/TermsOfService).

The existing non-wakeup alarm continues to realign cached graphs. It can check
whether weather is due without fetching anything. Consequently the configured
interval is a minimum spacing, not an exact wall-clock schedule; Android's
[inexact alarm delivery](https://developer.android.com/develop/background-work/services/alarms#inexact)
can make the actual interval longer.

## Alternatives and limits

### Manual recovery in 0.1.10

Tapping now sends an explicit, foreground-priority broadcast PendingIntent
to Rainline's non-exported receiver. It realigns the graph and schedules an
expedited manual job for the tapped widget, with a regular fallback if quota
is unavailable. An existing pending expedited job is reused. Manual jobs are
independent of automatic-update settings and jobs; the worker still observes
the awake/unlocked gate, location permissions, MET cache and retry delays.
Location/HTTP work runs in JobService rather than extending the broadcast
receiver's lifetime. This follows Android's
[widget interaction guidance](https://developer.android.com/develop/ui/views/appwidgets/advanced#update-in-response-to-a-user-interaction).

With the Samsung process confirmed cached and frozen before a tap, the actual
widget action completed a refresh and submitted a graph in about 0.4 seconds,
with the launcher still focused. The last widget tap is recorded in local
diagnostics. This verifies manual recovery; it does not change the automatic
unlock delivery limitation described above. A completed refresh can reuse
MET's valid cache rather than downloading another response.

The unavailable indicator is a circular refresh arrow, while an open ring
still means waiting. Settings use the launcher's existing touch-and-hold
reconfiguration control or the app icon. The Android
[reconfiguration flag](https://developer.android.com/develop/ui/compose/glance/configuration#enable-users-to-reconfigure-placed-widgets)
is already declared; launchers control how that affordance appears.

### Automatic recovery alternatives

Replacing JobScheduler with WorkManager would not remove the relevant Doze or
standby limits: WorkManager uses JobScheduler for this work. See
[Doze and App Standby](https://developer.android.com/training/monitoring-device-state/doze-standby).

Doing the whole update inside `BroadcastReceiver.goAsync()` would bypass the
job scheduling step only after a broadcast is delivered. It would not revive
a killed receiver or release a broadcast held for a frozen process. Its
execution deadline includes the asynchronous work;
Rainline's location lookup and HTTP request can exceed that deadline on a slow
connection. Android recommends scheduling longer widget work instead. See
[widget update guidance](https://developer.android.com/develop/ui/views/appwidgets/advanced).

An ongoing foreground service is a materially different product choice: it
requires a notification and a valid service type. A special-use declaration
also needs Play review. It is not the default for this minimal widget. See
[foreground services](https://developer.android.com/develop/background-work/services/fgs)
and [service types](https://developer.android.com/develop/background-work/services/fgs/service-types#special-use).
Keeping an explicitly enabled service active is the supported direction to
investigate if prompt unlock handling is essential. Network access must still
pause while the phone is locked or asleep, and honour the selected intervals,
HTTP cache and retry delays. Service stops, connectivity and scheduling still
prevent an unconditional seconds-after-unlock guarantee.

[Samsung documents](https://developer.samsung.com/mobile/app-management.html)
**Settings → Device care → Battery → Background usage limits → Never sleeping
apps** as an exception to its application-control restrictions. That is a
reasonable user-controlled trial, but it is not evidence that Android's
cached-broadcast deferral has been disabled. Rainline was not battery-optimizer
exempt during this investigation. No battery settings or permissions were
changed, and no always-running service was added.

There is no promise of an update within 20 seconds on every launcher and power
mode. The automated wake test runs with an instrumented app process and cannot
establish Samsung's delivery timing after process freezing or termination.
The physical-device capture above now confirms freezing-related deferral; it
does not demonstrate a fix. Process termination and long-running recovery
remain separate cases requiring device testing.
