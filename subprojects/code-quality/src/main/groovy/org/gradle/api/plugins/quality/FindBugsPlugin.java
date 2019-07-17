/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.plugins.quality;

import com.google.common.util.concurrent.Callables;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.ConventionMapping;
import org.gradle.api.plugins.quality.internal.AbstractCodeQualityPlugin;
import org.gradle.api.resources.TextResource;
import org.gradle.api.tasks.SourceSet;
import org.gradle.util.SingleMessageLogger;

import java.io.File;
import java.util.Collection;
import java.util.concurrent.Callable;

/**
 * A plugin for the <a href="http://findbugs.sourceforge.net">FindBugs</a> byte code analyzer.
 *
 * <p>
 * Declares a <tt>findbugs</tt> configuration which needs to be configured with the FindBugs library to be used.
 * Additional plugins can be added to the <tt>findbugsPlugins</tt> configuration.
 *
 * <p>
 * For projects that have the Java (base) plugin applied, a {@link FindBugs} task is
 * created for each source set.
 *
 * @see FindBugs
 * @see FindBugsExtension
 * @deprecated FindBugs is unmaintained and does not support bytecode compiled for Java 9 and above.
 */
@Deprecated
public class FindBugsPlugin extends AbstractCodeQualityPlugin<FindBugs> {

    public static final String DEFAULT_FINDBUGS_VERSION = "3.0.1";
    private FindBugsExtension extension;

    @Override
    protected String getToolName() {
        return "FindBugs";
    }

    @Override
    protected Class<FindBugs> getTaskType() {
        return FindBugs.class;
    }

    @Override
    protected void beforeApply() {
        SingleMessageLogger.nagUserOfPluginReplacedWithExternalOne("findbugs", "com.github.spotbugs");
        configureFindBugsConfigurations();
    }

    private void configureFindBugsConfigurations() {
        Configuration configuration = project.getConfigurations().create("findbugsPlugins");
        configuration.setVisible(false);
        configuration.setTransitive(true);
        configuration.setDescription("The FindBugs plugins to be used for this project.");
    }

    @Override
    protected CodeQualityExtension createExtension() {
        extension = project.getExtensions().create("findbugs", FindBugsExtension.class, project);
        extension.setToolVersion(DEFAULT_FINDBUGS_VERSION);
        return extension;
    }

    @Override
    protected void configureConfiguration(Configuration configuration) {
        configureDefaultDependencies(configuration);
    }

    @Override
    protected void configureTaskDefaults(FindBugs task, String baseName) {
        task.setPluginClasspath(project.getConfigurations().getAt("findbugsPlugins"));
        Configuration configuration = project.getConfigurations().getAt(getConfigurationName());
        configureTaskConventionMapping(configuration, task);
        configureReportsConventionMapping(task, baseName);
    }

    private void configureDefaultDependencies(Configuration configuration) {
        configuration.defaultDependencies(dependencies -> dependencies.add(project.getDependencies().create("com.google.code.findbugs:findbugs:" + extension.getToolVersion())));
    }

    private void configureTaskConventionMapping(Configuration configuration, FindBugs task) {
        ConventionMapping taskMapping = task.getConventionMapping();
        taskMapping.map("findbugsClasspath", Callables.returning(configuration));
        taskMapping.map("ignoreFailures", (Callable<Boolean>) () -> extension.isIgnoreFailures());
        taskMapping.map("effort", (Callable<String>) () -> extension.getEffort());
        taskMapping.map("reportLevel", (Callable<String>) () -> extension.getReportLevel());
        taskMapping.map("visitors", (Callable<Collection<String>>) () -> extension.getVisitors());
        taskMapping.map("omitVisitors", (Callable<Collection<String>>) () -> extension.getOmitVisitors());

        taskMapping.map("excludeFilterConfig", (Callable<TextResource>) () -> extension.getExcludeFilterConfig());
        taskMapping.map("includeFilterConfig", (Callable<TextResource>) () -> extension.getIncludeFilterConfig());
        taskMapping.map("excludeBugsFilterConfig", (Callable<TextResource>) () -> extension.getExcludeBugsFilterConfig());

        taskMapping.map("extraArgs", (Callable<Collection<String>>) () -> extension.getExtraArgs());
        taskMapping.map("jvmArgs", (Callable<Collection<String>>) () -> extension.getJvmArgs());
        taskMapping.map("showProgress", (Callable<Boolean>) () -> extension.isShowProgress());
    }

    private void configureReportsConventionMapping(FindBugs task, final String baseName) {
        task.getReports().all(report -> {
            ConventionMapping reportMapping = conventionMappingOf(report);
            reportMapping.map("enabled", (Callable<Boolean>) () -> report.getName().equals("xml"));
            reportMapping.map("destination", (Callable<File>) () -> new File(extension.getReportsDir(), baseName + "." + report.getName()));
        });
    }

    @Override
    protected void configureForSourceSet(final SourceSet sourceSet, FindBugs task) {
        task.setDescription("Run FindBugs analysis for " + sourceSet.getName() + " classes");
        task.setSource(sourceSet.getAllJava());
        ConventionMapping taskMapping = task.getConventionMapping();
        taskMapping.map("classes", (Callable<FileCollection>) () -> sourceSet.getOutput().getClassesDirs());
        taskMapping.map("classpath", (Callable<FileCollection>) () -> sourceSet.getCompileClasspath());
    }
}
