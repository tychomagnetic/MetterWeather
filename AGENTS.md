# Agent guide: Metter Weather

## Scope and purpose

This guide applies to the whole repository. Metter Weather is a native Android
weather app written in Kotlin with Jetpack Compose and Material 3. It provides
current, hourly and seven-day forecasts, location search and favourites, unit and
theme settings, weather maps, API diagnostics, and an interactive home-screen
widget. It is an independent project, not an official Met Office app.

Read the relevant implementation and tests before changing behavior. `README.md`
explains the product and user setup; `RELEASE_NOTES_*.md` describe past releases.
Use the Gradle files and source as the authority when documentation differs.

## Build and project identity

- This is a single-module Gradle project: root name `MetterWeather`, module `:app`.
- Source files live under `app/src/main/java/com/example/`, but their Kotlin
  package is **`io.github.tychomagnetic.metterweather`**. Tests have the same
  path/package mismatch. Do not introduce `com.example` imports or rename the
  source tree as an incidental cleanup.
- Release application ID and namespace: `io.github.tychomagnetic.metterweather`.
  Debug application ID: `io.github.tychomagnetic.metterweather.dev`, allowing
  debug and production installations to coexist.
- `app/build.gradle.kts` owns SDK levels, app versions, build types and signing.
  Currently: minimum SDK 26, target SDK 36, compile SDK 36.1.
- `gradle/libs.versions.toml` owns dependency/plugin versions. The wrapper is
  Gradle 9.3.1 and the Android Gradle plugin is 9.1.1. Use the wrapper, not a
  globally installed Gradle. Preserve the existing plugin arrangement.
- Java source/target compatibility is 11; this is not the Gradle runtime JDK
  requirement. Use a JDK compatible with the configured Gradle/Android plugin,
  normally the bundled JDK from a compatible Android Studio installation.
- Configure the local Android SDK through Android Studio or `local.properties`.
  Install the SDK components requested by Gradle sync.
- Despite the README's generic environment instructions, the checked-in build
  does not load a `.env` file and there is no tracked `.env.example`. Weather
  credentials are entered in the app, not embedded at build time.

Run these commands from the repository root in PowerShell:

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
```

On Unix-like systems, use `./gradlew` with the same tasks. The debug APK is
`app/build/outputs/apk/debug/app-debug.apk`. With an emulator or device connected:

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
.\gradlew.bat :app:connectedDebugAndroidTest
```

Debug signing explicitly uses the ignored root `debug.keystore`, with the
standard Android debug alias/passwords in the build file. A fresh checkout may
need a local standard debug keystore at that path. Release signing uses
`KEYSTORE_PATH` (default `my-upload-key.jks`), `STORE_PASSWORD`, `KEY_PASSWORD`,
and alias `upload`. Do not replace signing keys or change package IDs to work
around installation/build issues. Do not build or publish a release unless the
task requires it.

## Code map and data flow

All paths below are relative to `app/src/main/java/com/example/`.

| Area | Responsibility |
| --- | --- |
| `MainActivity.kt` | Compose entry point, lifecycle-aware state collection, theme/system bars, visible minute clock checks. |
| `ui/WeatherViewModel.kt` | `WeatherUiState` exposed through `StateFlow`; location/source selection, settings, loading, cache reuse, refresh and diagnostics. |
| `ui/WeatherScreen.kt`, `ui/SettingsScreen.kt` | Main screen and settings orchestration. |
| `ui/components/`, `ui/theme/` | Reusable Compose cards, dialogs and detail sheets; colors, typography and theme. |
| `ui/ForecastCache.kt`, `ui/HeroWeatherPresentation.kt` | Cache identity/age helpers and hero-card presentation decisions. |
| `data/repository/WeatherRepository.kt` | Provider requests, response validation and mapping, fallback, geocoding, and API diagnostics. This is a large file; inspect the specific path you are changing. |
| `data/remote/` | Retrofit service interfaces and `ApiServiceProvider`, which shares Moshi and HTTP clients across app/widget use. |
| `data/model/` | Provider DTOs and common models such as `WeatherReport`, locations, weather codes, units and source enums. |
| `data/local/PreferencesManager.kt` | SharedPreferences settings, Moshi-serialized reports/caches, credential storage and preference flows. |
| `data/util/` | Forecast clock/timezone handling, BPF interval alignment, representative daily conditions and map manifest utilities. |
| `ui/MapImagesScreen.kt`, `ui/MapImagesViewModel.kt`, `data/repository/MapImagesRepository.kt` | Separate map catalog, layer/time selection, image fetching, preloading and caching flow. |
| `widget/` | Glance widget rendering/actions, refresh workers, scheduling, clock redraws, location selection and failure policy. |

