# Metter Weather 0.84

## More representative daily conditions

- Future-day headlines use the forecast provider's daily weather summary when
  one is available. BPF daily summaries are included in the existing forecast
  request, so this does not add another API call.
- The top forecast card, day carousel and hourly detail page now show the same
  daily condition.
- When a provider does not supply a daily summary, wet hourly conditions are
  considered together. Several hours of rain, showers, sleet, hail, snow or
  thunder can therefore define the day even when dry hours are more numerous.
- An isolated shower does not automatically define an otherwise dry day, and
  today's headline continues to follow the remaining forecast.
- BPF 24-hour summaries are accepted only when they align with a complete local
  calendar day, including daylight-saving transitions.

## Installation

- Signed production APK: `Metter-Weather-v0.84.apk`.
- Version 0.84, Android version code 84; upgrades production 0.83 in place.

## Verification

- All 101 unit and UI regression tests pass, including provider-summary caching,
  mixed wet conditions, isolated showers and consistent top-card presentation.
- Debug and release lint report no errors.
