package io.github.tychomagnetic.metterweather.data.util

import io.github.tychomagnetic.metterweather.data.model.BpfCoverageCollection
import io.github.tychomagnetic.metterweather.data.model.PrecipitationPeriod
import java.time.Instant
import kotlin.math.abs

/** Consumer forecast thresholds are 0.1 mm/hour and 0.3 mm/three hours.
 * CoverageJSON accumulation thresholds are expressed in metres of water.
 */
internal object BpfPrecipitationUtils {
    data class Probability(val value: Double, val period: PrecipitationPeriod)

    fun read(collection: BpfCoverageCollection, intervalHours: Int): Map<String, Probability> {
        require(intervalHours == 1 || intervalHours == 3)
        val parameter = "probabilityOfLweThicknessOfPrecipitationAmountAboveThresholdSumPt0${intervalHours}h"
        val thresholdMetres = if (intervalHours == 1) 0.0001 else 0.0003
        val coverage = collection.coverages.firstOrNull { parameter in it.ranges } ?: return emptyMap()
        val axes = coverage.domain?.axes ?: return emptyMap()
        val range = coverage.ranges.getValue(parameter)
        val thresholdAxis = "${parameter}Values"
        val thresholdIndex = axes[thresholdAxis]?.values?.indexOfFirst { raw ->
            val label = raw?.toString()?.trim().orEmpty()
            label.startsWith(">") && label.removePrefix(">").toDoubleOrNull()?.let {
                abs(it - thresholdMetres) < 1e-10
            } == true
        } ?: -1
        // Never silently select the first (trace-rain) threshold if the desired
        // threshold is absent. The repository's normal missing-data policy applies.
        if (thresholdIndex < 0 || thresholdAxis !in range.axisNames || "t" !in range.axisNames ||
            range.axisNames.size != range.shape.size
        ) return emptyMap()
        val timeAxis = axes["t"] ?: return emptyMap()
        val bounds = timeAxis.bounds
        if (bounds.isNotEmpty() && bounds.size != timeAxis.values.size * 2) return emptyMap()

        return buildMap {
            timeAxis.values.forEachIndexed timeLoop@ { timeIndex, rawTime ->
                var index = 0
                var stride = 1
                range.axisNames.forEachIndexed { dimension, axis ->
                    val size = range.shape[dimension]
                    val coordinate = when (axis) {
                        "t" -> timeIndex
                        thresholdAxis -> thresholdIndex
                        else -> 0
                    }
                    if (size <= 0 || coordinate >= size) return@timeLoop
                    index += coordinate * stride
                    stride *= size
                }
                val value = range.values.getOrNull(index)?.takeIf { it.isFinite() && it in 0.0..1.0 }
                    ?: return@timeLoop
                val end = TimezoneUtils.parseIsoToMillis(
                    if (bounds.isEmpty()) rawTime?.toString() else bounds[timeIndex * 2 + 1]?.toString()
                ) ?: return@timeLoop
                val start = if (bounds.isEmpty()) end - intervalHours * 3_600_000L else
                    TimezoneUtils.parseIsoToMillis(bounds[timeIndex * 2]?.toString()) ?: return@timeLoop
                if (end - start != intervalHours * 3_600_000L) return@timeLoop
                val period = PrecipitationPeriod(
                    start = Instant.ofEpochMilli(start).toString(),
                    end = Instant.ofEpochMilli(end).toString(),
                    hours = intervalHours,
                    thresholdMm = thresholdMetres * 1000.0
                )
                // Preserve the period when displaying its probability on each
                // constituent hour; it is not an independent hourly probability.
                repeat(intervalHours) { hour ->
                    put(Instant.ofEpochMilli(start + hour * 3_600_000L).toString(), Probability(value, period))
                }
            }
        }
    }
}