The usual flow is Compose UI -> ViewModel -> repository -> shared Retrofit
services -> provider DTOs -> `WeatherReport` -> state and local cache. Dependencies
are constructed directly; repository constructors also provide test seams for
fake services. Room and DataStore dependencies exist, but current preferences
and forecast persistence use `SharedPreferences`; do not assume a Room database.

Resources, permission declarations and receivers live in `app/src/main/res/`
and `app/src/main/AndroidManifest.xml`. The debug app label has a resource override
under `app/src/debug/res/`.

## Behavior to preserve

### Providers, fallback and diagnostics

- `ForecastSource` is the user's selection; `WeatherDataSource` describes the
  report actually returned. Keep them distinct, especially after fallback.
- Global Spot, BPF (Blended Probabilistic Forecast), and Map Images use separate
  subscription credentials. Open-Meteo provides account-free forecasts and
  geocoding.
- Foreground forecast fallback is BPF -> Spot -> Open-Meteo when available;
  Spot can fall back to Open-Meteo. An explicit Open-Meteo selection should not
  trigger Met Office requests.
- BPF can fill genuine missing coverage from Spot and record
  `partialFallbackSource`. Preserve response-location validation and accurate
  provider attribution; do not silently label fallback data as the selected source.
- BPF normally requires two requests (percentiles and probabilities). Fields
  have different time axes and hourly/three-hour intervals. Trim unsupported
  terminal hours before testing for missing data so a valid response does not
  cause unnecessary Spot calls. `BpfIntervalUtils.kt` and the fixture README
  explain this regression.
- `BpfPrecipitationUtils` selects the 0.0001 m (hourly) or 0.0003 m
  (three-hour) accumulation threshold. Never substitute the trace `>0.0`
  threshold when one is missing. Keep the selected probability's interval
  metadata through caching and UI display; three-hour values need a period
  label. BPF cache v6 invalidates earlier trace-threshold reports.
- Diagnostics target the chosen diagnostic provider and must not silently
  perform normal forecast fallback. API key tests consume real requests when
  run against a live provider; prefer fake services and sanitized fixtures.
- Preserve the existing Met Office/Open-Meteo attribution and provider links.

### Caches, units and time

- BPF forecasts are cached per location for two hours. Manual refresh bypasses
  that cache, with the newest eight locations retained. Switching locations
  should reuse a fresh matching report. Do not fetch every favourite on startup.
- Cache matching includes coordinates and source, not just the display name.
  Keep foreground forecast, BPF location, widget and map caches distinct.
- `WeatherViewModel` handles saved-report display while refreshing, visible
  forecast aging, and automatic refresh retries. Inspect those paths together
  when changing refresh behavior; avoid duplicate loads or stale job results.
- Common model fields use Celsius, mph, hPa and metres where named. Convert
  provider values during mapping and user units during presentation; avoid
  double conversion. Preserve day/night weather-code distinctions.
- Spot hourly timestamps are mapped through `SpotHourlyMapper`. Incomplete
  timestamps are omitted, and days with daily-only coverage display an explicit
  unavailable message. Do not restore synthetic hourly curves or plausible
  default weather values in repositories or UI components.
- Use `TimezoneUtils` and `WeatherClockUtils` for forecast location time,
  daylight-saving changes, current-hour selection and daily rollover. Do not
  substitute the phone's timezone or compare display labels as timestamps.

### Home-screen widget and maps

- The widget uses `getSpotWidgetReport` and Global Spot only, independently of
  the main app source. Do not introduce BPF or Open-Meteo fallback into it.
