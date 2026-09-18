# Rainline: Google Play handoff

Prepared 18 September 2026 for **Hardly Working**, an **organisation** developer
account. Public support: **workinghardlyforyou@gmail.com**.

The current signed release is **0.1.13 (14)**, package **app.rainline**. It adds
a location disclosure before the first permission prompt in all five languages.
The original listing/screenshot ZIP still contains the **0.1.12 (13)** unsigned
inspection build; use the newer signed bundle described in step 5. A local upload
key and review video have been prepared. No Play or YouTube upload has been
performed by these preparation scripts.

## Files to use

Open `output/rainline-play/review.html` for all five listings and their images.
The complete handoff is also in `output/rainline-google-play-pack.zip`.

| Pack file | Where it goes |
| --- | --- |
| `en-GB/title.txt`, `short-description.txt`, `full-description.txt` | Main store listing, English (United Kingdom) |
| Equivalent files in `no-NO`, `sv-SE`, `fi-FI`, `da-DK` | Norwegian Bokmål, Swedish, Finnish and Danish translations |
| `icon.png` | App icon, shared by all listings |
| Each language's `feature-graphic.png` | That listing's feature graphic |
| Each language's `phone-screenshots/01` through `04` | Phone screenshots in that order |
| Each language's `image-descriptions.txt` | Accessible descriptions of the graphics |
| Each language's `release-notes.txt` | What's new in the release |
| `privacy/index.html`, `nb.html`, `sv.html`, `fi.html`, `da.html` | Public privacy pages, ready to host |
| `rainline-0.1.12-UNSIGNED.aab` | Build verification artifact; **sign before upload** |
| `data-safety.md`, `background-location.md` | Working notes for App content and review |

