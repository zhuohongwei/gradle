/*
 * Copyright 2019 the original author or authors.
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

class CsvLine {
    String scenario;
    ScenarioBuildResultData.ExecutionData data

    CsvLine(String scenario, ScenarioBuildResultData.ExecutionData data) {
        this.scenario = scenario
        this.data = data
    }

    static String getTitleLine() {
        "scenario, branch, channel, time, commitId, baselineVersion, baselineAverage, baselineMedian, currentVersion, currentAverage, currentMedian, averageDiff, medianDiff, confidence, host, jvm, template, tasks, jvmOpts, daemon\n"
    }

    String toLine() {
        double currentAverage = data.currentVersion.totalTime.average.value.doubleValue()
        double baselineAverage = data.baseVersion.totalTime.average.value.doubleValue()
        [
            scenario.replace(',','_'),
            data.execution.getVcsBranch(),
            data.execution.getChannel(),
            FormatSupport.timestamp(data.time),
            data.shortCommitId,
            data.baseVersion.name,
            data.baseVersion.totalTime.getAverage(),
            data.baseVersion.totalTime.getMedian(),
            data.currentVersion.name,
            data.currentVersion.totalTime.getAverage(),
            data.currentVersion.totalTime.getMedian(),
            String.format("%.2f", (currentAverage - baselineAverage) / baselineAverage),
            data.differencePercentage / 100,
            data.confidencePercentage / 100,
            data.execution.getHost(),
            data.execution.getJvm(),
            data.execution.getTestProject(),
            data.execution.getTasks()?.join(" ") ?: '',
            data.execution.getGradleOpts()?.join(" ") ?: '',
            data.execution.getDaemon() ? "1" : "0"
        ].join(", ") + "\n"
    }
}
