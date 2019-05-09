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

package org.gradle.api.internal.tasks.execution;

import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.tasks.TaskExecuter;
import org.gradle.api.internal.tasks.TaskExecuterResult;
import org.gradle.api.internal.tasks.TaskExecutionContext;
import org.gradle.api.internal.tasks.TaskStateInternal;
import org.gradle.deployment.DeploymentHandle;
import org.gradle.deployment.DeploymentSpec;
import org.gradle.deployment.internal.DeploymentRegistry;

public class RequiresDeploymentTaskExecuter implements TaskExecuter {
    private final TaskExecuter executer;
    private final DeploymentRegistry deploymentRegistry;

    public RequiresDeploymentTaskExecuter(TaskExecuter executer, DeploymentRegistry deploymentRegistry) {
        this.executer = executer;
        this.deploymentRegistry = deploymentRegistry;
    }

    @Override
    public TaskExecuterResult execute(TaskInternal task, TaskStateInternal state, TaskExecutionContext context) {
        if (!task.getRequiredDeployments().isEmpty()) {
            for (String deploymentSpecName : task.getRequiredDeployments()) {
                DeploymentSpec spec = task.getProject().getDeployments().getByName(deploymentSpecName);
                DeploymentHandle handle = deploymentRegistry.get(spec.getName(), spec.getDeploymentHandleClass());
                if (handle == null) {
                    deploymentRegistry.start(spec.getName(), DeploymentRegistry.ChangeBehavior.NONE, spec.getDeploymentHandleClass(), spec.getConstructorArgs());
                }
            }
        }

        return executer.execute(task, state, context);
    }
}
