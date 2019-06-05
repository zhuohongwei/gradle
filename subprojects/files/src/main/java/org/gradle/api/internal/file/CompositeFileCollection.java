/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.api.internal.file;

import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.internal.file.collections.BuildDependenciesOnlyFileCollectionResolveContext;
import org.gradle.api.internal.file.collections.DefaultFileCollectionResolveContext;
import org.gradle.api.internal.file.collections.DirectoryFileTree;
import org.gradle.api.internal.file.collections.FileCollectionContainer;
import org.gradle.api.internal.file.collections.FileCollectionResolveContext;
import org.gradle.api.internal.file.collections.ResolvableFileCollectionResolveContext;
import org.gradle.api.internal.tasks.AbstractTaskDependency;
import org.gradle.api.internal.tasks.TaskDependencyContainer;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.TaskDependency;

import java.io.File;
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.gradle.api.internal.file.collections.DefaultFileCollectionResolveContext.asStream;

/**
 * A {@link org.gradle.api.file.FileCollection} that contains the union of zero or more file collections. Maintains file ordering.
 *
 * <p>The source file collections are calculated from the result of calling {@link #visitContents(FileCollectionResolveContext)}, and may be lazily created.
 * This also means that the source collections can be created using any representation supported by {@link FileCollectionResolveContext}.
 * </p>
 *
 * <p>The dependencies of this collection are calculated from the result of calling {@link #visitDependencies(TaskDependencyResolveContext)}.</p>
 */
public abstract class CompositeFileCollection extends AbstractFileCollection implements FileCollectionContainer, TaskDependencyContainer {
    @Override
    public Set<File> getFiles() {
        return getFileStream()
            .collect(Collectors.toSet());
    }

    @Override
    public Stream<File> getFileStream() {
        return getSourceStream()
            .flatMap(collection -> asStream(collection.iterator()))
            .distinct();
    }

    @Override
    public boolean contains(File file) {
        return getSourceStream()
            .anyMatch(collection -> collection.contains(file));
    }

    @Override
    public boolean isEmpty() {
        return getSourceStream()
            .allMatch(collection -> collection.isEmpty());
    }

    @Override
    protected void addAsResourceCollection(Object builder, String nodeName) {
        getSourceStream()
            .forEach(fileCollection -> fileCollection.addToAntBuilder(builder, nodeName, AntType.ResourceCollection));
    }

    @Override
    protected Collection<DirectoryFileTree> getAsFileTrees() {
        return getSourceStream()
            .flatMap(source -> ((AbstractFileCollection) source).getAsFileTrees().stream())
            .collect(Collectors.toList());
    }

    @Override
    public FileTree getAsFileTree() {
        return new CompositeFileTree() {
            @Override
            public void visitContents(FileCollectionResolveContext context) {
                ResolvableFileCollectionResolveContext nested = context.newContext();
                CompositeFileCollection.this.visitContents(nested);
                context.add(nested.resolveAsFileTrees());
            }

            @Override
            public void visitDependencies(TaskDependencyResolveContext context) {
                CompositeFileCollection.this.visitDependencies(context);
            }

            @Override
            public String getDisplayName() {
                return CompositeFileCollection.this.getDisplayName();
            }
        };
    }

    @Override
    public FileCollection filter(final Spec<? super File> filterSpec) {
        return new CompositeFileCollection() {
            @Override
            public void visitContents(FileCollectionResolveContext context) {
                CompositeFileCollection.this.getSourceStream()
                    .forEach(collection -> context.add(collection.filter(filterSpec)));
            }

            @Override
            public void visitDependencies(TaskDependencyResolveContext context) {
                CompositeFileCollection.this.visitDependencies(context);
            }

            @Override
            public String getDisplayName() {
                return CompositeFileCollection.this.getDisplayName();
            }
        };
    }

    // This is final - use {@link TaskDependencyContainer#visitDependencies} to provide the dependencies instead.
    @Override
    public final TaskDependency getBuildDependencies() {
        return new AbstractTaskDependency() {
            @Override
            public String toString() {
                return CompositeFileCollection.this.toString() + " dependencies";
            }

            @Override
            public void visitDependencies(TaskDependencyResolveContext context) {
                CompositeFileCollection.this.visitDependencies(context);
            }
        };
    }

    @Override
    public void visitDependencies(TaskDependencyResolveContext context) {
        BuildDependenciesOnlyFileCollectionResolveContext fileContext = new BuildDependenciesOnlyFileCollectionResolveContext(context);
        visitContents(fileContext);
    }

    protected Iterable<? extends FileCollectionInternal> getSourceCollections() {
        return new Iterable<FileCollectionInternal>() {
            @Override
            public Iterator<FileCollectionInternal> iterator() {
                return (Iterator<FileCollectionInternal>) // TODO eliminate cast
                    getSourceStream()
                        .iterator();
            }
        };
    }

    protected Stream<? extends FileCollectionInternal> getSourceStream() {
        DefaultFileCollectionResolveContext context = new DefaultFileCollectionResolveContext(new IdentityFileResolver());
        visitContents(context);
        return context.resolveAsFileStream();
    }

    @Override
    public void registerWatchPoints(FileSystemSubset.Builder builder) {
        getSourceStream()
            .forEach(files -> files.registerWatchPoints(builder));
    }

    @Override
    public void visitLeafCollections(FileCollectionLeafVisitor visitor) {
        getSourceStream()
            .forEach(element -> element.visitLeafCollections(visitor));
    }
}
