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

package org.gradle.testing

import groovy.transform.CompileStatic
import org.gradle.api.Action
import org.gradle.api.tasks.TaskAction
import org.gradle.initialization.BuildCancellationToken
import org.gradle.process.JavaExecSpec

import javax.inject.Inject

@CompileStatic
abstract class FetchFlakinessDataPerformanceTest extends DistributedPerformanceTest {
    @Inject
    FetchFlakinessDataPerformanceTest(BuildCancellationToken cancellationToken) {
        super(cancellationToken)
    }

    @TaskAction
    @Override
    void executeTests() {
        project.javaexec(new Action<JavaExecSpec>() {
            void execute(JavaExecSpec spec) {
                spec.setMain('org.gradle.performance.results.DefaultReportGenerator')
                spec.setArgs([getProject().getRootDir().absolutePath])
                spec.systemProperties(databaseParameters)
                spec.setClasspath(FetchFlakinessDataPerformanceTest.this.getClasspath())
            }
        })
    }
}
