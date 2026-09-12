# Metter Weather 0.8

## Forecast accuracy and reliability

- BPF precipitation chances now use the Met Office consumer thresholds: more
  than 0.1 mm in one hour and more than 0.3 mm in three hours. The app no longer
  substitutes the much more sensitive trace-rain probability.
- Three-hour BPF precipitation values are labelled in the hourly forecast, and
  expanded details show the full forecast interval in local time.
- Older BPF caches containing trace-threshold probabilities are ignored. The
  eight most recently fetched BPF locations are retained in the refreshed cache.
- Incomplete provider timestamps are omitted instead of being filled with
  plausible default weather values. Daily forecasts remain available when a
  provider has no usable hourly coverage, with the missing hourly data clearly
  identified in the interface.
- Forecast and widget cache writes now report failures accurately, and widget
  location handling avoids a permission-state race during refresh.

## Installation and verification

- Signed production APK: `Metter-Weather-v0.8.apk`.
- Version 0.8, Android version code 80; upgrades production 0.781 in place.
- All 77 unit and UI regression tests pass. Android lint reports no errors.
