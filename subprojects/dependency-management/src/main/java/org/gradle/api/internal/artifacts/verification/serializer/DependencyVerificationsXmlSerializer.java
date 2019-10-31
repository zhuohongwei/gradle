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
package org.gradle.api.internal.artifacts.verification.serializer;

import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.artifacts.verification.model.ArtifactVerification;
import org.gradle.api.internal.artifacts.verification.model.ChecksumKind;
import org.gradle.api.internal.artifacts.verification.model.ComponentVerification;
import org.gradle.api.internal.artifacts.verification.model.DependencyVerifier;
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier;
import org.gradle.internal.xml.SimpleXmlWriter;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;

public class DependencyVerificationsXmlSerializer {
    private final SimpleXmlWriter writer;

    private DependencyVerificationsXmlSerializer(OutputStream out) throws IOException {
        this.writer = new SimpleXmlWriter(out);
    }

    public static void serialize(DependencyVerifier verifier, OutputStream out) throws IOException {
        DependencyVerificationsXmlSerializer writer = new DependencyVerificationsXmlSerializer(out);
        writer.write(verifier);
    }

    private void write(DependencyVerifier verifier) throws IOException {
        writer.startElement("dependency-verifications");
        writeVerifications(verifier.getVerifications());
        writer.endElement();
        writer.close();
    }

    private void writeVerifications(List<ComponentVerification> verifications) throws IOException {
        writer.startElement("components");
        for (ComponentVerification verification : verifications) {
            writeVerification(verification);
        }
        writer.endElement();
    }

    private void writeVerification(ComponentVerification verification) throws IOException {
        ComponentIdentifier component = verification.getComponent();
        if (component instanceof ModuleComponentIdentifier) {
            ModuleComponentIdentifier mci = (ModuleComponentIdentifier) component;
            writer.startElement("component");
            writer.attribute("group", mci.getGroup());
            writer.attribute("name", mci.getModule());
            writer.attribute("version", mci.getVersion());
            writeArtifactVerifications(verification.getVerifications());
            writer.endElement();
        }
    }

    private void writeArtifactVerifications(List<ArtifactVerification> verifications) throws IOException {
        writer.startElement("artifacts");
        for (ArtifactVerification verification : verifications) {
            writeArtifactVerification(verification);
        }
        writer.endElement();
    }

    private void writeArtifactVerification(ArtifactVerification verification) throws IOException {
        ComponentArtifactIdentifier artifact = verification.getArtifact();
        if (artifact instanceof ModuleComponentArtifactIdentifier) {
            ModuleComponentArtifactIdentifier mcai = (ModuleComponentArtifactIdentifier) artifact;
            writer.startElement("artifact");
            writer.attribute("name", mcai.getFileName());
            writeChecksums(verification.getChecksums());
            writer.endElement();
        }
    }

    private void writeChecksums(Map<ChecksumKind, String> checksums) throws IOException {
        for (Map.Entry<ChecksumKind, String> entry : checksums.entrySet()) {
            String kind = entry.getKey().name();
            String value = entry.getValue();
            writer.startElement(kind);
            writer.characters(value);
            writer.endElement();
        }
    }
}
