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
package org.gradle.api.internal.artifacts.verification.model;

import com.google.common.collect.Maps;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier;

import java.util.EnumMap;
import java.util.Map;
import java.util.stream.Collectors;

public class VerificationsBuilder {
    private final Map<ModuleComponentIdentifier, ComponentVerificationsBuilder> byComponent = Maps.newLinkedHashMap();

    public void addChecksum(ModuleComponentArtifactIdentifier artifact, ChecksumKind kind, String value) {
        ModuleComponentIdentifier componentIdentifier = artifact.getComponentIdentifier();
        byComponent.computeIfAbsent(componentIdentifier, id -> new ComponentVerificationsBuilder(id))
            .addChecksum(artifact, kind, value);
    }

    public DependencyVerifier build() {
        return new DependencyVerifier(
            byComponent.values()
                .stream()
                .map(ComponentVerificationsBuilder::build)
                .collect(Collectors.toList())
        );
    }

    private static class ComponentVerificationsBuilder {
        private final ModuleComponentIdentifier component;
        private final Map<ModuleComponentArtifactIdentifier, EnumMap<ChecksumKind, String>> checksums = Maps.newLinkedHashMap();

        private ComponentVerificationsBuilder(ModuleComponentIdentifier component) {
            this.component = component;
        }

        void addChecksum(ModuleComponentArtifactIdentifier artifact, ChecksumKind kind, String value) {
            checksums.computeIfAbsent(artifact, id -> Maps.newEnumMap(ChecksumKind.class)).put(kind, value);
        }

        private static ArtifactVerification toArtifactVerification(Map.Entry<ModuleComponentArtifactIdentifier, EnumMap<ChecksumKind, String>> entry) {
            return new ArtifactVerification(entry.getKey(), entry.getValue());
        }

        ComponentVerification build() {
            return new ComponentVerification(component,
                checksums.entrySet()
                    .stream()
                    .map(ComponentVerificationsBuilder::toArtifactVerification)
                    .collect(Collectors.toList())
                );
        }
    }
}
