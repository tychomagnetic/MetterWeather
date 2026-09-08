These CoverageJSON fixtures come from a successful two-call London BPF pull on
8 September 2026, using the app's production parameter lists and the UTC window
2026-09-08T15:00:00Z/2026-09-15T15:00:00Z. Only coverage domains and ranges are
retained; the year is shifted to 2099 so the regression does not expire.
No API credentials or request headers are included.

The response contains 144 temperature timestamps, 137 feels-like timestamps,
156 wind-speed timestamps, 120 hourly weather-code timestamps and 57 three-hour
weather-code timestamps. Precipitation probabilities have 144 hourly and 137
three-hour timestamps. There are no null range values.

The last instantaneous timestamp is 15:00, while the final weather and
precipitation intervals end at 15:00. Thus the supported hourly display ends
at 14:00 (168 hours total). Checking the extra 15:00 endpoint for missing fields
before trimming it used to trigger an unnecessary Spot request.
