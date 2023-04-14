/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.threading.framework.config;

import com.swirlds.common.threading.framework.internal.AbstractExecutorServiceConfiguration;
import com.swirlds.common.threading.manager.ExecutorServiceRegistry;
import com.swirlds.common.threading.manager.internal.ManagedScheduledExecutorService;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

/**
 * Configures and builds an executor service. Executor service is tied to the lifecycle of the thread manager. If work
 * is submitted to any of the following methods prior to the start of the thread manager, then that work is buffered.
 * The exception to this rule are the invokeAll() and invokeAny() methods, which will throw if called prior to the
 * thread manager being started.
 */
public class ScheduledExecutorServiceConfiguration
        extends AbstractExecutorServiceConfiguration<ScheduledExecutorServiceConfiguration> {

    /**
     * Create a new executor service configuration.
     *
     * @param registry the executor service registry
     * @param name     the name of the executor service
     */
    public ScheduledExecutorServiceConfiguration(final ExecutorServiceRegistry registry, final String name) {
        super(registry, name);
    }

    /**
     * Build a cached thread pool executor service.
     *
     * @return a new executor service
     */
    @NonNull
    public ScheduledExecutorService build() {
        applyProfile();
        final ScheduledExecutorService baseExecutor =
                new ScheduledThreadPoolExecutor(getCorePoolSize(), buildThreadFactory());

        final ManagedScheduledExecutorService service = new ManagedScheduledExecutorService(baseExecutor);

        getRegistry().registerExecutorService(service);
        return service;
    }
}
