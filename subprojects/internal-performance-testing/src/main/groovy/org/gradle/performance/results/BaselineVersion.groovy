/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.performance.results

import groovy.transform.CompileStatic
import org.gradle.performance.measure.Amount
import org.gradle.performance.measure.DataSeries
import org.gradle.performance.measure.Duration

import static PrettyCalculator.toMillis

/**
 * Allows comparing one Gradle version's results against another, using the Mann–Whitney U test with a minimum confidence of 99%.
 *
 * We prefer the Mann-Whitney U test over Student's T test, because performance data often has a non-normal distribution.
 * Many scenarios have 2 maxima, one for a typical build and another for builds with a major GC pause.
 *
 * https://en.wikipedia.org/wiki/Mann%E2%80%93Whitney_U_test
 */
@CompileStatic
class BaselineVersion implements VersionResults {
    private static final double MINIMUM_CONFIDENCE = 0.99

    final String version
    final MeasuredOperationList results = new MeasuredOperationList()

    BaselineVersion(String version) {
        this.version = version
        results.name = "Gradle $version"
    }

    String getSpeedStatsAgainst(String displayName, MeasuredOperationList current) {
        def sb = new StringBuilder()
        def thisVersionMean = results.totalTime.median
        def currentVersionMean = current.totalTime.median
        if (currentVersionMean && thisVersionMean) {
            if (significantlyFasterThan(current)) {
                sb.append "Speed $displayName: we're slower than $version"
            } else if (significantlySlowerThan(current)) {
                sb.append "Speed $displayName: AWESOME! we're faster than $version"
            } else {
                sb.append "Speed $displayName: Results were inconclusive"
            }
            String confidencePercent = DataSeries.confidenceInDifference(results.totalTime, current.totalTime) * 100 as int
            sb.append(" with " + confidencePercent + "% confidence.\n")

            def meanDiff = currentVersionMean - thisVersionMean
            def desc = meanDiff > Duration.millis(0) ? "slower" : "faster"
            sb.append("\nDifference: ${meanDiff.abs().format()} $desc (${toMillis(meanDiff.abs())}), ${PrettyCalculator.percentChange(currentVersionMean, thisVersionMean)}%\n")

            def sortedMean = calculateSortedMean(current)
            sb.append("Sorted mean: \tstandardError: " + standardError(sortedMean) + ", \t measurementMean: " + measurementMean(sortedMean) + ", " + sortedMean + "\n")

            def mean = calculateMean(current)
            sb.append("Mean: \tstandardError: " + standardError(mean) + ", \t measurementMean: " + measurementMean(mean) + ", " + mean + "\n")

            def absDiff = calculateAbsDiff(current)
            sb.append("Abs Diff: \tstandardError: " + standardError(absDiff) + ", \t measurementMean: " + measurementMean(absDiff) + ", " + absDiff + "\n")

            def diffSquared = calculateDiffSquared(current)
            sb.append("Diff squared: \tstandardError: " + standardError(diffSquared) + ", \t measurementMean: " + measurementMean(diffSquared) + ", " + diffSquared + "\n")

            def ratio = calculateRatio(current)
            sb.append("Ratio: \tstandardError: " + standardError(ratio) + ", \t measurementMean: " + measurementMean(ratio) + ", " + ratio + "\n")

            sb.append(current.speedStats)
            sb.append(results.speedStats)
            sb.toString()
        } else {
            sb.append("Speed measurement is not available (probably due to a build failure)")
        }
    }

    private BigDecimal standardError(List<Amount<Duration>> values) {
        new DataSeries(values).standardError.value
    }

    private Amount measurementMean(List<Amount<Duration>> values) {
        def sum = values*.value.sum() as BigDecimal
        def mean = sum / values.size()
        Amount.valueOf(mean, values.first().units);
    }

    List<Amount<Duration>> calculateDiffSquared(MeasuredOperationList current) {
        assert results.size() == current.size()

        def (resultsTotalTime, currentTotalTime) = [results.totalTime, current.totalTime]
        def unit = resultsTotalTime.first().units
        assert currentTotalTime.first().units == unit

        def zippedValues = [resultsTotalTime.asDoubleList().sort(), currentTotalTime.asDoubleList().sort()].transpose()
        zippedValues.collect { double resultsTime, double currentTime ->
            Amount.valueOf((resultsTime - currentTime)**2 as BigDecimal, unit)
        }
    }

    List<Amount<Duration>> calculateSortedMean(MeasuredOperationList current) {
        assert results.size() == current.size()

        def (resultsTotalTime, currentTotalTime) = [results.totalTime, current.totalTime]
        def unit = resultsTotalTime.first().units
        assert currentTotalTime.first().units == unit

        def zippedValues = [resultsTotalTime.asDoubleList().sort(), currentTotalTime.asDoubleList().sort()].transpose()
        zippedValues.collect { double resultsTime, double currentTime ->
            Amount.valueOf((resultsTime + currentTime) / 2 as BigDecimal, unit)
        }
    }

    List<Amount<Duration>> calculateMean(MeasuredOperationList current) {
        assert results.size() == current.size()

        def (resultsTotalTime, currentTotalTime) = [results.totalTime, current.totalTime]
        def unit = resultsTotalTime.first().units
        assert currentTotalTime.first().units == unit

        def zippedValues = [resultsTotalTime.asDoubleList(), currentTotalTime.asDoubleList()].transpose()
        zippedValues.collect { double resultsTime, double currentTime ->
            Amount.valueOf((resultsTime + currentTime) / 2 as BigDecimal, unit)
        }
    }

    List<Amount<Duration>> calculateAbsDiff(MeasuredOperationList current) {
        assert results.size() == current.size()

        def (resultsTotalTime, currentTotalTime) = [results.totalTime, current.totalTime]
        def unit = resultsTotalTime.first().units
        assert currentTotalTime.first().units == unit

        def zippedValues = [resultsTotalTime.asDoubleList(), currentTotalTime.asDoubleList()].transpose()
        zippedValues.collect { double resultsTime, double currentTime ->
            Amount.valueOf(Math.abs(resultsTime - currentTime) as BigDecimal, unit)
        }
    }

    // TODO dedup
    List<Amount<Duration>> calculateRatio(MeasuredOperationList current) {
        assert results.size() == current.size()

        def (resultsTotalTime, currentTotalTime) = [results.totalTime, current.totalTime]
        def unit = resultsTotalTime.first().units
        assert currentTotalTime.first().units == unit

        def zippedValues = [resultsTotalTime.asDoubleList(), currentTotalTime.asDoubleList()].transpose()
        zippedValues.collect { double resultsTime, double currentTime ->
            Amount.valueOf(100 * (resultsTime / currentTime) as BigDecimal, unit)
        }
    }

    boolean significantlyFasterThan(MeasuredOperationList other, double minConfidence = MINIMUM_CONFIDENCE) {
        def myTime = results.totalTime
        def otherTime = other.totalTime
        myTime && myTime.median < otherTime.median && differenceIsSignificant(myTime, otherTime, minConfidence)
    }

    boolean significantlySlowerThan(MeasuredOperationList other, double minConfidence = MINIMUM_CONFIDENCE) {
        def myTime = results.totalTime
        def otherTime = other.totalTime
        myTime && myTime.median > otherTime.median && differenceIsSignificant(myTime, otherTime, minConfidence)
    }

    private static boolean differenceIsSignificant(DataSeries<Duration> myTime, DataSeries<Duration> otherTime, double minConfidence) {
        DataSeries.confidenceInDifference(myTime, otherTime) > minConfidence
    }

}
