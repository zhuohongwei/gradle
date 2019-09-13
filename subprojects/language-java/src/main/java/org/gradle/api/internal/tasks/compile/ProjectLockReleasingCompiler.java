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

package org.gradle.api.internal.tasks.compile;

import org.gradle.api.tasks.WorkResult;
import org.gradle.internal.work.WorkerLeaseService;
import org.gradle.language.base.internal.compile.CompileSpec;
import org.gradle.language.base.internal.compile.Compiler;

/**
 * Quick and dirty hack to release the project lock for the compile task so multiple tasks can run in parallel.
 */
public class ProjectLockReleasingCompiler<T extends CompileSpec> implements Compiler<T> {
    private final WorkerLeaseService workerLeaseService;
    private final Compiler<T> delegate;

    public ProjectLockReleasingCompiler(WorkerLeaseService workerLeaseService, Compiler<T> delegate) {
        this.workerLeaseService = workerLeaseService;
        this.delegate = delegate;
    }

    @Override
    public WorkResult execute(final T spec) {
        return workerLeaseService.withoutProjectLock(() -> {
            return delegate.execute(spec);
        });
    }
}
