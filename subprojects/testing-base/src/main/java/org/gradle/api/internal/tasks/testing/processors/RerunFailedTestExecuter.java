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

package org.gradle.api.internal.tasks.testing.processors;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import org.gradle.api.internal.tasks.testing.DefaultTestClassRunInfo;
import org.gradle.api.internal.tasks.testing.TestClassProcessor;
import org.gradle.api.internal.tasks.testing.TestClassRunInfo;
import org.gradle.api.internal.tasks.testing.TestCompleteEvent;
import org.gradle.api.internal.tasks.testing.TestDescriptorInternal;
import org.gradle.api.internal.tasks.testing.TestResultProcessor;
import org.gradle.api.internal.tasks.testing.TestStartEvent;
import org.gradle.api.tasks.testing.TestOutputEvent;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class RerunFailedTestExecuter implements TestClassProcessor {

    private final TestClassProcessor delegate;
    private final int numberOfRetries;
    private final Multiset<String> executedTestClasses = HashMultiset.create();
    private final ConcurrentMap<Object, Boolean> rootIds = new ConcurrentHashMap<Object, Boolean>();
    private final ConcurrentMap<Object, String> classNameForTestId = new ConcurrentHashMap<Object, String>();
    private final ConcurrentMap<String, Boolean> newlyScheduledTest = new ConcurrentHashMap<String, Boolean>();
    private boolean stopped;

    public RerunFailedTestExecuter(int numberOfRetries, TestClassProcessor delegate) {
        this.delegate = delegate;
        this.numberOfRetries = numberOfRetries;
    }

    @Override
    public void startProcessing(TestResultProcessor resultProcessor) {
        executedTestClasses.clear();
        classNameForTestId.clear();
        rootIds.clear();
        stopped = false;
        delegate.startProcessing(new RerunningTestResultProcessor(resultProcessor));
    }

    @Override
    public void processTestClass(TestClassRunInfo testClass) {
        delegate.processTestClass(testClass);
    }

    @Override
    public void waitForComplete() {
        delegate.waitForComplete();
    }

    @Override
    public void stop() {
        delegate.stop();
    }

    @Override
    public void stopNow() {
        delegate.stopNow();
    }

    private synchronized void maybeRerun(Object testId) {
        String className = classNameForTestId.get(testId);
        if (className == null) {
            throw new AssertionError("Test which never started failed. TestId: " + testId);
        }
        executedTestClasses.add(className);
        if (executedTestClasses.count(className) <= numberOfRetries) {
            newlyScheduledTest.put(className, Boolean.TRUE);
            processTestClass(new DefaultTestClassRunInfo(className));
        }
    }

    private class RerunningTestResultProcessor implements TestResultProcessor {
        private final TestResultProcessor delegate;

        public RerunningTestResultProcessor(TestResultProcessor delegate) {
            this.delegate = delegate;
        }

        @Override
        public void started(TestDescriptorInternal test, TestStartEvent event) {
            if (test.getClassName() != null) {
                rootIds.put(test.getId(), Boolean.TRUE);
                classNameForTestId.put(test.getId(), test.getClassName());
                newlyScheduledTest.remove(test.getClassName(), Boolean.TRUE);
            }
            delegate.started(test, event);
        }

        @Override
        public void completed(Object testId, TestCompleteEvent event) {
            delegate.completed(testId, event);
            rootIds.remove(testId, Boolean.TRUE);
        }

        @Override
        public void output(Object testId, TestOutputEvent event) {
            delegate.output(testId, event);
        }

        @Override
        public void failure(Object testId, Throwable result) {
            delegate.failure(testId, result);
            maybeRerun(testId);
        }
    }
}
