/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.nativeplatform.toolchain.internal.metadata;

import org.gradle.api.Action;
import org.gradle.internal.vfs.VirtualFileSystem;
import org.gradle.internal.vfs.impl.RoutingVirtualFileSystem;
import org.gradle.nativeplatform.toolchain.internal.gcc.metadata.GccMetadata;
import org.gradle.nativeplatform.toolchain.internal.gcc.metadata.GccMetadataProvider;
import org.gradle.nativeplatform.toolchain.internal.swift.metadata.SwiftcMetadata;
import org.gradle.nativeplatform.toolchain.internal.swift.metadata.SwiftcMetadataProvider;
import org.gradle.platform.base.internal.toolchain.SearchResult;
import org.gradle.process.internal.ExecActionFactory;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CompilerMetaDataProviderFactory {
    private final CachingCompilerMetaDataProvider<GccMetadata> gcc;
    private final CachingCompilerMetaDataProvider<GccMetadata> clang;
    private final CachingCompilerMetaDataProvider<SwiftcMetadata> swiftc;

    public CompilerMetaDataProviderFactory(ExecActionFactory execActionFactory, VirtualFileSystem virtualFileSystem) {
        RoutingVirtualFileSystem routingVirtualFileSystem = (RoutingVirtualFileSystem) virtualFileSystem;
        gcc = new CachingCompilerMetaDataProvider<>(GccMetadataProvider.forGcc(execActionFactory), routingVirtualFileSystem);
        clang = new CachingCompilerMetaDataProvider<>(GccMetadataProvider.forClang(execActionFactory), routingVirtualFileSystem);
        swiftc = new CachingCompilerMetaDataProvider<>(new SwiftcMetadataProvider(execActionFactory), routingVirtualFileSystem);
    }

    public CompilerMetaDataProvider<GccMetadata> gcc() {
        return gcc;
    }

    public CompilerMetaDataProvider<GccMetadata> clang() {
        return clang;
    }

    public CompilerMetaDataProvider<SwiftcMetadata> swiftc() {
        return swiftc;
    }

    private static class CachingCompilerMetaDataProvider<T extends CompilerMetadata> implements CompilerMetaDataProvider<T> {
        private final CompilerMetaDataProvider<T> delegate;
        private final RoutingVirtualFileSystem routingVirtualFileSystem;
        private final Map<Key, SearchResult<T>> resultMap = new HashMap<>();

        private CachingCompilerMetaDataProvider(CompilerMetaDataProvider<T> delegate, RoutingVirtualFileSystem routingVirtualFileSystem) {
            this.delegate = delegate;
            this.routingVirtualFileSystem = routingVirtualFileSystem;
        }

        @Override
        public SearchResult<T> getCompilerMetaData(List<File> path, Action<? super CompilerExecSpec> configureAction) {
            AbstractMetadataProvider.DefaultCompilerExecSpec execSpec = new AbstractMetadataProvider.DefaultCompilerExecSpec();
            configureAction.execute(execSpec);

            Key key = new Key(execSpec.executable, execSpec.args, path, execSpec.environments);
            SearchResult<T> result = resultMap.get(key);
            if (result == null) {
                result = delegate.getCompilerMetaData(path, configureAction);
                resultMap.put(key, result);
                if (result.isAvailable()) {
                    T component = result.getComponent();
                    if (component instanceof GccMetadata) {
                        routingVirtualFileSystem.addImmutableLocations(((GccMetadata) component).getSystemLibraries().getIncludeDirs());
                    }
                }
            }
            return result;
        }

        @Override
        public CompilerType getCompilerType() {
            return delegate.getCompilerType();
        }
    }

    private static class Key {
        final File gccBinary;
        final List<String> args;
        final List<File> path;
        private final Map<String, ?> environmentVariables;

        private Key(File gccBinary, List<String> args, List<File> path, Map<String, ?> environmentVariables) {
            this.gccBinary = gccBinary;
            this.args = args;
            this.path = path;
            this.environmentVariables = environmentVariables;
        }

        @Override
        public boolean equals(Object obj) {
            Key other = (Key) obj;
            return other.gccBinary.equals(gccBinary) && other.args.equals(args) && other.path.equals(path) && other.environmentVariables.equals(environmentVariables);
        }

        @Override
        public int hashCode() {
            return gccBinary.hashCode() ^ args.hashCode() ^ path.hashCode() ^ environmentVariables.hashCode();
        }
    }
}
