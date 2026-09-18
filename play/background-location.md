# Background location review preparation

Rainline declares `ACCESS_BACKGROUND_LOCATION`. It has no foreground service,
persistent notification, exact-alarm permission or battery-exemption request.
The intended feature is the **home-screen precipitation widget following your
location while the settings app is closed**, not continuous route tracking.
The listing descriptions now explain that feature in all five languages.

## Declaration text

Copy-ready answers are also in `play/location-declaration.txt`. Both are under
500 characters.

**What is the main purpose of your app?**

> Rainline is an open-source Android home-screen widget showing the next two hours of local precipitation in Norway, Sweden, Denmark and Finland, where radar coverage is available. It lets users see when rain is expected to start or stop at a glance, without opening a weather app. The widget can follow the user’s current location or show a fixed place.

**Describe one location-based feature that needs background location.**

> Follow my location keeps the home-screen rain forecast relevant as the user moves. With permission, Rainline obtains or reuses a recent device location during widget updates while the app is closed, then sends coordinates to MET Norway for the forecast. Foreground-only access cannot obtain a fresh location while only the widget is visible. Updates respect Android power saving; location is not continuously tracked. Users can choose a fixed place without location permission.

This describes the implementation; Google decides whether the feature qualifies
for background location. Do not describe Rainline as a safety-alert service,
continuous tracker or an app that guarantees an immediate update on every unlock.

## Disclosure and consent

Version **0.1.13 (14)** shows a translated disclosure before the first location
permission request. It identifies the widget, optional closed-app use, coordinates
sent to MET Norway and the IP address MET receives. The user can continue, decline,
or choose a fixed place without granting location. All five app languages include
the disclosure.

`SettingsActivity.backgroundLocation()` separately explains background access
before Android’s background permission screen. Rainline requests foreground and
background permissions in separate steps. The background instructions use
Android’s own “Allow all the time” option label. Background access is an option
inside Location, not a separate top-level Android permission.

The app obtains or reuses a recent location during refreshes. It does not
continuously monitor movement. Automatic refreshes pause when the screen is off
or locked, and Android can defer them. A tap on the home-screen widget requests a
refresh without opening a Rainline activity, so location access still occurs in
the background.

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

A settings screenshot alone does not demonstrate background location.
Recordings use an isolated Android emulator and simulated Nordic coordinates;
label the simulation clearly. They must show the signed release candidate, real
permission screens and real forecast behaviour. Do not present the sample preview
as a live home-screen forecast.

## Prepared video and validation

The matching signed release is **0.1.13 (14)**. The 30-second captioned video is
`output/location-review/rainline-background-location.mp4`. Upload it to YouTube
as Unlisted and use its accessible watch URL in the declaration. Companion
YouTube title/description files and the two declaration answers are in that
folder; original footage and edit decisions are retained there too.

The video shows the actual release APK and Android permission screens. Its final
sequence simulates a GPS move from Oslo to Bergen, then taps the home-screen
widget. No Rainline activity opens during the refresh. The widget changes from
the dry Oslo forecast to the real Bergen forecast (0.5 mm/h peak at recording).
The saved widget coordinates were subsequently verified as 60.3929, 5.3221.
The captions identify the GPS simulation. No forecast samples were injected into
the app and the labelled settings sample is not used as a home-screen forecast.

Validation: 53 release unit tests; lint with 0 errors and 15 existing warnings;
all five translation sets; bundletool and release-signature verification;
disclosure cancellation and fixed-place alternative; fresh-install foreground
and background permission flow; and a widget refresh while the launcher was
the resumed activity. The recording used a clean, temporary emulator.
