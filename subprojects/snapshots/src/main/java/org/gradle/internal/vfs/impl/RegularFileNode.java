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
import org.gradle.internal.snapshot.RegularFileSnapshot;

import javax.annotation.Nonnull;
import java.nio.file.Path;
import java.nio.file.Paths;

public class RegularFileNode extends AbstractSnapshotNode {
    private final RegularFileSnapshot snapshot;
    private final Node parent;

    public RegularFileNode(Node parent, RegularFileSnapshot snapshot) {
        this.snapshot = snapshot;
        this.parent = parent;
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
        parent.removeDescendant(Paths.get(snapshot.getName()));
    }

    @Override
    public Path getAbsolutePath() {
        return Paths.get(snapshot.getAbsolutePath());
    }

    @Override
    public Type getType() {
        return Type.FILE;
    }

    @Override
    public FileSystemLocationSnapshot getSnapshot() {
        return snapshot;
    }
}
