# Metter Weather 0.83

## More useful, compact weather widgets

- Five hourly cards fit from 260×90dp, targeting a 4×1 home-screen layout.
  Taller widgets show Now, previous/next and refresh controls at the top right;
  short widgets hide controls and return to the current hours. Launcher grid
  dimensions vary by device.
- Narrow and wide widgets display two and eight hours respectively. Navigation
  moves one visible page, with 48dp touch targets for cards and controls.
- Hour cards show average wind speed in the selected unit and compass direction.
  Precipitation probabilities of 30% or more appear in blue.
- Widget colours follow the device theme, with system corner radii on supported
  Android versions and light/dark previews in the widget picker.
- Manual refresh shows queued and running feedback from WorkManager, avoiding
  a saved loading flag that could get stuck after interrupted work.
- Restored GPS place-name lookup with a two-second wait limit. More detailed
  settlement names take precedence over county names. UK formatted addresses
  provide a town fallback when Android omits the town field

## Installation

- Signed production APK: `Metter-Weather-v0.83.apk`.
- Version 0.83, Android version code 83; upgrades production 0.82 in place.

## Verification

- All 90 unit and widget regression tests pass, including light/dark layouts,
  minimum touch targets, wind units, precipitation colours and place naming.
- Debug and release lint report no errors (81 warnings).

