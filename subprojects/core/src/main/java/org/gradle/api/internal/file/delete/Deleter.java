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
package org.gradle.api.internal.file.delete;

import org.gradle.api.Action;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.file.DeleteSpec;
import org.gradle.api.file.UnableToDeleteFileException;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.tasks.WorkResult;
import org.gradle.api.tasks.WorkResults;
import org.gradle.internal.nativeintegration.filesystem.FileSystem;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.internal.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public class Deleter {
    private static final Logger LOGGER = LoggerFactory.getLogger(Deleter.class);

    private final FileResolver fileResolver;
    private final FileSystem fileSystem;
    private final Clock clock;

    private static final int DELETE_RETRY_SLEEP_MILLIS = 10;

    static final int MAX_REPORTED_PATHS = 16;

    static final String HELP_FAILED_DELETE_CHILDREN = "Failed to delete some children. This might happen because a process has files open or has its working directory set in the target directory.";
    static final String HELP_NEW_CHILDREN = "New files were found. This might happen because a process is still writing to the target directory.";

    public Deleter(FileResolver fileResolver, FileSystem fileSystem, Clock clock) {
        this.fileResolver = fileResolver;
        this.fileSystem = fileSystem;
        this.clock = clock;
    }

    public boolean delete(Object... paths) {
        final Object[] innerPaths = paths;
        return delete(new Action<DeleteSpec>() {
            @Override
            public void execute(DeleteSpec deleteSpec) {
                deleteSpec.delete(innerPaths).setFollowSymlinks(false);
            }
        }).getDidWork();
    }

    public WorkResult delete(Action<? super DeleteSpec> action) {
        final AtomicBoolean didWork = new AtomicBoolean();

        DeleteSpecInternal deleteSpec = new DefaultDeleteSpec();
        action.execute(deleteSpec);

        Object[] paths = deleteSpec.getPaths();
        Collection<String> failedPaths = new ArrayList<String>();
        Set<FileVisitOption> options = deleteSpec.isFollowSymlinks() ? Collections.singleton(FileVisitOption.FOLLOW_LINKS) : Collections.emptySet();
        for (File baseDir : fileResolver.resolveFiles(paths)) {
            long startTime = clock.getCurrentTime();
            try {
                Files.walkFileTree(baseDir.toPath(), options, Integer.MAX_VALUE, new FileVisitor<Path>() {
                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                        LOGGER.debug("Deleting directory {}", dir);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        LOGGER.debug("Deleting file {}", file);
                        didWork.set(true);
                        try {
                            Files.delete(file);
                        } catch (IOException e) {
                            try {
                                handleFailedDelete(file);
                            } catch (IOException e2) {
                                failedPaths.add(file.toString());
                            }
                        }

                        // Fail fast
                        if (failedPaths.size() >= MAX_REPORTED_PATHS) {
                            return FileVisitResult.TERMINATE;
                        }
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                        failedPaths.add(file.toString());
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                        LOGGER.debug("Deleted directory {}", dir);
                        return FileVisitResult.CONTINUE;
                    }
                });
            } catch (IOException e) {
                throw new UncheckedIOException("Could not delete " + baseDir, e);
            }

            if (failedPaths.size() > 0) {
                throwWithHelpMessage(startTime, baseDir, deleteSpec, failedPaths, true);
            }
        }
        return WorkResults.didWork(didWork.get());
    }

    private void handleFailedDelete(Path file) throws IOException {
        // This is copied from Ant (see org.apache.tools.ant.util.FileUtils.tryHardToDelete).
        // It mentions that there is a bug in the Windows JDK impls that this is a valid
        // workaround for. I've been unable to find a definitive reference to this bug.
        // The thinking is that if this is good enough for Ant, it's good enough for us.
        if (isRunGcOnFailedDelete()) {
            System.gc();
        }
        try {
            Thread.sleep(DELETE_RETRY_SLEEP_MILLIS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }

        Files.delete(file);
    }

    private boolean isRunGcOnFailedDelete() {
        return OperatingSystem.current().isWindows();
    }

    private void throwWithHelpMessage(long startTime, File file, DeleteSpecInternal deleteSpec, Collection<String> failedPaths, boolean more) {
        throw new UnableToDeleteFileException(file, buildHelpMessageForFailedDelete(startTime, file, deleteSpec, failedPaths, more));
    }

    private String buildHelpMessageForFailedDelete(long startTime, File file, DeleteSpecInternal deleteSpec, Collection<String> failedPaths, boolean more) {

        boolean isSymlink = fileSystem.isSymlink(file);
        boolean isDirectory = file.isDirectory();

        StringBuilder help = new StringBuilder("Unable to delete ");
        if (isSymlink) {
            help.append("symlink to ");
        }
        if (isDirectory) {
            help.append("directory ");
        } else {
            help.append("file ");
        }
        help.append('\'').append(file).append('\'');

        if (isDirectory && (deleteSpec.isFollowSymlinks() || !isSymlink)) {

            String absolutePath = file.getAbsolutePath();
            failedPaths.remove(absolutePath);
            if (!failedPaths.isEmpty()) {
                help.append("\n  ").append(HELP_FAILED_DELETE_CHILDREN);
                for (String failed : failedPaths) {
                    help.append("\n  - ").append(failed);
                }
                if (more) {
                    help.append("\n  - and more ...");
                }
            }

            Collection<String> newPaths = listNewPaths(startTime, file, failedPaths);
            if (!newPaths.isEmpty()) {
                help.append("\n  ").append(HELP_NEW_CHILDREN);
                for (String newPath : newPaths) {
                    help.append("\n  - ").append(newPath);
                }
                if (newPaths.size() == MAX_REPORTED_PATHS) {
                    help.append("\n  - and more ...");
                }
            }
        }
        return help.toString();
    }

    private Collection<String> listNewPaths(long startTime, File directory, Collection<String> failedPaths) {
        List<String> paths = new ArrayList<String>(MAX_REPORTED_PATHS);
        Deque<File> stack = new ArrayDeque<File>();
        stack.push(directory);
        while (!stack.isEmpty() && paths.size() < MAX_REPORTED_PATHS) {
            File current = stack.pop();
            String absolutePath = current.getAbsolutePath();
            if (!current.equals(directory) && !failedPaths.contains(absolutePath) && current.lastModified() >= startTime) {
                paths.add(absolutePath);
            }
            if (current.isDirectory()) {
                File[] children = current.listFiles();
                if (children != null) {
                    for (File child : children) {
                        stack.push(child);
                    }
                }
            }
        }
        return paths;
    }
}
