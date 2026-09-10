# Metter Weather 0.78

## Widget improvements

- GPS widgets request a fresh, high-accuracy location for each refresh, with a 30-second timeout. If a fix or the required permission is unavailable, the existing forecast remains visible.
- With Hourly selected, the clock-hour alarm triggers a forecast refresh instead of relying on a separate periodic download schedule. Android may still defer background execution.
- The widget advances Now from cached weather at each hour boundary without waiting for location acquisition or a download. This display update also works when automatic downloads are off.
- Forward and back arrows move five hours per tap, matching the five visible forecast cards.
- Added settings guidance for precise location, background location, and precise widget timing.
- Debug builds and their widget entry are now labelled Metter Weather Dev.

## Setup

For GPS widgets, select GPS Location and Hourly in widget settings. In Android location permissions, enable Use precise location and Allow all the time. Enable Allow precise widget timing (Alarms & reminders) for clock-hour scheduling. Fixed-location widgets do not require location permission.

## Installation and verification

- Signed production APK: `Metter-Weather-v0.78.apk`.
- Version 0.78, version code 78; production package `io.github.tychomagnetic.metterweather`.
- All 64 unit tests passed, including widget clock, paging, and location-permission regression tests.