- Keep clock redraws separate from network refreshes. Cached hours must advance
  even when automatic downloads are off. Hourly alarms coordinate automatic
  refresh. A durable hourly recovery worker and launcher updates share a
  persisted hourly attempt gate with the alarm, so recovery cannot duplicate
  downloads in the same hour. Failed attempts become eligible next hour.
- `WidgetClock`, `WidgetRefreshManager`, `WidgetRefreshWorker` and receivers
  jointly handle scheduling. Preserve off-state cancellation, reboot/timezone
  handling, and operation without exact-alarm permission.
- Keep credential errors, quota errors and transient failures distinct through
  `WidgetFailurePolicy`; do not retry all failures indiscriminately.
- Fixed-location mode needs no location permission. GPS mode supports
  approximate access, optional background access and last-known-location
  fallback. Preserve bounded location attempts and permission checks.
- Widget arrows navigate five hours, matching its five visible cards.
- Map catalogs use compatible PNG orders and immutable frames from the newest
  run. Manifest freshness follows 00:00/12:00 UTC boundaries. Preserve cached
  display and warnings when a newer run or the network is unavailable, as well
  as bounded bitmap caching and cancellation of obsolete loading work.

## Tests and validation

Local tests are under `app/src/test/java/com/example/`; device tests are under
`app/src/androidTest/java/com/example/`. The project uses JUnit 4, Robolectric,
Compose UI tests and Roborazzi. Many Robolectric tests specify SDK 36.

For a focused regression, use the actual Kotlin package in the test filter:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "io.github.tychomagnetic.metterweather.WeatherRepositoryFallbackTest"
```

- Forecast/provider changes: `WeatherRepositoryFallbackTest`,
  `BpfIntervalUtilsTest`, `WeatherCodeMappingTest`,
  `RepresentativeWeatherUtilsTest` and `ForecastRefreshTest`.
- Time/presentation changes: `TimezoneUtilsTest`, `WeatherClockUtilsTest`,
  `HeroWeatherPresentationTest` and `ThemeModeTest`.
- Widget changes: `WidgetClockTest`, `WidgetBackgroundLocationTest` and the
  widget failure/refresh cases in `ForecastRefreshTest`.
- Map changes: `MapImagesUtilsTest`; use device/emulator checks for interactive
  loading behavior beyond the utility tests.
- Sanitized BPF regression fixtures are in `app/src/test/resources/bpf/`.
  Their timestamps were intentionally shifted to 2099 to prevent expiry;
  read the adjacent README before editing them.
- `GreetingScreenshotTest` actually captures `HeroWeatherCard` with Pixel 8
  qualifiers into `app/src/test/screenshots/greeting.png`. Inspect any image
  change and do not accept a regenerated baseline without visual review.

Run checks appropriate to the change. For substantial app changes, run debug
assembly, relevant unit tests and lint; use an emulator/device for affected UI,
widget or permission flows. Documentation-only edits need content and diff
checks, not an Android build. Report what ran and any missing JDK/SDK, dependency
download, device or credential limitation; do not claim unrun checks passed.
Test and lint reports are written under `app/build/reports/`.

## Change and secret handling

- Check the working tree before editing and preserve unrelated user changes.
  Keep changes focused, follow nearby Kotlin/Compose formatting, and use
  `gradle/libs.versions.toml` for dependency changes.
- Keep network/file work off the UI thread and preserve coroutine cancellation.
  Prefer existing mapping/time helpers and repository test seams over new
  global clients or duplicate conversion logic.
- Preserve persisted preference keys and enum compatibility, or provide an
  explicit migration when changing them. Production credentials use Android
  Keystore encryption; the plaintext test fallback is restricted to Robolectric.
- Never commit or print API keys, signing passwords, keystores, `local.properties`,
  `.env`, or `release-signing.env.ps1`. Ignored `*.key` files and `.api-fixtures/`
  may exist locally; they are not public test fixtures. Avoid broad reads of
  ignored files and sanitize diagnostics before adding them to tests or docs.
- Do not hand-edit generated `build/`, `.gradle/`, `.kotlin/` or IDE state, or
  change versions/signing as part of unrelated work. Update user documentation
  when setup or visible behavior changes.
