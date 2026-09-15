"""Generate fictional CoverageJSON fixtures for BPF parser regression tests.

All coordinates, identifiers, and meteorological values are deterministic,
locally generated test data. No provider response is used by this generator.
"""

import json
import math
from datetime import datetime, timedelta, timezone
from pathlib import Path


OUTPUT_DIR = Path(__file__).resolve().parent
START = datetime(2099, 9, 8, 15, tzinfo=timezone.utc)
PERCENTILES = [5, 10, 15, 20, 25, 30, 40, 50, 60, 70, 75, 80, 85, 90, 95]
THRESHOLDS = [
    ">0.0", ">3.0E-5", ">9.0E-5", ">1.0E-4", ">2.5E-4", ">3.0E-4",
    ">5.0E-4", ">0.001", ">0.002", ">0.003", ">0.004", ">0.008",
    ">0.012", ">0.016", ">0.02", ">0.025", ">0.03", ">0.04", ">0.05",
    ">0.075", ">0.1", ">0.125", ">0.15", ">0.2", ">0.25", ">0.3", ">0.4",
]


def iso(moment):
    return moment.isoformat(timespec="seconds").replace("+00:00", "Z")


def at_offsets(offsets):
    return [START + timedelta(hours=offset) for offset in offsets]


def mixed_144_times():
    return at_offsets(list(range(131)) + [132] + list(range(135, 169, 3)))


def mixed_137_times():
    return at_offsets(list(range(121)) + list(range(123, 169, 3)))


def interval_bounds(times, hours):
    return [value for end in times for value in (iso(end - timedelta(hours=hours)), iso(end))]


def domain(times, extra_axis_name, extra_axis_values, bounds=None):
    time_axis = {"values": [iso(value) for value in times]}
    if bounds is not None:
        time_axis["bounds"] = bounds
    return {
        "type": "Domain",
        "axes": {
            "locationId": {"values": ["fictional-grid-point"]},
            extra_axis_name: {"values": extra_axis_values},
            "t": time_axis,
            "x": {"values": [-0.1278]},
            "y": {"values": [51.5074]},
            "z": {"values": [10.0]},
        },
    }


def coverage(parameter, times, axis_name, axis_values, values, bounds=None):
    return {
        "type": "Coverage",
        "domain": domain(times, axis_name, axis_values, bounds),
        "ranges": {
            parameter: {
                "type": "NdArray",
                "dataType": "float",
                "axisNames": [axis_name, "t"],
                "shape": [len(axis_values), len(times)],
                "values": values,
            }
        },
    }


def percentile_values(times, parameter):
    values = []
    for index, moment in enumerate(times):
        hour_angle = 2.0 * math.pi * (moment.hour - 6) / 24.0
        if parameter == "airTemperature1p5m":
            median, spread = 284.0 + 4.5 * math.sin(hour_angle), 0.035
        elif parameter == "feelsLikeTemperature1p5m":
            median, spread = 283.2 + 4.2 * math.sin(hour_angle), 0.04
        elif parameter == "airPressureAtSeaLevel":
            median, spread = 100_800.0 + 420.0 * math.sin(index / 19.0), 3.0
        elif parameter == "relativeHumidity1p5m":
            median, spread = 0.68 - 0.14 * math.sin(hour_angle), 0.0015
        elif parameter == "ultravioletIndex":
            median = max(0.0, 5.5 * math.sin(math.pi * (moment.hour - 6) / 14.0))
            spread = 0.005
        elif parameter == "visibilityInAir1p5m":
            median, spread = 14_000.0 + 2_500.0 * math.sin(index / 13.0), 12.0
        elif parameter == "windSpeed10m":
            median, spread = 4.3 + 1.1 * math.sin(index / 8.0), 0.012
        elif parameter.startswith("windSpeedOfGust10mMaximum"):
            median, spread = 7.6 + 1.6 * math.sin(index / 7.0), 0.018
        else:
            raise ValueError(parameter)
        for percentile in PERCENTILES:
            fictional = median + (percentile - 50) * spread
            if parameter == "relativeHumidity1p5m":
                fictional = min(0.98, max(0.2, fictional))
            if parameter == "ultravioletIndex":
                fictional = max(0.0, fictional)
            values.append(round(fictional, 5))
    return values


