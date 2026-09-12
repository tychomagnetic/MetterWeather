# Metter Weather 0.81

## Widget update recovery

- Widget rendering no longer postpones a pending hourly alarm.
- A durable hourly recovery check and launcher updates recover missed automatic
  updates. All automatic triggers share a limit of one attempt per clock hour.
- Failed location or forecast attempts become eligible again next hour, without
  repeated retries within the same hour.
- A successful manual widget refresh clears saved authentication and quota
  pauses so automatic downloads can resume.
- Android may still delay background execution. Turning automatic refresh off
  cancels automatic downloads while keeping the cached display clock active.

## Installation and verification

- Signed production APK: `Metter-Weather-v0.81.apk`.
- Version 0.81, Android version code 81; upgrades production 0.8 in place.
- All 81 unit and UI regression tests pass. Android lint reports no errors.
