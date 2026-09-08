# Metter Weather 0.77

## Improvements

- Restore matching saved Spot and Open-Meteo forecasts immediately when reopening the app, display their age, and refresh in the background. Saved forecasts remain visible if the refresh fails.
- Preserve existing widget refresh schedules when opening the app, and skip scheduling and refresh requests when no widget is installed.
- Retry temporary network and server failures. Pause background requests for missing or invalid Spot credentials until the credentials change.
- Handle quota limits separately, respecting the server's Retry-After delay or using a one-hour cooldown when no valid delay is supplied.
- Avoid unnecessary Spot fallback at the end of BPF forecasts with mixed timestamp intervals. Genuine gaps within the forecast can still use Spot fallback.

## Installation

Download and install `Metter-Weather-v0.77.apk`. This signed release uses version code 77 and the production package `io.github.tychomagnetic.metterweather`.

## Validation

- All 56 unit tests passed, including cache restoration and widget failure regression tests.
