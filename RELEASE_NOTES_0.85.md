# Metter Weather 0.85

## More reliable refreshes and daily summaries

- Fixed an overnight rollover race that could leave Tomorrow selected after a
  fresh forecast replaced the previous day's cached timeline.
- Spot, BPF and Open-Meteo now reuse a matching forecast for two hours during
  automatic loading. Stale forecasts refresh normally, while a manual refresh
  still requests new data immediately.
- Future-day hero cards now emphasise the day's high and low instead of a
  point-in-time midday temperature. Their wind headline uses the strongest
  hourly wind and its corresponding direction.
- Replaced provider-derived BPF regression payloads with deterministic,
  locally generated synthetic CoverageJSON fixtures. Their mixed cadence,
  interval bounds and incomplete terminal-hour edge cases are preserved.

## Installation

- Signed production APK: `Metter-Weather-v0.85.apk`.
- Version 0.85, Android version code 85; upgrades production 0.84 in place.

## Verification

- Unit and UI regression tests pass, including rollover, forecast-cache,
  future-day presentation and synthetic BPF parsing coverage.
- Debug and release builds and lint checks complete successfully.
