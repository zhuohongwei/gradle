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

package org.gradle.performance.fixture;

import com.google.common.io.ByteStreams;
import org.gradle.integtests.fixtures.executer.DurationMeasurement;
import org.gradle.performance.measure.Duration;
import org.gradle.performance.measure.MeasuredOperation;
import org.joda.time.DateTime;

import java.io.IOException;
import java.util.AbstractMap;
import java.util.Map;

import static java.util.AbstractMap.Entry;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.stream.Collectors.toMap;
import static org.gradle.performance.fixture.BuildExperimentRunner.splitLines;

public class DurationMeasurementImpl implements DurationMeasurement {
    private final MeasuredOperation measuredOperation;

    private Map<String, Integer> safepontTimes;
    private DateTime start;
    private long startNanos;

    public DurationMeasurementImpl(MeasuredOperation measuredOperation) {
        this.measuredOperation = measuredOperation;
    }

    public void measure(Runnable runnable) {
        try {
            start();
            runnable.run();
        } finally {
            stop();
        }
    }

    /**
     * Reset the OS state, for consistency.
     */
    public static void cleanup() {
        runJvmGc();
    }

    /**
     * Run a garbage collection on the benchmarking JVM.
     */
    private static void runJvmGc() {
        System.gc();
        System.runFinalization();
    }

    /**
     * Execute command with root privileges.
     *
     * @return the output of the process.
     */
    public static String executeProcess(String command) {
        try {
            Process exec = Runtime.getRuntime()
                .exec(new String[]{"/bin/bash", "-c", command});
            int exitValue = exec.waitFor();
            assert exitValue == 0 : String.format("Failed executing '%s': %s", command, new String(ByteStreams.toByteArray(exec.getErrorStream())));

            return new String(ByteStreams.toByteArray(exec.getInputStream()));
        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    public static void printProcess(String explanation, String command) {
        System.out.print(explanation + ": " + executeProcess(command));
    }

    @Override
    public void start() {
        cleanup();

        this.safepontTimes = getAllProcessSafepointTimes();
        this.start = DateTime.now();
        this.startNanos = System.nanoTime();
    }

    /**
     * Get the current safepoint (Stop-the-World pause) time of every running JVM (pid -> millis)
     */
    public static Map<String, Integer> getAllProcessSafepointTimes() {
        return splitLines(executeProcess("pgrep java | xargs -I{} sh -c 'printf \"{} \"; jcmd {} PerfCounter.print | grep safepointTime | cut --delimiter='=' --fields=2'"))
            .map(line -> line.split(" "))
            .map(parts -> new AbstractMap.SimpleImmutableEntry<>(parts[0], Long.parseLong(parts[1])))
            .collect(toMap(Entry::getKey, entry -> (int) NANOSECONDS.toMillis((entry.getValue())))); // int can contain ~3 weeks in millis, should suffice for pause times
    }
    @Override
    public void stop() {
        long endNanos = System.nanoTime();
        DateTime end = DateTime.now();
        int pauseMillis = getCumulativePauses(safepontTimes, getAllProcessSafepointTimes());


        measuredOperation.setStart(start);
        measuredOperation.setEnd(end);
        measuredOperation.setTotalTime(Duration.millis(NANOSECONDS.toMillis(endNanos - startNanos)));
        measuredOperation.setPauseTime(Duration.millis(pauseMillis));
    }

    private int getCumulativePauses(Map<String, Integer> startSafepontTimes, Map<String, Integer> currentSafepointTimes) {
        int resultMillis = 0;
        for (Entry<String, Integer> entry : currentSafepointTimes.entrySet()) {
            resultMillis += entry.getValue() - startSafepontTimes.getOrDefault(entry.getKey(), 0);
        }
        return resultMillis;
    }
}
