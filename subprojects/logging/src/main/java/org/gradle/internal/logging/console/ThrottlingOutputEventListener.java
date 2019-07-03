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

package org.gradle.internal.logging.console;

import org.gradle.internal.logging.events.EndOutputEvent;
import org.gradle.internal.logging.events.FlushOutputEvent;
import org.gradle.internal.logging.events.OutputEvent;
import org.gradle.internal.logging.events.OutputEventListener;
import org.gradle.internal.logging.events.UpdateNowEvent;
import org.gradle.internal.time.Clock;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Queue output events to be forwarded and schedule flush when time passed or if end of build is signalled.
 */
public class ThrottlingOutputEventListener implements OutputEventListener {
    // This is a fuzzy heuristic based on the default memory settings of the Gradle client (64MB)
    // Usually, the OutputEvents queued are very small (<250 bytes), but they can be much larger (>4000 bytes)
    // Assuming an average of ~1000 bytes, 64MB/1000 ~= 65536 output events before we need to flush
    // To hit this limit, that means we must have queued more than 65k output events in a console throttle interval (default 100ms)
    // We could be fancier here and assign different costs/weights to particular output events and adjust this based on
    // the available memory, but this is intended to just discourage ugly OOM errors.
    static final int CRITICAL_FLUSH_SIZE = 65536;
    private final OutputEventListener listener;

    private final ScheduledExecutorService executor;
    private final Clock clock;
    private final int throttleMs;
    private final Object lock = new Object();

    private final List<OutputEvent> queue = new ArrayList<OutputEvent>();

    public ThrottlingOutputEventListener(OutputEventListener listener, Clock clock) {
        this(listener, Integer.getInteger("org.gradle.internal.console.throttle", 100), Executors.newSingleThreadScheduledExecutor(), clock);
    }

    ThrottlingOutputEventListener(OutputEventListener listener, int throttleMs, ScheduledExecutorService executor, Clock clock) {
        this.throttleMs = throttleMs;
        this.listener = listener;
        this.executor = executor;
        this.clock = clock;
        scheduleUpdateNow();
    }

    private void scheduleUpdateNow() {
        executor.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                onOutput(new UpdateNowEvent(clock.getCurrentTime()));
            }
        }, throttleMs, throttleMs, TimeUnit.MILLISECONDS);
    }

    @Override
    public void onOutput(OutputEvent newEvent) {
        synchronized (lock) {
            queue.add(newEvent);

            if (newEvent instanceof UpdateNowEvent) {
                // Flush any buffered events and update the clock
                renderNow();
                return;
            }

            if (newEvent instanceof FlushOutputEvent) {
                renderNow();
                return;
            }

            if (newEvent instanceof EndOutputEvent) {
                // Flush and clean up
                renderNow();
                executor.shutdown();
            }

            if (queue.size() > CRITICAL_FLUSH_SIZE) {
                renderNow();
                return;
            }

            // Else, wait for the next update event
        }
    }

    private void renderNow() {
        for (OutputEvent event : queue) {
            listener.onOutput(event);
        }
        queue.clear();
    }
}
