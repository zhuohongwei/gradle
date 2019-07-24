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

public class DurationMeasurementImpl implements DurationMeasurement {
    private DateTime start;
    private long startNanos;
    private final MeasuredOperation measuredOperation;

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

    @Override
    public void start() {
        this.start = DateTime.now();
        this.startNanos = System.nanoTime();
    }

    @Override
    public void stop() {
        long endNanos = System.nanoTime();
        DateTime end = DateTime.now();

        measuredOperation.setStart(start);
        measuredOperation.setEnd(end);
        measuredOperation.setTotalTime(Duration.millis((endNanos - startNanos) / 1000000L));
    }
}
