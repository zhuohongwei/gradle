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
package org.gradle.execution;

import org.gradle.api.Project;
import org.gradle.api.internal.GradleInternal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DependencyVerificationConfigurationAction implements BuildConfigurationAction {
    private static final Logger LOGGER = LoggerFactory.getLogger(DependencyVerificationConfigurationAction.class);

    @Override
    public void configure(BuildExecutionContext context) {
        GradleInternal gradle = context.getGradle();
        if (gradle.getStartParameter().isWriteDependencyVerifications()) {
            gradle.buildFinished( r -> gradle.allprojects(DependencyVerificationConfigurationAction::resolveAllConfigurationsAndForceDownload));
        }
        context.proceed();
    }

    private static void resolveAllConfigurationsAndForceDownload(Project p) {
        p.getConfigurations().all(cnf -> {
            if (cnf.isCanBeResolved()) {
                try {
                    cnf.getFiles();
                } catch (Exception e) {
                    LOGGER.warn("Cannot resolve configuration {}: {}", cnf.getName(), e.getMessage());
                }
            }
        });
    }
}
