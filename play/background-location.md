# Background location review preparation

Rainline declares `ACCESS_BACKGROUND_LOCATION`. It has no foreground service,
persistent notification, exact-alarm permission or battery-exemption request.
The intended feature is the **home-screen precipitation widget following your
location while the settings app is closed**, not continuous route tracking.
The listing descriptions now explain that feature in all five languages.

## Proposed declaration text

> Rainline's core feature is a home-screen widget showing the next two hours of
> local precipitation. When a user enables Follow my location and background
> location access, the widget obtains or reuses a recent location during forecast
> updates while the Rainline activity is closed. This keeps the forecast relevant
> as the user moves between places without opening the app to choose a new point.
> Coordinates are sent directly to MET Norway to request the forecast. They are
> not used by Rainline for ads or analytics. Automatic requests pause while the
> phone is asleep or locked and respect Android scheduling and MET caching rules.
> Users can instead select a fixed place without granting location permission.

This explains the current implementation; Google decides whether the benefit
justifies this permission. A weather app or optional feature is not automatically
entitled to approval. If review requires a fixed-place-only Play variant, that is
a product decision and code change, not something already done here.

## Disclosure check before signing the release

`SettingsActivity.backgroundLocation()` already shows a translated explanation
before requesting background access, including closed-app use, MET transfer and
the fixed-place alternative. Its Continue action leads to Android's permission
flow. The earlier foreground request in `locate()` currently calls Android's
permission prompt directly. Review both paths against the final policy: the
background feature and disclosure must be encountered in the normal activation
flow, before the relevant request, rather than relying on an About privacy page.
Check a fresh installation as well as already-granted foreground permission.

Suggested concise disclosure, if refining the activation flow:

> Rainline uses your location to update the precipitation widget for where you
> are, even when the app is closed. Coordinates are sent to MET Norway for the
> forecast; MET also receives your IP address and keeps access logs. You can use
> a fixed place without location access.

This proposed replacement is not yet installed. Retain the platform's own
“Allow all the time” option label in the subsequent instructions. Do not call
background location a separate top-level Android permission; it is an option
inside Location. Cancellation must preserve the fixed-place alternative.

Google asks for a prominent in-app disclosure, declaration and a video showing
the permission flow and background feature. Show the actual release candidate.
Aim for about 30 seconds, allowing readable time for the disclosure; use a
reviewer-accessible YouTube or Drive link.
[Background-location review requirements](https://support.google.com/googleplay/android-developer/answer/9799150?hl=en)
[Disclosure presentation guidance](https://support.google.com/googleplay/android-developer/answer/11150561?hl=en)

## Recording sequence

1. Start with a clean test installation and a Rainline widget. Open location setup.
2. Show the app's explanation, then the real foreground and background permission
   screens in the order encountered. Readable screen text matters more than speed.
3. Grant the optional follow permission yourself and return to the launcher with
   Rainline visible. Show that settings are closed and the widget gets its local
   forecast. Where possible demonstrate a subsequent location change and update;
   if using emulator location simulation, identify it clearly in the recording.
4. If automatic scheduling is deferred, do not edit the video to suggest an
   instant guaranteed wake update. Explain how Android scheduling affects it.
5. Show or provide supplementary footage of declining permission and selecting a
   fixed place. Give the reviewer the Oslo coordinates from the main guide.

A settings screenshot alone does not demonstrate background location. No review
video was recorded in this preparation and no permission was granted by automation.
The optional location permission and real background behaviour must be verified
on the signed release candidate before submission.
