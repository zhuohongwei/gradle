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

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class CompleteDirectoryNode extends AbstractSnapshotNode {
    private final Map<String, FileSystemLocationSnapshot> childrenMap;
    private final Node parent;
    private final DirectorySnapshot directorySnapshot;

    public CompleteDirectoryNode(Node parent, DirectorySnapshot directorySnapshot) {
        this.parent = parent;
        this.directorySnapshot = directorySnapshot;
        this.childrenMap = directorySnapshot.getChildren().stream()
            .collect(Collectors.toMap(
                FileSystemLocationSnapshot::getName,
                Function.identity()
            ));
    }

    @Nonnull
    @Override
    public Node getDescendant(Path path) {
        return getChildOrMissing(path);
    }

    private Node getChildOrMissing(Path path) {
        if (path.getNameCount() == 0) {
            return this;
        }
        Path childName = path.getName(0);
        FileSystemLocationSnapshot fileSystemLocationSnapshot = childrenMap.get(childName.toString());
        Node childNode = fileSystemLocationSnapshot != null
            ? convertToNode(fileSystemLocationSnapshot, this)
            : new MissingFileNode(this, getChildAbsolutePath(childName), childName);
        return path.getNameCount() == 1
            ? childNode
            : childNode.getDescendant(path.subpath(1, path.getNameCount()));
    }


    @Override
    public Node replaceDescendant(Path path, ChildNodeSupplier nodeSupplier) {
        if (path.getNameCount() == 0) {
            throw new IllegalArgumentException("Cannot replace child with empty path");
        }
        boolean directChild = path.getNameCount() == 1;
        Path childName = path.getName(0);
        FileSystemLocationSnapshot snapshot = childrenMap.get(childName.toString());
        if (snapshot == null) {
            return new MissingFileNode(this, getChildAbsolutePath(childName), childName).getDescendant(path.subpath(1, path.getNameCount()));
        }
        Node currentChild = convertToNode(snapshot, this);
        if (directChild) {
            Node replacedChild = nodeSupplier.create(currentChild);
            if (replacedChild instanceof AbstractSnapshotNode && replacedChild.getSnapshot().getHash().equals(currentChild.getSnapshot().getHash())) {
                return currentChild;
            }
            replaceByMutableNodeWithReplacedSnapshot(snapshot, replacedChild);
            return replacedChild;
        }

        return currentChild.replaceDescendant(path.subpath(1, path.getNameCount()), nodeSupplier);
    }

    @Override
    public void removeDescendant(Path path) {
        if (path.getNameCount() == 0) {
            throw new IllegalArgumentException("Cannot remove current node");
        }

        FileSystemLocationSnapshot childSnapshot = childrenMap.get(path.getName(0).toString());
        boolean directChild = path.getNameCount() == 1;
        if (directChild || childSnapshot == null) {
            replaceByMutableNodeWithReplacedSnapshot(childSnapshot, null);
        } else {
            convertToNode(childSnapshot, this).removeDescendant(path.subpath(1, path.getNameCount()));
        }
    }

    protected void replaceByMutableNodeWithReplacedSnapshot(@Nullable FileSystemLocationSnapshot snapshot, @Nullable Node replacement) {
        DefaultNode replacementForCurrentNode = new DefaultNode(Paths.get(directorySnapshot.getAbsolutePath()));
        directorySnapshot.getChildren().forEach(childSnapshot -> {
            if (childSnapshot != snapshot) {
                replacementForCurrentNode.addChild(
                    Paths.get(childSnapshot.getName()),
                    convertToNode(childSnapshot, replacementForCurrentNode)
                );
            } else if (snapshot != null && replacement != null) {
                replacementForCurrentNode.addChild(
                    Paths.get(snapshot.getName()),
                    replacement
                );
            }
        });
        parent.replaceDescendant(
            Paths.get(directorySnapshot.getName()),
            parent -> replacementForCurrentNode
        );
    }

    @Override
    public Path getAbsolutePath() {
        return Paths.get(directorySnapshot.getAbsolutePath());
    }

    @Override
    public Type getType() {
        return Type.DIRECTORY;
    }

    @Override
    public FileSystemLocationSnapshot getSnapshot() {
        return directorySnapshot;
    }

}
