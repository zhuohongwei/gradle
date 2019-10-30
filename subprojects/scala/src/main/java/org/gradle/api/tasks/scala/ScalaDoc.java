/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.tasks.scala;

import org.gradle.api.Action;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.internal.project.IsolatedAntBuilder;
import org.gradle.api.internal.tasks.scala.AntScalaDoc;
import org.gradle.api.internal.tasks.scala.ScalaDocWorkAction;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SourceTask;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.JavaForkOptions;
import org.gradle.util.GUtil;
import org.gradle.workers.WorkerExecutor;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.File;

/**
 * Generates HTML API documentation for Scala source files.
 *
 * <em>NOTE: This no longer works for Scala 2.13 and newer.</em>
 */
@CacheableTask
public class ScalaDoc extends SourceTask {

    private File destinationDir;

    private FileCollection classpath;
    private FileCollection scalaClasspath;
    private ScalaDocOptions scalaDocOptions = new ScalaDocOptions();
    private String title;

    @Inject
    protected IsolatedAntBuilder getAntBuilder() {
        throw new UnsupportedOperationException();
    }

    @Inject
    protected WorkerExecutor getWorkerExecutor() {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns the directory to generate the API documentation into.
     */
    @OutputDirectory
    public File getDestinationDir() {
        return destinationDir;
    }

    public void setDestinationDir(File destinationDir) {
        this.destinationDir = destinationDir;
    }

    /**
     * {@inheritDoc}
     */
    @PathSensitive(PathSensitivity.RELATIVE)
    @Override
    public FileTree getSource() {
        return super.getSource();
    }

    /**
     * <p>Returns the classpath to use to locate classes referenced by the documented source.</p>
     *
     * @return The classpath.
     */
    @Classpath
    public FileCollection getClasspath() {
        return classpath;
    }

    public void setClasspath(FileCollection classpath) {
        this.classpath = classpath;
    }

    /**
     * Returns the classpath to use to load the ScalaDoc tool.
     */
    @Classpath
    public FileCollection getScalaClasspath() {
        return scalaClasspath;
    }

    public void setScalaClasspath(FileCollection scalaClasspath) {
        this.scalaClasspath = scalaClasspath;
    }

    /**
     * Returns the ScalaDoc generation options.
     */
    @Nested
    public ScalaDocOptions getScalaDocOptions() {
        return scalaDocOptions;
    }

    public void setScalaDocOptions(ScalaDocOptions scalaDocOptions) {
        this.scalaDocOptions = scalaDocOptions;
    }

    /**
     * Returns the documentation title.
     */
    @Nullable @Optional @Input
    public String getTitle() {
        return title;
    }

    public void setTitle(@Nullable String title) {
        this.title = title;
    }

    @TaskAction
    protected void generate() {
        ScalaDocOptions options = getScalaDocOptions();
        if (!GUtil.isTrue(options.getDocTitle())) {
            options.setDocTitle(getTitle());
        }

        try {
            AntScalaDoc antScalaDoc = new AntScalaDoc(getAntBuilder());
            antScalaDoc.execute(getSource(), getDestinationDir(), getClasspath(), getScalaClasspath(), options);
        } catch (Exception e) {
            System.out.println(getScalaClasspath().getAsPath());
            getWorkerExecutor().classLoaderIsolation(spec -> {
                spec.getClasspath().from(getScalaClasspath());
//                spec.forkOptions(new Action<JavaForkOptions>() {
//                    @Override
//                    public void execute(JavaForkOptions forkOptions) {
//                        forkOptions.bootstrapClasspath(getScalaClasspath());
//                    forkOptions.bootstrapClasspath(getScalaClasspath());
//                    }
//                });
            }).submit(ScalaDocWorkAction.class, parameters -> {
                parameters.getOptions().set(options.optionMap());
                parameters.getOptions().put("destDir", getDestinationDir());
                parameters.getOptions().put("classpath", getClasspath().getAsPath());
                parameters.getSourceFiles().from(getSource());
            });
        }
    }
}
