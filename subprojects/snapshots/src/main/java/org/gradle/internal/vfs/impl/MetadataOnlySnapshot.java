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

package org.gradle.internal.vfs.impl;

import org.gradle.internal.file.FileType;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.snapshot.AbstractFileSystemLocationSnapshot;
import org.gradle.internal.snapshot.FileSystemLocationSnapshot;
import org.gradle.internal.snapshot.FileSystemSnapshotVisitor;
import org.gradle.internal.snapshot.MissingFileSnapshot;

import java.util.Optional;

public class MetadataOnlySnapshot extends AbstractFileSystemLocationSnapshot implements IncompleteFileSystemLocationSnapshot {

    private final FileType type;

    public MetadataOnlySnapshot(String absolutePath, String name, FileType type) {
        super(absolutePath, name);
        this.type = type;
    }

    @Override
    public FileType getType() {
        return type;
    }

    @Override
    public HashCode getHash() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isContentAndMetadataUpToDate(FileSystemLocationSnapshot other) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void accept(FileSystemSnapshotVisitor visitor) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Optional<FileSystemLocationSnapshot> getChild(String filePath, int offset) {
        switch (getType()) {
            case RegularFile:
            case Missing:
                return Optional.of(new MissingFileSnapshot(filePath, getFileNameForAbsolutePath(filePath)));
            case Directory:
                return Optional.empty();
            default:
                throw new AssertionError("Unknown file type: " + getType());
        }
    }
}
