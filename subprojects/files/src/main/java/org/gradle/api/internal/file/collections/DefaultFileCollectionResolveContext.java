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
package org.gradle.api.internal.file.collections;

import org.gradle.api.Task;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.FileTreeInternal;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.api.tasks.TaskOutputs;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.internal.Cast;
import org.gradle.internal.Factory;
import org.gradle.internal.file.PathToFileResolver;
import org.gradle.internal.nativeintegration.services.FileSystems;
import org.gradle.util.DeferredUtil;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.Spliterator;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.util.Spliterators.spliteratorUnknownSize;

public class DefaultFileCollectionResolveContext implements ResolvableFileCollectionResolveContext {
    protected final PathToFileResolver fileResolver;
    private final Converter<? extends FileCollectionInternal> fileCollectionConverter;
    private final Converter<? extends FileTreeInternal> fileTreeConverter;
    private final Queue<Object> queue = new ArrayDeque<>();

    public DefaultFileCollectionResolveContext(FileResolver fileResolver) {
        this(fileResolver, new FileCollectionConverter(fileResolver.getPatternSetFactory()), new FileTreeConverter(fileResolver.getPatternSetFactory()));
    }

    private DefaultFileCollectionResolveContext(PathToFileResolver fileResolver, Converter<? extends FileCollectionInternal> fileCollectionConverter, Converter<? extends FileTreeInternal> fileTreeConverter) {
        this.fileResolver = fileResolver;
        this.fileCollectionConverter = fileCollectionConverter;
        this.fileTreeConverter = fileTreeConverter;
    }

    @Override
    public FileCollectionResolveContext add(Object element) {
        if (element != null) {
            queue.add(element);
        }
        return this;
    }

    @Override
    public FileCollectionResolveContext push(PathToFileResolver fileResolver) {
        ResolvableFileCollectionResolveContext nestedContext = newContext(fileResolver);
        add(nestedContext);
        return nestedContext;
    }

    protected DefaultFileCollectionResolveContext newContext(PathToFileResolver fileResolver) {
        return new DefaultFileCollectionResolveContext(fileResolver, fileCollectionConverter, fileTreeConverter);
    }

    @Override
    public final DefaultFileCollectionResolveContext newContext() {
        return newContext(fileResolver);
    }

    /**
     * Resolves the contents of this context as a list of atomic {@link FileTree} instances.
     */
    @Override
    public List<FileTreeInternal> resolveAsFileTrees() {
        return doResolve(fileTreeConverter)
            .collect(Collectors.toList());
    }

    /**
     * Resolves the contents of this context as a list of atomic {@link FileCollection} instances.
     */
    @Override
    public List<FileCollectionInternal> resolveAsFileCollections() {
        return resolveAsFileStream()
            .collect(Collectors.toList());
    }

    /**
     * Resolves the contents of this context as a Stream of atomic {@link FileCollection} instances.
     */
    @Override
    public Stream<? extends FileCollectionInternal> resolveAsFileStream() {
        return doResolve(fileCollectionConverter);
    }

    /**
     * Resolves the contents of this context as a list of atomic {@link MinimalFileCollection} instances.
     */
    public Stream<MinimalFileCollection> resolveAsMinimalFileCollections() {
        return doResolve(new MinimalFileCollectionConverter());
    }

    private <T> Stream<T> doResolve(Converter<T> converter) {
        Iterator<T> queueConsumer = new Iterator<T>() {
            private Iterator<? extends T> next = Collections.emptyIterator();

            @Override
            public boolean hasNext() {
                return next.hasNext()
                    || (!queue.isEmpty() && hasNextBatch());
            }

            private boolean hasNextBatch() {
                next = nextBatch(converter, queue.remove()).iterator();
                return hasNext();
            }

            @Override
            public T next() {
                return next.next();
            }
        };

        return asStream(queueConsumer);
    }

    // TODO - need to sync with BuildDependenciesOnlyFileCollectionResolveContext
    private <T> Stream<? extends T> nextBatch(Converter<T> converter, Object element) {
        if (element instanceof DefaultFileCollectionResolveContext) {
            return converter.convertInto(element, fileResolver);
        } else if (element instanceof FileCollectionContainer) {
            return resolveNested(converter, (FileCollectionContainer) element);
        } else if (element instanceof FileCollection || element instanceof MinimalFileCollection) {
            return converter.convertInto(element, fileResolver);
        } else if (element instanceof Task) {
            return nextBatch(converter, ((Task) element).getOutputs().getFiles());
        } else if (element instanceof TaskOutputs) {
            return nextBatch(converter, ((TaskOutputs) element).getFiles());
        } else if (DeferredUtil.isDeferred(element)) {
            return nextBatch(converter, DeferredUtil.unpack(element));
        } else if (element instanceof Path) {
            return nextBatch(converter, ((Path) element).toFile());
        } else {
            if (element instanceof Object[]) {
                element = Arrays.asList((Object[]) element);
            }

            if (element instanceof Iterable) {
                return resolveElements(converter, (Iterable<?>) element);
            } else if (element != null) {
                return converter.convertInto(element, fileResolver);
            } else {
                return Stream.empty();
            }
        }
    }

