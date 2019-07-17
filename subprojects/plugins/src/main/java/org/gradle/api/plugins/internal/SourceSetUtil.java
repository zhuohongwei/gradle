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

package org.gradle.api.plugins.internal;

import org.gradle.api.Project;
import org.gradle.api.Transformer;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.internal.ConventionMapping;
import org.gradle.api.internal.plugins.DslObject;
import org.gradle.api.internal.tasks.DefaultSourceSetOutput;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.compile.AbstractCompile;
import org.gradle.api.tasks.compile.CompileOptions;
import org.gradle.internal.Cast;

import java.io.File;
import java.util.concurrent.Callable;

public class SourceSetUtil {
    private SourceSetUtil() {
    }

    public static void configureForSourceSet(final SourceSet sourceSet, final SourceDirectorySet sourceDirectorySet, AbstractCompile compile, CompileOptions options, final Project target) {
        configureForSourceSet(sourceSet, sourceDirectorySet, compile, target);
        configureAnnotationProcessorPath(sourceSet, sourceDirectorySet, options, target);
    }

    private static void configureForSourceSet(final SourceSet sourceSet, final SourceDirectorySet sourceDirectorySet, AbstractCompile compile, final Project target) {
        compile.setDescription("Compiles the " + sourceDirectorySet.getDisplayName() + ".");
        compile.setSource(sourceSet.getJava());

        ConfigurableFileCollection classpath = compile.getProject().getObjects().fileCollection();
        classpath.from((Callable<Object>) () -> sourceSet.getCompileClasspath().plus(target.files(sourceSet.getJava().getOutputDir())));

        compile.getConventionMapping().map("classpath", () -> classpath);
        compile.setDestinationDir(target.provider(() -> sourceDirectorySet.getOutputDir()));
    }

    public static void configureAnnotationProcessorPath(final SourceSet sourceSet, SourceDirectorySet sourceDirectorySet, CompileOptions options, final Project target) {
        final ConventionMapping conventionMapping = new DslObject(options).getConventionMapping();
        conventionMapping.map("annotationProcessorPath", () -> sourceSet.getAnnotationProcessorPath());
        final String annotationProcessorGeneratedSourcesChildPath = "generated/sources/annotationProcessor/" + sourceDirectorySet.getName() + "/" + sourceSet.getName();
        conventionMapping.map("annotationProcessorGeneratedSourcesDirectory", () -> new File(target.getBuildDir(), annotationProcessorGeneratedSourcesChildPath));
    }

    public static void configureOutputDirectoryForSourceSet(final SourceSet sourceSet, final SourceDirectorySet sourceDirectorySet, final Project target, Provider<? extends AbstractCompile> compileTask, Provider<CompileOptions> options) {
        final String sourceSetChildPath = "classes/" + sourceDirectorySet.getName() + "/" + sourceSet.getName();
        sourceDirectorySet.setOutputDir(target.provider(() -> new File(target.getBuildDir(), sourceSetChildPath)));

        DefaultSourceSetOutput sourceSetOutput = Cast.cast(DefaultSourceSetOutput.class, sourceSet.getOutput());
        sourceSetOutput.addClassesDir(() -> sourceDirectorySet.getOutputDir());
        sourceSetOutput.getGeneratedSourcesDirs().from(options.map((Transformer<Object, CompileOptions>) compileOptions -> compileOptions.getAnnotationProcessorGeneratedSourcesDirectory())).builtBy(compileTask);
    }
}
