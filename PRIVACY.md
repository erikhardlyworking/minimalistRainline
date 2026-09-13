# Rainline privacy

Rainline has no accounts, advertisements, analytics or tracking SDKs.

## Weather and location

Rainline sends the chosen latitude and longitude, rounded to four decimal
places, directly to https://api.met.no to request a local precipitation
forecast. MET Norway also receives the device's public IP address and the
app's identifying User-Agent. MET states that IP addresses and request
coordinates are stored in its API access logs; see its
[privacy policy](https://www.met.no/en/About-us/privacy) and
[API terms](https://docs.api.met.no/doc/TermsOfService).

Rainline itself operates no server.

Following your location requires optional Android location permission.
Following from the home screen while Rainline is closed requires optional
background location permission. The app requests a location during weather
updates and reuses recent fixes to save battery. It does not continuously
record a route. A fixed place works without granting location permission.

The selected location, last followed position, per-widget preferences and a
bounded cache of up to 16 forecast locations are stored privately on the device.
Old cached locations are pruned when new entries are saved. No location history
is uploaded to a Rainline service. Android cloud backup and device transfer
are disabled for app data.

## Place searches and external apps

Searching by place name uses Android's geocoding service. The device's geocoding
provider receives that query and handles it under its own policies. Entering
coordinates avoids name search.

Tapping the widget opens Rainline settings. The **Open Yr** button opens Yr if
installed or its website, whose own policies apply. About links open external
documentation in your browser.

## Removing data

Uninstall Rainline or clear its storage in Android settings to delete local
settings and cached weather. Revoke location access at any time in Android's
app permissions; a fixed place remains available as an alternative.

## Diagnostics

Opening Diagnostics generates a summary locally from selected technical fields.
It includes app/Android versions, device model, widget dimensions, permission
and power/network states, forecast ages and fixed error categories. Rainline
also keeps the latest received wake-event and job timestamps and fixed
scheduling/stop categories locally, replacing earlier values rather than
building an event history. For each widget it keeps the latest graph submission
time, graph time origin, forecast issue time and display-state category. Settings
temporarily retain a snapshot of those fields from before their own refresh.
The summary includes those fields and Android's
reported job-delay reasons. It excludes
coordinates, place names, device IDs, request URLs and raw exception messages.
Rainline does not upload the report or retain a report history. Pressing Copy
places it on Android's clipboard so you can choose where to paste it; the
receiving app or website then handles that text under its own policy.

This policy describes the 0.1.x test builds maintained by erikhardlyworking.
For questions, use the
[repository issue tracker](https://github.com/erikhardlyworking/minimalistRainline/issues).
