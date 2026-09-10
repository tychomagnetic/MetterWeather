# Metter Weather 0.781

## Widget location reliability

- Precise location is now optional for GPS widgets. Refreshes prefer a fresh precise fix when permitted, then fall back to a fresh approximate fix.
- When background access or a fresh fix is unavailable, the widget tries its last known location so weather updates can continue on a best-effort basis.
- If all location access is denied, GPS refreshes stop; fixed-location widgets remain available without location permission.
- Updated widget settings to explain the fallback behavior and optional precise-location access.

Hourly scheduling and five-hour widget paging are unchanged.

## Installation and verification

- Signed production APK: `Metter-Weather-v0.781.apk`.
- Version 0.781, Android version code 79; upgrades production 0.78 in place.
- All 66 unit tests passed, including approximate-only access, precise-request failure, successful precise requests, cancellation, and cached-location fallback.