def scalar_values(times, parameter):
    if parameter == "windFromDirection10mMean":
        return [float((205 + index * 7) % 360) for index in range(len(times))]
    codes = [1, 3, 7, 8, 10, 12, 14, 15]
    return [float(codes[(index // 8) % len(codes)]) for index in range(len(times))]


def probability_values(times, target_index, first_target_value):
    values = []
    for time_index in range(len(times)):
        target = min(0.86, first_target_value + 0.09 * (1.0 + math.sin(time_index / 9.0)))
        for threshold_index in range(len(THRESHOLDS)):
            fictional = target - 0.025 * (threshold_index - target_index)
            values.append(round(min(0.99, max(0.01, fictional)), 5))
    values[target_index] = first_target_value
    return values


def build_percentiles():
    mixed_144 = mixed_144_times()
    mixed_137 = mixed_137_times()
    wind_156 = at_offsets(list(range(143)) + list(range(144, 169, 2)))
    hourly_weather = at_offsets(range(120))
    three_hourly = at_offsets(range(0, 169, 3))
    hourly_gust = at_offsets(range(122))
    coverages = []
    for parameter, times in (
        ("airPressureAtSeaLevel", mixed_144),
        ("airTemperature1p5m", mixed_144),
        ("feelsLikeTemperature1p5m", mixed_137),
        ("relativeHumidity1p5m", mixed_144),
        ("ultravioletIndex", mixed_137),
        ("visibilityInAir1p5m", mixed_137),
    ):
        coverages.append(coverage(parameter, times, "percentiles", PERCENTILES, percentile_values(times, parameter)))
    coverages.extend([
        coverage("weatherCodePt01h", hourly_weather, "percentiles", [50],
                 scalar_values(hourly_weather, "weatherCodePt01h"), interval_bounds(hourly_weather, 1)),
        coverage("weatherCodePt03h", three_hourly, "percentiles", [50],
                 scalar_values(three_hourly, "weatherCodePt03h"), interval_bounds(three_hourly, 3)),
        coverage("windFromDirection10mMean", mixed_144, "percentiles", [50],
                 scalar_values(mixed_144, "windFromDirection10mMean")),
        coverage("windSpeed10m", wind_156, "percentiles", PERCENTILES,
                 percentile_values(wind_156, "windSpeed10m")),
        coverage("windSpeedOfGust10mMaximumPt01h", hourly_gust, "percentiles", PERCENTILES,
                 percentile_values(hourly_gust, "windSpeedOfGust10mMaximumPt01h"), interval_bounds(hourly_gust, 1)),
        coverage("windSpeedOfGust10mMaximumPt03h", three_hourly, "percentiles", PERCENTILES,
                 percentile_values(three_hourly, "windSpeedOfGust10mMaximumPt03h"), interval_bounds(three_hourly, 3)),
    ])
    return {"type": "CoverageCollection", "coverages": coverages}


def build_probabilities():
    coverages = []
    for parameter, times, hours, target_index, target_value in (
        ("probabilityOfLweThicknessOfPrecipitationAmountAboveThresholdSumPt01h", mixed_144_times(), 1, 3, 0.42),
        ("probabilityOfLweThicknessOfPrecipitationAmountAboveThresholdSumPt03h", mixed_137_times(), 3, 5, 0.63),
    ):
        axis_name = f"{parameter}Values"
        coverages.append(coverage(
            parameter, times, axis_name, THRESHOLDS,
            probability_values(times, target_index, target_value), interval_bounds(times, hours)
        ))
    return {"type": "CoverageCollection", "coverages": coverages}


def write_fixture(name, payload):
    (OUTPUT_DIR / name).write_text(
        json.dumps(payload, separators=(",", ":")) + "\n",
        encoding="utf-8",
    )


if __name__ == "__main__":
    write_fixture("percentiles.json", build_percentiles())
    write_fixture("probabilities.json", build_probabilities())
