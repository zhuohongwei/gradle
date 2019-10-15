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

import org.gradle.internal.snapshot.FileSystemLocationSnapshot;
import org.gradle.internal.snapshot.MissingFileSnapshot;

import javax.annotation.Nonnull;
import java.nio.file.Path;

public class MissingFileNode extends AbstractSnapshotNode {

    private final Node parent;
    private final Path absolutePath;
    private final Path name;

    public MissingFileNode(Node parent, Path absolutePath, Path name) {
        this.parent = parent;
        this.absolutePath = absolutePath;
        this.name = name;
    }

    @Nonnull
    @Override
    public Node getDescendant(Path path) {
        return getMissingDescendant(path);
    }

    @Override
    public Node replaceDescendant(Path path, ChildNodeSupplier nodeSupplier) {
        return getMissingDescendant(path);
    }

    @Override
    public void removeDescendant(Path path) {
        parent.removeDescendant(name);
    }

    @Override
    public Path getAbsolutePath() {
        return absolutePath;
    }

    @Override
    public Type getType() {
        return Type.MISSING;
    }

    @Override
    public FileSystemLocationSnapshot getSnapshot() {
        return new MissingFileSnapshot(absolutePath.toString(), name.toString());
    }

}
