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

import org.gradle.internal.snapshot.DirectorySnapshot;
import org.gradle.internal.snapshot.FileSystemLocationSnapshot;
import org.gradle.internal.snapshot.RegularFileSnapshot;

import javax.annotation.Nonnull;
import java.nio.file.Path;
import java.nio.file.Paths;

public abstract class AbstractSnapshotNode implements Node {

    @Nonnull
    @Override
    public abstract Node getDescendant(Path path);

    protected Node getMissingDescendant(Path path) {
        if (path.getNameCount() == 0) {
            return this;
        }
        Path childName = path.getName(0);
        MissingFileNode missingFileNode = new MissingFileNode(this, getChildAbsolutePath(childName), childName);
        return path.getNameCount() == 1 ? missingFileNode : missingFileNode.getMissingDescendant(path.subpath(1, path.getNameCount()));
    }

    public static AbstractSnapshotNode convertToNode(FileSystemLocationSnapshot snapshot, Node parent) {
        switch (snapshot.getType()) {
            case RegularFile:
                return new RegularFileNode(parent, (RegularFileSnapshot) snapshot);
            case Directory:
                return new CompleteDirectoryNode(parent, (DirectorySnapshot) snapshot);
            case Missing:
                return new MissingFileNode(parent, Paths.get(snapshot.getAbsolutePath()), Paths.get(snapshot.getName()));
            default:
                throw new AssertionError("Unknown type: " + snapshot.getType());
        }
    }

}
