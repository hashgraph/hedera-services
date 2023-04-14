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
import com.swirlds.common.threading.manager.internal.ManagedExecutorService;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.lang.Thread.UncaughtExceptionHandler;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Configures and builds an executor service with properties similar to {@link Executors#newCachedThreadPool()}.
 */
public class ExecutorServiceConfiguration extends AbstractExecutorServiceConfiguration<ExecutorServiceConfiguration> {

    public ExecutorServiceConfiguration(final ExecutorServiceRegistry registry, final String name) {
        super(registry, name);
    }

    // TODO document startup behavior

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public ExecutorServiceConfiguration setUncaughtExceptionHandler(
            @NonNull UncaughtExceptionHandler uncaughtExceptionHandler) {
        return super.setUncaughtExceptionHandler(uncaughtExceptionHandler);
    }

    /**
     * Build a cached thread pool executor service.
     *
     * @return a new executor service
     */
    @NonNull
    public ExecutorService build() {
        applyProfile();
        final ExecutorService baseExecutor = new ThreadPoolExecutor(
                getCorePoolSize(),
                getMaximumPoolSize(),
                getKeepAliveTime().toMillis(),
                TimeUnit.MILLISECONDS,
                buildQueue(),
                buildThreadFactory());

        final ManagedExecutorService service = new ManagedExecutorService(baseExecutor);

        getRegistry().registerExecutorService(service);
        return service;
    }
}