The Norwegian store folder uses Play's `no-NO` name; the app itself uses Android's
`nb` locale. The prose is Bokmål. Every title is within 30 characters, every short
description within 80, and every full description within 4,000. The generator
also checks 500-character release notes and 140-character image descriptions.
[Google's listing fields](https://support.google.com/googleplay/android-developer/answer/9859152?hl=en)

There are four **1080 × 1920** phone screenshots per language, a **1024 × 500**
feature graphic per language and a shared **512 × 512** icon. Screenshots and
feature graphics are RGB PNGs; the icon is RGBA. These meet the documented file
formats and dimensions. Four portrait screenshots also meet the resolution/count
recommendation; that does not guarantee promotional placement.
[Asset requirements](https://support.google.com/googleplay/android-developer/answer/9866151?hl=en)

Screenshots show actual Android settings and dialogs, with the app's own labelled
sample forecast. They are not home-screen screenshots or live weather. The store
icon and all five feature graphics use white backgrounds with black artwork and
text. The feature graphics use the actual chart geometry, recoloured for this
promotional artwork.
There is no personal phone content or weather data from a user's location.

## 1. Finish account setup and create the app

Complete the verification tasks shown on your Play Console account dashboard,
including organisation and contact verification. Use Hardly Working as the
public developer name and the support email above.

Create an **App**, choose **English (United Kingdom)** as the default listing
language, and enter **Rainline – rain widget**. Choose your intended price; Free
fits the current app, which contains no ads or purchases. Complete the developer
and export declarations yourself as the account holder.

Your organisation account is outside the new-personal-account gate requiring
12 continuously opted-in testers for 14 days. An internal test is still useful
for verifying the signed Play-delivered build.
[Personal account testing rule](https://support.google.com/googleplay/android-developer/answer/14151465?hl=en)

Before the first bundle upload, confirm that **app.rainline** is the application
ID you want permanently. It is already used by the test APK on your phone;
changing it produces a separate app. Play application IDs cannot be repurposed.
[Application IDs and uploads](https://support.google.com/googleplay/android-developer/answer/9859152?hl=en)

## 2. Add the listing

Under **Grow users → Store presence → Main store listing**, paste the three
English text files and upload the English assets. Add the other four languages
and their corresponding text and images. Use **Weather** as the category.

Set the public support email to **workinghardlyforyou@gmail.com** and the website
to **https://github.com/erikhardlyworking/minimalistRainline**. The support email
is required. [Listing and contact fields](https://support.google.com/googleplay/android-developer/answer/9859152?hl=en)

Start distribution in **Norway, Sweden, Denmark and Finland** if you want the
store availability to match the forecast coverage. This is a distribution choice,
not a replacement for checking radar coverage for each requested coordinate.
The full descriptions explain that some areas and times lack radar data.
[MET's coverage and precipitation data](https://docs.api.met.no/doc/nowcast/datamodel.html)

## 3. Publish the privacy pages

The ready-to-host pages are in `docs/privacy/` and in the pack's `privacy/` folder.
Read them, including the proposed support-email retention wording, before making
them your public policy. They identify Hardly Working and the support email and
explain MET, Android geocoding, local storage, diagnostics and deletion.

The pages are live on GitHub Pages, deployed from **main**, **/docs**. The root
page and all five policy languages were verified publicly accessible.

Use **https://erikhardlyworking.github.io/minimalistRainline/privacy/** in Play
Console **App content → Privacy policy**. Changes pushed to `docs/` are published
through the repository's Pages deployment.

[GitHub Pages deployment settings](https://docs.github.com/en/pages/getting-started-with-github-pages/configuring-a-publishing-source-for-your-github-pages-site)

The repository also has an updated `PRIVACY.md`. Do not paste a local path, an
unpublished Pages address or a PDF into Play. The policy must be publicly readable,
and the app must also expose its privacy policy text or link. Rainline already
has **About → Privacy**; before submitting the final build, reconcile it with the
hosted policy, including support contact and external-provider details.
[Google's privacy-policy requirements](https://support.google.com/googleplay/android-developer/answer/10144311?hl=en)

## 4. Complete App content

Work through the declarations displayed in your dashboard. For this codebase:

- **Ads:** no ads.
- **App access:** no login, subscription or invitation. All functionality is
  available without an account. Use the reviewer instructions below so a reviewer
  outside the coverage area can select a supported fixed place.
- **Content rating:** answer the questionnaire for a weather utility; do not
  invent an age rating before the questionnaire produces one.
- **Target audience:** choose the ages you actually intend to target; this is
  separate from the content rating. No audience selection has been made for you.
  If you intend to target children, review the additional Families requirements.
- **Data safety:** use `data-safety.md`; “no analytics” does **not** mean “no data
  collected.” Coordinates and IP addresses leave the device.
- **Background location:** use `background-location.md`, review the disclosure
  flow and supply the demonstration video. Approval is not automatic.
- **Other declarations:** answer any additional dashboard questions against the
  final bundle. There are no accounts, ads, payments or foreground services in the
  current app. It is an independent weather app, not a government app.

Suggested **App access/reviewer instructions**:

> Rainline does not require an account. Open the app, choose Location mode → Use
> a fixed place, and enter 59.9139, 10.7522 (central Oslo). This works without
> location permission and lets reviewers outside the Nordic coverage area see
> real forecasts. In decimal-comma locales, 59,9139; 10,7522 also works. Add a
> Rainline widget using Add widget or the launcher's widget picker. Tap the widget
> to request a refresh. Touch and hold → Settings/Reconfigure where supported;
> otherwise open Rainline to configure it. Radar availability can vary and a flat
> line is a valid dry forecast. Follow my location is optional and uses background
> location during widget forecast updates when the app is closed.

## 5. Create the signed Android App Bundle

The signed bundle is
`output/release/0.1.13/rainline-0.1.13-14-release.aab`.
The release build, 53 unit tests, release lint, bundle structure and signature
checks passed. The original ZIP's explicitly **UNSIGNED** bundle is for
inspection, not direct upload. Do not upload the personal-testing debug APK.

The upload keystore and password are private local files in
`~/.local/share/rainline/signing/`, outside the repository. Back up both securely.
That folder's README explains how to reuse the key for future uploads.

The matching 30-second review video is
`output/location-review/rainline-background-location.mp4`, with declaration and
YouTube copy in the same folder. It shows actual permission screens and a real
widget refresh using simulated GPS coordinates on an Android 16 emulator.

The app targets **Android 16 / API 36**, meeting the current new-app target level.
[Current target API rule](https://support.google.com/googleplay/android-developer/answer/11926878?hl=en-AU)

For later releases, reuse the existing upload key. Android Studio’s **Build →
Generate Signed App Bundle / APK → Android App Bundle** wizard can select it,
or use the command-line instructions in the private signing folder. Keep the
password out of chat, source files and store packs.

Use **Play App Signing**. The upload key signs the bundle you send to Google;
Play's app-signing key signs the APKs installed by users. The wizard can sign a
release without committing keystore credentials to this Gradle project.
[Android signing workflow](https://developer.android.com/studio/publish/app-signing)
[Play App Signing](https://support.google.com/googleplay/android-developer/answer/9842756?hl=en)

Before signing for submission, finish the privacy/disclosure checks in steps 3
and 4. If app code changes, increment `versionCode` (currently 14), rebuild and
recapture any affected screenshots. Keep source/version notes aligned.

The APK currently on your Samsung is debug-signed. A Play-signed build with the
same package cannot replace it in place. Test first on the emulator or a spare
profile/device; do not uninstall your working widget unexpectedly. Moving that
phone to the Play build will require removing the debug installation and setting
up its widgets again unless an explicit migration is prepared.

## 6. Internal test, then submit production

Create an **Internal testing** release, upload the signed AAB, add the release
notes and your tester email, and follow the test opt-in link. This validates the
actual Play-delivered installation and signing, which the debug APK cannot do.
Check setup, fixed location, optional follow mode, transparent rendering, tap to
refresh, launcher reconfiguration, resize, all languages and no-data behaviour.
Review Play's automated report and resolve material findings before production.
[Testing tracks](https://support.google.com/googleplay/android-developer/answer/9845334?hl=en)

Once the listing, privacy URL, Data safety, content declarations, background
location evidence and signed-build checks are complete, create the production
release with the chosen countries. Review **Publishing overview** carefully and
send the completed release for review. A submitted app is not yet an approved
app. Let the dashboard guide any further review requests.
[Prepare and roll out a release](https://support.google.com/googleplay/android-developer/answer/9859348?hl=en)

## Decisions and work still needed before submission

- Confirm final package ID, price and target audience in your account.
- Keep the published privacy policy aligned with the final release.
- Align the in-app privacy text and location disclosure with the final policy.
- Finalise Data safety, especially the device-dependent geocoding service and
  MET's server-side logging; do not treat the draft as an already filed form.
- Upload the prepared background-location video to an accessible YouTube link.
- Back up the upload key and password; upload the prepared signed release AAB.
- Verify the Play-delivered build, then submit the reviewed production release.

## Reproducing the pack

Sources: `play/listings.json`, `play/privacy.json`, this guide and the two review
notes. Exported graphics and the unsigned bundle stay under ignored `output/`.
No publishing credentials or private keys belong in these files.

Using Java 17 and the configured Android SDK:

```sh
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:bundleRelease :app:lintRelease
python3 scripts/capture-play-assets.py --serial emulator-5554
python3 scripts/build-play-pack.py
```

The capture script is restricted to an Android 13+ emulator without Rainline
widgets or a selected location. It temporarily uses 1080×1920 at 480 dpi and
restores display size, density, rotation, app language and widget defaults.
It never requests location permissions or weather. The test APK can be removed
from that emulator afterwards with `adb -s emulator-5554 uninstall app.rainline.test`.
The pack builder validates text limits, locale completeness and PNG dimensions
and colour modes, generates the privacy pages and review gallery, and writes a
ZIP and checksum manifest.
