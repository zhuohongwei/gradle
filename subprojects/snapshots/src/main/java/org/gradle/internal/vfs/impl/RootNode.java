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

import java.nio.file.Path;

public class RootNode extends AbstractMutableNode {

    @Override
    public Path getAbsolutePath() {
        throw new UnsupportedOperationException("Root does not have a path");
    }

    @Override
    public Type getType() {
        return Type.DIRECTORY;
    }

    @Override
    public FileSystemLocationSnapshot getSnapshot() {
        throw new UnsupportedOperationException("Cannot visit root node");
    }

    @Override
    public Path getChildAbsolutePath(Path name) {
        throw new UnsupportedOperationException("Cannot calculate path for root");
    }

    public void clear() {
        getChildren().clear();
    }
}
