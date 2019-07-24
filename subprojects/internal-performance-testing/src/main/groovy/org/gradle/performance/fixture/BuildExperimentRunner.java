/*
 * Copyright 2014 the original author or authors.
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

import org.gradle.api.Action;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.performance.measure.MeasuredOperation;
import org.gradle.performance.results.MeasuredOperationList;
import org.gradle.util.GFileUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.gradle.performance.fixture.DurationMeasurementImpl.executeProcess;

public class BuildExperimentRunner {

    private final GradleSessionProvider executerProvider;
    private final Profiler profiler;

    public enum Phase {
        WARMUP,
        MEASUREMENT
    }

    public BuildExperimentRunner(GradleSessionProvider executerProvider) {
        this.executerProvider = executerProvider;
        profiler = Profiler.create();
    }

    public Profiler getProfiler() {
        return profiler;
    }

    public void run(BuildExperimentSpec experiment, MeasuredOperationList results) {
        System.out.println();
        System.out.println(String.format("%s ...", experiment.getDisplayName()));
        System.out.println();

        InvocationSpec invocationSpec = experiment.getInvocation();
        File workingDirectory = invocationSpec.getWorkingDirectory();
        workingDirectory.mkdirs();
        copyTemplateTo(experiment, workingDirectory);

        if (experiment.getListener() != null) {
            experiment.getListener().beforeExperiment(experiment, workingDirectory);
        }

        if (invocationSpec instanceof GradleInvocationSpec) {
            GradleInvocationSpec invocation = (GradleInvocationSpec) invocationSpec;
            final List<String> additionalJvmOpts = profiler.getAdditionalJvmOpts(experiment);
            final List<String> additionalArgs = new ArrayList<String>(profiler.getAdditionalGradleArgs(experiment));
            additionalArgs.add("-PbuildExperimentDisplayName=" + experiment.getDisplayName());

            GradleInvocationSpec buildSpec = invocation.withAdditionalJvmOpts(additionalJvmOpts).withAdditionalArgs(additionalArgs);
            GradleSession session = executerProvider.session(buildSpec);
            session.prepare();
            try {
                beforeIterations();
                performMeasurements(session, experiment, results, workingDirectory);
            } finally {
                afterIterations();
                CompositeStoppable.stoppable(profiler).stop();
                session.cleanup();
            }
        }
    }

    private void copyTemplateTo(BuildExperimentSpec experiment, File workingDir) {
        File templateDir = new TestProjectLocator().findProjectDir(experiment.getProjectName());
        GFileUtils.cleanDirectory(workingDir);
        GFileUtils.copyDirectory(templateDir, workingDir);
    }

    protected void performMeasurements(final InvocationExecutorProvider session, BuildExperimentSpec experiment, MeasuredOperationList results, File projectDir) {
        doWarmup(experiment, projectDir, session);
        profiler.start(experiment);
        doMeasure(experiment, results, projectDir, session);
        profiler.stop(experiment);
    }

    private void doMeasure(BuildExperimentSpec experiment, MeasuredOperationList results, File projectDir, InvocationExecutorProvider session) {
        int invocationCount = invocationsForExperiment(experiment);
        for (int i = 0; i < invocationCount; i++) {
            System.out.println();
            System.out.println(String.format("Test run #%s", i + 1));

            displayInfo();

            BuildExperimentInvocationInfo info = new DefaultBuildExperimentInvocationInfo(experiment, projectDir, Phase.MEASUREMENT, i + 1, invocationCount);
            runOnce(session, results, info);
        }
    }

    @SuppressWarnings("unchecked")
    protected InvocationCustomizer createInvocationCustomizer(final BuildExperimentInvocationInfo info) {
        if (info.getBuildExperimentSpec() instanceof GradleBuildExperimentSpec) {
            return new InvocationCustomizer() {
                @Override
                public <T extends InvocationSpec> T customize(BuildExperimentInvocationInfo info, T invocationSpec) {
                    final List<String> iterationInfoArguments = createIterationInfoArguments(info.getPhase(), info.getIterationNumber(), info.getIterationMax());
                    GradleInvocationSpec gradleInvocationSpec = ((GradleInvocationSpec) invocationSpec).withAdditionalArgs(iterationInfoArguments);
                    System.out.println("Run Gradle using JVM opts: " + gradleInvocationSpec.getJvmOpts());
                    if (info.getBuildExperimentSpec().getInvocationCustomizer() != null) {
                        gradleInvocationSpec = info.getBuildExperimentSpec().getInvocationCustomizer().customize(info, gradleInvocationSpec);
                    }
                    return (T) gradleInvocationSpec;
                }
            };
        }
        return null;
    }

    private void doWarmup(BuildExperimentSpec experiment, File projectDir, InvocationExecutorProvider session) {
        int warmUpCount = warmupsForExperiment(experiment);
        for (int i = 0; i < warmUpCount; i++) {
            System.out.println();
            System.out.println(String.format("Warm-up #%s", i + 1));
            BuildExperimentInvocationInfo info = new DefaultBuildExperimentInvocationInfo(experiment, projectDir, Phase.WARMUP, i + 1, warmUpCount);
            runOnce(session, new MeasuredOperationList(), info);
        }
    }

    private static void beforeIterations() {
        if (!OperatingSystem.current().isLinux()) {
            return;
        }
        setOSSchedulerStates(false);
        setNetworkManagerState(false);

        stabilizeSystem();

        assertOSPerformanceSettings();
    }

    /**
     * Temporarily disable all network traffic during benchmarking.
     */
    private static void setNetworkManagerState(boolean enabled) {
        String command = enabled ? "start" : "stop";
        System.out.println(String.format("Network manager will %s now.", command));
        executeProcess(String.format("sudo systemctl %s networking", command));
    }

    private static void assertOSPerformanceSettings() {
        assert executeProcess("cat /etc/default/cpufrequtils").contains("performance"); // CPU should not be in powersave - https://github.com/gradle/dev-infrastructure/pull/269/files
        // assert executeProcess("cat /proc/cmdline").contains("intel_pstate=disable"); // https://github.com/softdevteam/krun#step-2-linux-only-kernel-and-os-setup
        assert executeProcess("cat /sys/devices/system/cpu/isolated").contains("0-3"); // https://vstinner.github.io/journey-to-stable-benchmark-system.html#cpu-isolation
    }

    /**
     * Show the currently running services, CPU speeds, temperatures, free space, etc.
     */
    private static void displayInfo() {
        System.out.println(executeProcess("sensors | grep 'Core '")); // CPU temperature
        System.out.println(executeProcess("lscpu | grep 'CPU MHz:'")); // CPU speed
        System.out.println(executeProcess("free")); // Used memory
        System.out.println(executeProcess("df --human-readable ")); // Disk space
        System.out.println(executeProcess("systemctl | grep 'running'")); // Running services
        System.out.println(executeProcess("ps ax | egrep '[Gg]radle'\n")); // Running Gradle processes
    }

    /**
     * Disable swap completely, auditing, turbo boost, etc.
     */
    private static void stabilizeSystem() {
        System.out.println(executeProcess("sudo swapoff --all --verbose")); // Disable devices and files for paging and swapping.

        System.out.println(executeProcess("sudo sysctl vm.overcommit_memory=2")); // disable overcommit, see https://github.com/softdevteam/krun/blob/da9e46f1207a4ba99df6ae896f8fc24036b648dc/krun/platform.py#L1401
    }

    private static void afterIterations() {
        if (!OperatingSystem.current().isLinux()) {
            return;
        }

        setNetworkManagerState(true);
        setOSSchedulerStates(true);
    }

    /**
     * Temporarily disable cron and atd to make sure nothing unexpected happens during benchmarking.
     * See: https://github.com/softdevteam/krun#step-4-audit-system-services
     */
    private static void setOSSchedulerStates(boolean enabled) {
        String command = enabled ? "start" : "stop";
        System.out.println(String.format("Cron & Atd will %s now.", command));
        executeProcess(String.format("sudo systemctl %s cron", command)); // daemon to execute scheduled commands
        executeProcess(String.format("sudo systemctl %s atd", command)); // run jobs queued for later execution
    }

    private static String getExperimentOverride(String key) {
        String value = System.getProperty("org.gradle.performance.execution." + key);
        if (value != null && !"defaults".equals(value)) {
            return value;
        }
        return null;
    }

    protected Integer invocationsForExperiment(BuildExperimentSpec experiment) {
        String overriddenInvocationCount = getExperimentOverride("runs");
        if (overriddenInvocationCount != null) {
            return Integer.valueOf(overriddenInvocationCount);
        }
        if (experiment.getInvocationCount() != null) {
            return experiment.getInvocationCount();
        }
        return 40;
    }

    protected int warmupsForExperiment(BuildExperimentSpec experiment) {
        String overriddenWarmUpCount = getExperimentOverride("warmups");
        if (overriddenWarmUpCount != null) {
            return Integer.valueOf(overriddenWarmUpCount);
        }
        if (experiment.getWarmUpCount() != null) {
            return experiment.getWarmUpCount();
        }
        if (usesDaemon(experiment)) {
            return 10;
        } else {
            return 1;
        }
    }

    private boolean usesDaemon(BuildExperimentSpec experiment) {
        InvocationSpec invocation = experiment.getInvocation();
        if (invocation instanceof GradleInvocationSpec) {
            if (((GradleInvocationSpec) invocation).getBuildWillRunInDaemon()) {
                return true;
            }
        }
        return false;
    }

    protected void runOnce(
        final InvocationExecutorProvider session,
        final MeasuredOperationList results,
        final BuildExperimentInvocationInfo invocationInfo) {
        BuildExperimentSpec experiment = invocationInfo.getBuildExperimentSpec();
        final Action<MeasuredOperation> runner = session.runner(invocationInfo, wrapInvocationCustomizer(invocationInfo, createInvocationCustomizer(invocationInfo)));

        if (experiment.getListener() != null) {
            experiment.getListener().beforeInvocation(invocationInfo);
        }

        MeasuredOperation operation = new MeasuredOperation();
        runner.execute(operation);

        final AtomicBoolean omitMeasurement = new AtomicBoolean();
        if (experiment.getListener() != null) {
            experiment.getListener().afterInvocation(invocationInfo, operation, new BuildExperimentListener.MeasurementCallback() {
                @Override
                public void omitMeasurement() {
                    omitMeasurement.set(true);
                }
            });
        }

        if (!omitMeasurement.get()) {
            results.add(operation);
        }
    }

    private InvocationCustomizer wrapInvocationCustomizer(BuildExperimentInvocationInfo invocationInfo, final InvocationCustomizer invocationCustomizer) {
        final InvocationCustomizer experimentSpecificCustomizer = invocationInfo.getBuildExperimentSpec().getInvocationCustomizer();
        if (experimentSpecificCustomizer != null) {
            if (invocationCustomizer != null) {
                return new InvocationCustomizer() {
                    @Override
                    public <T extends InvocationSpec> T customize(BuildExperimentInvocationInfo info, T invocationSpec) {
                        return experimentSpecificCustomizer.customize(info, invocationCustomizer.customize(info, invocationSpec));
                    }
                };
            } else {
                return experimentSpecificCustomizer;
            }
        } else {
            return invocationCustomizer;
        }
    }

    protected List<String> createIterationInfoArguments(Phase phase, int iterationNumber, int iterationMax) {
        List<String> args = new ArrayList<String>(3);
        args.add("-PbuildExperimentPhase=" + phase.toString().toLowerCase());
        args.add("-PbuildExperimentIterationNumber=" + iterationNumber);
        args.add("-PbuildExperimentIterationMax=" + iterationMax);
        return args;
    }
}
