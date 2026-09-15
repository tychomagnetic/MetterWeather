These CoverageJSON fixtures are entirely synthetic. They are generated locally
by `generate_synthetic_fixtures.py` from deterministic formulas and contain no
provider response values, identifiers, or coordinates. The dates use 2099 so
the regression does not expire.

The response contains 144 temperature timestamps, 137 feels-like timestamps,
156 wind-speed timestamps, 120 hourly weather-code timestamps and 57 three-hour
weather-code timestamps. Precipitation probabilities have 144 hourly and 137
three-hour timestamps. There are no null range values. The schemas, shapes,
mixed cadences, interval bounds, and coverage horizons model the parser inputs
needed by the tests.

The last instantaneous timestamp is 15:00, while the final weather and
precipitation intervals end at 15:00. Thus the supported hourly display ends
at 14:00 (168 hours total). Checking the extra 15:00 endpoint for missing fields
before trimming it used to trigger an unnecessary Spot request.
