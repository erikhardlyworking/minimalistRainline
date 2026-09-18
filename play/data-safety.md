# Data safety: draft for the current Rainline code

Prepared for Hardly Working on 18 September 2026. This is a code-based worksheet,
not a submitted form. No runtime third-party SDKs are included. Native Android
services and the MET endpoint still matter.

| Data path found in the code | Proposed treatment / check |
| --- | --- |
| Fine or coarse device location → MET forecast request | Declare precise and approximate location collection for app functionality. Device location is optional because an arbitrary fixed place works without it. Four-decimal coordinates are still precise. |
| Selected fixed coordinates → MET | Weather requests always send a point. Do not describe this as “no information leaves the device,” even without Android location permission. |
| MET receives the requester's IP, coordinates and app/version User-Agent | Not ephemeral: MET keeps access logs. Include the service's retention and security use in the policy. Confirm the appropriate IP-related form category from its actual use rather than assuming that every IP is a device ID or that no IP needs disclosure. |
| Search text → Android Geocoder | Optional external processing. Verify how the device's geocoding provider handles and discloses it. Assess in-app search history and, if users can enter addresses, address/location categories. Do not omit the search just because there is no Rainline search-history database. |
| Private settings, cached locations and local diagnostic report | Stored locally; app does not upload diagnostics. No analytics, crash-reporting SDK or advertising identifier collection is implemented. |
| Open Yr / browser links | Separate external app/browser; no location or diagnostic payload is appended by Rainline. Those services apply their own policies. |
| Copy diagnostic report | User-controlled clipboard transfer of the displayed technical summary; not an automatic upload. |
| Email sent independently to the public support address | Outside the app's automatic collection path; covered in the privacy policy as support correspondence. |

Start the form with **Yes** to collecting/transmitting data. HTTPS to MET is
verified in `MetClient`; verify the native geocoding provider before attesting
that **all** paths use encryption. Mark MET's retained data **not ephemeral**.
For sharing, conservatively disclose transfer to MET unless a documented Play
exception applies; do not assume it is your contracted processor. No app accounts
exist. Local clearing does not delete third-party logs, so do not promise a
universal deletion mechanism. The final form must cover all distributed versions.
[Google Data safety definitions](https://support.google.com/googleplay/android-developer/answer/10787469?hl=en)
[Android data-use guidance](https://developer.android.com/privacy-and-security/declare-data-use)

The unresolved part is the platform Geocoder: its provider varies by device and
Rainline does not control its transport or retention. Before submission, establish
the provider-related declarations, or deliberately change the app to use a named,
documented search provider (or coordinates only), then update every text that
mentions search. **No such feature change has been made in this preparation.**

MET's API terms explicitly describe request logging. Its current privacy policy
says IPs for weather websites without login can be retained for up to 90 days;
this is not a statement that all fields have that exact retention period.
[MET API terms](https://docs.api.met.no/doc/TermsOfService)
[MET privacy policy](https://www.met.no/en/About-us/privacy)

Do not advertise “information is shared only with MET” while name search remains
available. The prepared listing instead distinguishes direct MET forecast
requests from Android geocoding and external links.
