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
package org.gradle.internal.buildevents;

import org.gradle.BuildResult;
import org.gradle.api.Action;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.configuration.LoggingConfiguration;
import org.gradle.api.logging.configuration.ShowStacktrace;
import org.gradle.execution.MultipleBuildFailures;
import org.gradle.initialization.BuildClientMetaData;
import org.gradle.initialization.StartParameterBuildOptions;
import org.gradle.internal.exceptions.FailureResolutionAware;
import org.gradle.internal.exceptions.LocationAwareException;
import org.gradle.internal.exceptions.ReportGenerated;
import org.gradle.internal.logging.LoggingConfigurationBuildOptions;
import org.gradle.internal.logging.text.BufferingStyledTextOutput;
import org.gradle.internal.logging.text.LinePrefixingStyledTextOutput;
import org.gradle.internal.logging.text.StyledTextOutput;
import org.gradle.internal.logging.text.StyledTextOutputFactory;
import org.gradle.util.GUtil;
import org.gradle.util.TreeVisitor;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.gradle.internal.logging.text.StyledTextOutput.Style.Failure;
import static org.gradle.internal.logging.text.StyledTextOutput.Style.FailureHeader;
import static org.gradle.internal.logging.text.StyledTextOutput.Style.Info;
import static org.gradle.internal.logging.text.StyledTextOutput.Style.Normal;
import static org.gradle.internal.logging.text.StyledTextOutput.Style.UserInput;

/**
 * Reports the build exception, if any.
 */
public class BuildExceptionReporter implements Action<Throwable> {
    private enum ExceptionStyle {
        NONE, FULL
    }

    private final StyledTextOutputFactory textOutputFactory;
    private final LoggingConfiguration loggingConfiguration;
    private final BuildClientMetaData clientMetaData;

    public BuildExceptionReporter(StyledTextOutputFactory textOutputFactory, LoggingConfiguration loggingConfiguration, BuildClientMetaData clientMetaData) {
        this.textOutputFactory = textOutputFactory;
        this.loggingConfiguration = loggingConfiguration;
        this.clientMetaData = clientMetaData;
    }

    public void buildFinished(BuildResult result) {
        Throwable failure = result.getFailure();
        if (failure == null) {
            return;
        }

        execute(failure);
    }

    @Override
    public void execute(Throwable failure) {
        StyledTextOutput output = textOutputFactory.create(BuildExceptionReporter.class, LogLevel.ERROR);
        FailureDetails details = extractFailureDetails(failure);
        renderFailureHeader(output, failure);
        renderFailureDetails(output, details);
        renderSuggestions(output);
    }

    private FailureDetails extractFailureDetails(Throwable failure) {
        FailureDetails details = new FailureDetails();

        if (failure instanceof MultipleBuildFailures) {
            List<? extends Throwable> causes = ((MultipleBuildFailures) failure).getCauses();
            for (Throwable cause : causes) {
                formatGenericFailure(cause, details);
            }
        } else {
            formatGenericFailure(failure, details);
        }

        return details;
    }

    private void renderFailureHeader(StyledTextOutput output, Throwable failure) {
        output.println();
        if (failure instanceof MultipleBuildFailures) {
            output.withStyle(FailureHeader).format("FAILURE: %s", failure.getMessage());
        } else {
            output.withStyle(FailureHeader).text("FAILURE: Build completed with an exception.");
        }
        output.println();
        output.println();
    }

    private void renderFailureDetails(StyledTextOutput output, FailureDetails details) {
        if (details.summary.getHasContent()) {
            details.summary.writeTo(output);
        }

        if (details.exceptions.getHasContent()) {
            details.exceptions.writeTo(output);
            output.println();
        }
    }

    private void renderSuggestions(StyledTextOutput output) {
        output.text("Try again:");
        output.println();

        if (loggingConfiguration.getShowStacktrace() == ShowStacktrace.INTERNAL_EXCEPTIONS) {
            output.text("   with ");
            output.withStyle(UserInput).format("--%s", LoggingConfigurationBuildOptions.StacktraceOption.STACKTRACE_LONG_OPTION);
            output.text(" to get the stack trace.");
            output.println();
        }

        if (loggingConfiguration.getLogLevel() != LogLevel.DEBUG) {
            output.text("   with ");
            if (loggingConfiguration.getLogLevel() != LogLevel.INFO) {
                output.withStyle(UserInput).format("--%s", LoggingConfigurationBuildOptions.LogLevelOption.INFO_LONG_OPTION);
                output.text(" or ");
            }
            output.withStyle(UserInput).format("--%s", LoggingConfigurationBuildOptions.LogLevelOption.DEBUG_LONG_OPTION);
            output.text(" to get more log output.");
            output.println();
        }

        output.text("   with ");
        output.withStyle(UserInput).format("--%s", StartParameterBuildOptions.BuildScanOption.LONG_OPTION);
        output.text(" to get deeper insights.");
        output.println();

        output.println();
        output.withStyle(UserInput).text("https://help.gradle.org");
        output.println();
    }

    private void formatGenericFailure(Throwable failure, final FailureDetails details) {
        List<File> reports = new ArrayList<File>();

        if (failure instanceof LocationAwareException) {
            final LocationAwareException scriptException = (LocationAwareException) failure;
            scriptException.visitReportableCauses(new TreeVisitor<Throwable>() {
                int depth;

                @Override
                public void node(final Throwable node) {
                    if (node instanceof ReportGenerated) {
                        reports.add(((ReportGenerated) node).getReportFile());
                    }
                    if (node == scriptException) {
                        details.summary.withStyle(Failure).text(getMessage(scriptException.getCause()));
                    } else {
                        final LinePrefixingStyledTextOutput output = getLinePrefixingStyledTextOutput();
                        output.text(getMessage(node));
                    }
                }

                @Override
                public void startChildren() {
                    depth++;
                }

                @Override
                public void endChildren() {
                    depth--;
                }

                private LinePrefixingStyledTextOutput getLinePrefixingStyledTextOutput() {
                    details.summary.format("%n");
                    StringBuilder prefix = new StringBuilder();
                    for (int i = 1; i < depth; i++) {
                        prefix.append("   ");
                    }
                    details.summary.text(prefix);
                    prefix.append("   ");
                    details.summary.style(Info).text("   ").style(Normal);

                    return new LinePrefixingStyledTextOutput(details.summary, prefix, false);
                }
            });
            String location = scriptException.getLocation();
            if (location != null) {
                details.summary.println();
                details.summary.text("   " + location);
            }
        } else {
            details.summary.text(getMessage(failure));
        }
        details.summary.println();

        for (File report : reports) {
            details.summary.style(Info).text("   See report at: ").style(Normal);
            details.summary.file(report);
            details.summary.println();
        }

        if (failure instanceof FailureResolutionAware) {
            ((FailureResolutionAware) failure).appendResolution(new LinePrefixingStyledTextOutput(details.summary, "   ", true), clientMetaData);
            details.summary.println();
            details.summary.println();
        }

        if (loggingConfiguration.getShowStacktrace() != ShowStacktrace.INTERNAL_EXCEPTIONS) {
            details.exceptions.exception(failure);
        }
    }

    private String getMessage(Throwable throwable) {
        String message = throwable.getMessage();
        if (GUtil.isTrue(message)) {
            return message;
        }
        return String.format("%s (no error message)", throwable.getClass().getName());
    }

    private static class FailureDetails {
        final BufferingStyledTextOutput summary = new BufferingStyledTextOutput();
        final BufferingStyledTextOutput exceptions = new BufferingStyledTextOutput();
    }
}
