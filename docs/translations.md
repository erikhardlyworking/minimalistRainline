# Translations

Rainline 0.1.11 uses Android string resources for its interface:

| Language | Resource directory |
| --- | --- |
| English (default/fallback) | `app/src/main/res/values` |
| Norwegian Bokmål | `app/src/main/res/values-nb` |
| Swedish | `app/src/main/res/values-sv` |
| Finnish | `app/src/main/res/values-fi` |
| Danish | `app/src/main/res/values-da` |

Each translation has 186 entries. `app_name` and the language-neutral timestamp
wrapper are intentionally non-translatable. The original MIT licence and
technical diagnostic fields remain in English; the diagnostic dialog explains
this in the selected language. API identifiers, URLs, colour codes and stored
place names are not translated.

Android chooses the resources from the device language. On Android 13+, the
explicit `locales_config.xml` also enables the system's per-app language picker,
linked from **About → Language**. This uses the native
[per-app language support](https://developer.android.com/guide/topics/resources/app-languages)
without additional runtime libraries. A locale-change broadcast redraws placed
widgets from the cache; it does not fetch weather.

`UiText` formats visible times and numbers with the context's locale, including
per-app overrides. Error categories and generic location labels are resolved
when displayed, so existing preferences do not pin the interface to the language
used when they were saved. Decimal-comma coordinate pairs use a semicolon;
decimal-point pairs can still use a comma.

Edit the XML files directly when improving translations. Keep string IDs,
numbered formatting arguments such as `%1$s` and `%2$d`, and hex colour examples
intact. Translations can reorder arguments. Keep permission guidance consistent
with Android's wording; the background-location explanation inserts the
platform's own permission-option label.

Run `python3 scripts/check-localizations.py` and Android lint after editing.
The emulator instrumentation option `-e locales true` checks all five app
languages, English fallback, formatting, decimal input and layouts at 360 dp
width. It requires an Android 13+ emulator without a configured Rainline
location or placed widgets, and restores the previous app-language choice.
It makes no forecast requests and does not change the device language or any
permissions. Visual captures are local test artifacts under `output/`.
