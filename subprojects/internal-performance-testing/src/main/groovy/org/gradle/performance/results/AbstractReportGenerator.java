/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.performance.results;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.gradle.util.GFileUtils;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;
import static org.gradle.performance.results.ScenarioBuildResultData.ExecutionData;

public abstract class AbstractReportGenerator<R extends ResultsStore> {
    protected void generateReport(String... args) {
        File projectDir = new File(args[0]);
        try (ResultsStore store = getResultsStore()) {
            for (String testName : store.getTestNames()) {
                System.out.println("Start fetching " + testName + " ...");
                PerformanceTestHistory history = store.getTestResults(testName, 10000, 365, null);
                List<ExecutionData> executions = history.getExecutions().stream().map(this::extractExecutionData).collect(toList());
                System.out.println("Fetched " + executions.size() + " executions for " + testName + "");

                if (executions.isEmpty()) {
                    continue;
                }
                String content;
                try {
                    content = executions.stream().map(ExecutionData::getLine).collect(Collectors.joining("\n"));
                } catch (Exception e) {
                    content = ExceptionUtils.getFullStackTrace(e);
                }

                FileUtils.write(new File(projectDir, testName + ".csv"), "difference, confidence\n" + content, Charset.defaultCharset(), true);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected void generate(PerformanceFlakinessAnalyzer flakinessAnalyzer, File outputDirectory, File resultJson, String projectName) {
        try (ResultsStore store = getResultsStore()) {
            renderIndexPage(flakinessAnalyzer, store, resultJson, outputDirectory);

            for (String testName : store.getTestNames()) {
                PerformanceTestHistory testResults = store.getTestResults(testName, 500, 90, ResultsStoreHelper.determineChannel());
                renderScenarioPage(projectName, outputDirectory, testResults);
            }

            copyResource("jquery.min-1.11.0.js", outputDirectory);
            copyResource("flot-0.8.1-min.js", outputDirectory);
            copyResource("flot.selection.min.js", outputDirectory);
            copyResource("style.css", outputDirectory);
            copyResource("report.js", outputDirectory);
            copyResource("performanceGraph.js", outputDirectory);
            copyResource("performanceReport.js", outputDirectory);
        } catch (Exception e) {
            throw new RuntimeException(String.format("Could not generate performance test report to '%s'.", outputDirectory), e);
        }
    }

    protected List<ExecutionData> removeEmptyExecution(List<? extends PerformanceTestExecution> executions) {
        return executions.stream().map(this::extractExecutionData).filter(Objects::nonNull).collect(toList());
    }

    private ExecutionData extractExecutionData(PerformanceTestExecution performanceTestExecution) {
        List<MeasuredOperationList> nonEmptyExecutions = performanceTestExecution
            .getScenarios()
            .stream()
            .filter(testExecution -> !testExecution.getTotalTime().isEmpty())
            .collect(toList());
        if (nonEmptyExecutions.size() > 1) {
            int size = nonEmptyExecutions.size();
            return new ExecutionData(performanceTestExecution.getStartTime(), getCommit(performanceTestExecution), nonEmptyExecutions.get(size - 2), nonEmptyExecutions.get(size - 1));
        } else {
            return null;
        }
    }

    private String getCommit(PerformanceTestExecution execution) {
        if (execution.getVcsCommits().isEmpty()) {
            return "";
        } else {
            return execution.getVcsCommits().get(0);
        }
    }

    protected void renderIndexPage(PerformanceFlakinessAnalyzer flakinessAnalyzer, ResultsStore store, File resultJson, File outputDirectory) throws IOException {
        new FileRenderer().render(store, new IndexPageGenerator(flakinessAnalyzer, store, resultJson), new File(outputDirectory, "index.html"));
    }

    protected void renderScenarioPage(String projectName, File outputDirectory, PerformanceTestHistory testResults) throws IOException {
        FileRenderer fileRenderer = new FileRenderer();
        TestPageGenerator testHtmlRenderer = new TestPageGenerator(projectName);
        TestDataGenerator testDataRenderer = new TestDataGenerator();
        fileRenderer.render(testResults, testHtmlRenderer, new File(outputDirectory, "tests/" + testResults.getId() + ".html"));
        fileRenderer.render(testResults, testDataRenderer, new File(outputDirectory, "tests/" + testResults.getId() + ".json"));
    }

    protected ResultsStore getResultsStore() throws Exception {
        Type superClass = getClass().getGenericSuperclass();
        Class<? extends ResultsStore> resultsStoreClass = (Class<R>) ((ParameterizedType) superClass).getActualTypeArguments()[0];
        return resultsStoreClass.getConstructor().newInstance();
    }

    protected void copyResource(String resourceName, File outputDirectory) {
        URL resource = getClass().getClassLoader().getResource("org/gradle/reporting/" + resourceName);
        String dir = StringUtils.substringAfterLast(resourceName, ".");
        GFileUtils.copyURLToFile(resource, new File(outputDirectory, dir + "/" + resourceName));
    }
}
