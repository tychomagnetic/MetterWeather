# Metter Weather 0.82

## Reliable, Play-compliant widget updates

- Removed the exact-alarm permission and its special-access setup. The widget
  now uses a non-waking, inexact display rollover, avoiding exact-alarm Play
  policy concerns.
- WorkManager handles hourly network downloads with a network constraint. The
  cached display clock remains active when automatic downloads are off.
- Manual widget refresh works even when scheduled refresh is off and remains
  queued if that setting changes.
- GPS widget refreshes no longer wait for reverse geocoding; they use
  **Current Location** so a location name lookup cannot block forecast data.
- One widget instance failing to redraw no longer stops other widget instances
  from updating.

## Installation and verification

- Signed production APK: `Metter-Weather-v0.82.apk`.
- Version 0.82, Android version code 82; upgrades production 0.81 in place.
- All 85 unit and UI regression tests pass. Android lint reports no errors.
