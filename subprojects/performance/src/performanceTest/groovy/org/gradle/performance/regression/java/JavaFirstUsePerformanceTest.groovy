/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.performance.regression.java


import org.gradle.performance.AbstractCrossVersionGradleProfilerPerformanceTest
import org.gradle.performance.categories.PerformanceExperiment
import org.gradle.profiler.BuildMutator
import org.gradle.util.GFileUtils
import org.junit.experimental.categories.Category
import spock.lang.Unroll

import static org.gradle.performance.generator.JavaTestProject.LARGE_JAVA_MULTI_PROJECT
import static org.gradle.performance.generator.JavaTestProject.LARGE_JAVA_MULTI_PROJECT_KOTLIN_DSL
import static org.gradle.performance.generator.JavaTestProject.LARGE_MONOLITHIC_JAVA_PROJECT

@Category(PerformanceExperiment)
class JavaFirstUsePerformanceTest extends AbstractCrossVersionGradleProfilerPerformanceTest {

    @Unroll
    def "first use of #testProject"() {
        given:
        runner.testProject = testProject
        runner.gradleOpts = ["-Xms${testProject.daemonMemory}", "-Xmx${testProject.daemonMemory}"]
        runner.tasksToRun = ['tasks']
        runner.runs = runs
        runner.useDaemon = false
        runner.targetVersions = ["5.7-20190722220035+0000"]
        runner.addBuildMutator{ settings -> new BuildMutator() {
            @Override
            void afterBuild(Throwable error) {
                def gradleDir = new File(settings.projectDir, '.gradle')
                assert gradleDir.exists()
                GFileUtils.deleteDirectory(gradleDir)
                def buildSrcGradleDir = new File(settings.projectDir, 'buildSrc/.gradle')
                assert buildSrcGradleDir.exists()
                GFileUtils.deleteDirectory(buildSrcGradleDir)
                GFileUtils.deleteDirectory(settings.gradleUserHome)
            }
        }}

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        testProject                         | runs
        LARGE_MONOLITHIC_JAVA_PROJECT       | 10
        LARGE_JAVA_MULTI_PROJECT            | 10
        LARGE_JAVA_MULTI_PROJECT_KOTLIN_DSL | 5
    }

    @Unroll
    def "clean checkout of #testProject"() {
        given:
        runner.testProject = testProject
        runner.gradleOpts = ["-Xms${testProject.daemonMemory}", "-Xmx${testProject.daemonMemory}"]
        runner.tasksToRun = ['tasks']
        runner.useDaemon = false
        runner.targetVersions = ["5.7-20190722220035+0000"]
        runner.addBuildMutator{settings -> new BuildMutator() {
            @Override
            void afterBuild(Throwable error) {
                def gradleDir = new File(settings.projectDir, '.gradle')
                assert gradleDir.exists()
                GFileUtils.deleteDirectory(gradleDir)
                def buildSrcGradleDir = new File(settings.projectDir, 'buildSrc/.gradle')
                assert buildSrcGradleDir.exists()
                GFileUtils.deleteDirectory(buildSrcGradleDir)
            }
        }}

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        testProject                              | _
        LARGE_MONOLITHIC_JAVA_PROJECT            | _
        LARGE_JAVA_MULTI_PROJECT                 | _
        LARGE_JAVA_MULTI_PROJECT_KOTLIN_DSL      | _
    }

    @Unroll
    def "cold daemon on #testProject"() {
        given:
        runner.testProject = testProject
        runner.gradleOpts = ["-Xms${testProject.daemonMemory}", "-Xmx${testProject.daemonMemory}"]
        runner.tasksToRun = ['tasks']
        runner.useDaemon = false
        runner.targetVersions = ["5.7-20190722220035+0000"]

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        testProject                              | _
        LARGE_MONOLITHIC_JAVA_PROJECT            | _
        LARGE_JAVA_MULTI_PROJECT                 | _
        LARGE_JAVA_MULTI_PROJECT_KOTLIN_DSL      | _
    }
}
