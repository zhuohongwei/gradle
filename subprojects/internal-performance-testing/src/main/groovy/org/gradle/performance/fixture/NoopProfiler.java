/*
 * Copyright 2018 the original author or authors.
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

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class NoopProfiler extends Profiler {
    private final AtomicInteger counter = new AtomicInteger(0);
    private final PidInstrumentation pid = new PidInstrumentation();


    @Override
    public List<String> getAdditionalJvmOpts(BuildExperimentSpec spec) {
        return Collections.emptyList();
    }

    @Override
    public String getJvmOptsForUseInBuild(String recordingsDirectoryRelativePath) {
        return "";
    }

    @Override
    public List<String> getAdditionalGradleArgs(BuildExperimentSpec spec) {
        return pid.getGradleArgs();
    }

    @Override
    public void start(BuildExperimentSpec spec) {
    }

    @Override
    public void stop(BuildExperimentSpec spec) {
    }

    public void dump(int i) {
        try {
            new ProcessBuilder("jmap", "-dump:format=b,file=/home/tcagent1/agent/work/a16b87e0a70f8c6e/" + pid.getPid() + "-" + i + ".hprof", pid.getPid())
                .inheritIO()
                .start().waitFor();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