    private <T> Stream<T> resolveElements(Converter<T> converter, Iterable<?> element) {
        return asStream(element.iterator())
            .flatMap(e -> nextBatch(converter, e));
    }

    // TODO move out
    public static <T> Stream<T> asStream(Iterator<T> iterator) {
        Spliterator<T> spliterator = spliteratorUnknownSize(iterator, Spliterator.ORDERED);
        return StreamSupport.stream(spliterator, false);
    }

    private <T> Stream<T> resolveNested(Converter<T> converter, FileCollectionContainer element) {
        DefaultFileCollectionResolveContext context = newContext();
        element.visitContents(context);
        return resolveElements(converter, context.queue);
    }

    public interface Converter<T> {
        Stream<? extends T> convertInto(Object element, PathToFileResolver resolver);
    }

    public static class FileCollectionConverter implements Converter<FileCollectionInternal> {
        private final Factory<PatternSet> patternSetFactory;

        public FileCollectionConverter(Factory<PatternSet> patternSetFactory) {
            this.patternSetFactory = patternSetFactory;
        }

        @Override
        public Stream<? extends FileCollectionInternal> convertInto(Object element, PathToFileResolver fileResolver) {
            if (element instanceof DefaultFileCollectionResolveContext) {
                return ((DefaultFileCollectionResolveContext) element).resolveAsFileStream();
            } else if (element instanceof FileCollection) {
                return Stream.of(Cast.cast(FileCollectionInternal.class, element));
            } else if (element instanceof MinimalFileTree) {
                return Stream.of(new FileTreeAdapter((MinimalFileTree) element, patternSetFactory));
            } else if (element instanceof MinimalFileSet) {
                return Stream.of(new FileCollectionAdapter((MinimalFileSet) element));
            } else if (element instanceof MinimalFileCollection) {
                throw new UnsupportedOperationException(String.format("Cannot convert instance of %s to FileTree", element.getClass().getSimpleName()));
            } else if (element instanceof TaskDependency) {
                // Ignore
                return Stream.empty();
            } else {
                return Stream.of(new FileCollectionAdapter(new ListBackedFileSet(fileResolver.resolve(element))));
            }
        }
    }

    public static class FileTreeConverter implements Converter<FileTreeInternal> {
        private final Factory<PatternSet> patternSetFactory;

        public FileTreeConverter(Factory<PatternSet> patternSetFactory) {
            this.patternSetFactory = patternSetFactory;
        }

        @Override
        public Stream<FileTreeInternal> convertInto(Object element, PathToFileResolver fileResolver) {
            if (element instanceof DefaultFileCollectionResolveContext) {
                return ((DefaultFileCollectionResolveContext) element).resolveAsFileTrees().stream();
            } else if (element instanceof FileTree) {
                return Stream.of(Cast.cast(FileTreeInternal.class, element));
            } else if (element instanceof MinimalFileTree) {
                return Stream.of(new FileTreeAdapter((MinimalFileTree) element, patternSetFactory));
            } else if (element instanceof MinimalFileSet) {
                return ((MinimalFileSet) element).getFiles().stream()
                    .flatMap(f -> convertFileToFileTree(f));
            } else if (element instanceof FileCollection) {
                return StreamSupport.stream(((FileCollection) element).spliterator(), false)
                    .flatMap(f -> convertFileToFileTree(f));
            } else if (element instanceof MinimalFileCollection) {
                throw new UnsupportedOperationException(String.format("Cannot convert instance of %s to FileTree", element.getClass().getSimpleName()));
            } else if (element instanceof TaskDependency) {
                // Ignore
                return Stream.empty();
            } else {
                return convertFileToFileTree(fileResolver.resolve(element));
            }
        }

        private Stream<FileTreeInternal> convertFileToFileTree(File file) {
            if (file.isDirectory()) {
                return Stream.of(new FileTreeAdapter(new DirectoryFileTree(file, patternSetFactory.create(), FileSystems.getDefault()), patternSetFactory));
            } else if (file.isFile()) {
                return Stream.of(new FileTreeAdapter(new DefaultSingletonFileTree(file), patternSetFactory));
            } else {
                return Stream.empty();
            }
        }
    }

    public static class MinimalFileCollectionConverter implements Converter<MinimalFileCollection> {
        @Override
        public Stream<MinimalFileCollection> convertInto(Object element, PathToFileResolver resolver) {
            if (element instanceof DefaultFileCollectionResolveContext) {
                return ((DefaultFileCollectionResolveContext) element).resolveAsMinimalFileCollections();
            } else if (element instanceof MinimalFileCollection) {
                return Stream.of((MinimalFileCollection) element);
            } else if (element instanceof FileCollection) {
                throw new UnsupportedOperationException(String.format("Cannot convert instance of %s to MinimalFileCollection", element.getClass().getSimpleName()));
            } else if (element instanceof TaskDependency) {
                // Ignore
                return Stream.empty();
            } else {
                return Stream.of(new ListBackedFileSet(resolver.resolve(element)));
            }
        }
    }
}

