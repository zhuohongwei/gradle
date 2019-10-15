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

import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public abstract class AbstractMutableNode implements Node {
    private final ConcurrentHashMap<Path, Node> children = new ConcurrentHashMap<>();

    @Nullable
    @Override
    public Node getDescendant(Path path) {
        if (path.getNameCount() == 0) {
            return this;
        }
        Path childName = path.getName(0);
        Node child = children.get(childName);
        if (child == null) {
            return null;
        }
        return path.getNameCount() == 1
            ? child
            : child.getDescendant(path.subpath(1, path.getNameCount()));
    }

    @Override
    public Node replaceDescendant(Path path, ChildNodeSupplier nodeSupplier) {
        return replace(path, nodeSupplier, this);
    }

    protected Node replace(Path path, ChildNodeSupplier nodeSupplier, Node parent) {
        if (path.getNameCount() == 0) {
            return this;
        }
        boolean directChild = path.getNameCount() == 1;
        Path childName = path.getName(0);
        if (directChild) {
            return children.compute(childName, (key, current) -> nodeSupplier.create(parent));
        }
        return children.computeIfAbsent(childName, key -> new DefaultNode(path.isAbsolute() ? path.getRoot().resolve(childName) : parent.getAbsolutePath().resolve(childName)))
            .replaceDescendant(path.subpath(1, path.getNameCount()), nodeSupplier);
    }

    @Override
    public void removeDescendant(Path path) {
        if (path.getNameCount() == 0) {
            throw new UnsupportedOperationException("Can't remove current node");
        }
        boolean directChild = path.getNameCount() == 1;
        Path childName = path.getName(0);
        if (directChild) {
            children.remove(childName);
        } else {
            Node child = children.get(childName);
            if (child != null) {
                child.removeDescendant(path.subpath(1, path.getNameCount()));
            }
        }
    }

    public Map<Path, Node> getChildren() {
        return children;
    }
}
