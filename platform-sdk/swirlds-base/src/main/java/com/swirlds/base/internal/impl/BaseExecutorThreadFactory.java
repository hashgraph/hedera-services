/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.swirlds.base.internal.impl;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A thread factory for creating threads for the base modules. All threads created by this factory are daemon threads
 * and have a low priority.
 */
class BaseExecutorThreadFactory implements ThreadFactory {

    private static final BaseExecutorThreadFactory instance = new BaseExecutorThreadFactory();

    private AtomicLong threadNumber = new AtomicLong(1);

    private BaseExecutorThreadFactory() {}

    @Override
    public Thread newThread(@NonNull final Runnable runnable) {
        Objects.requireNonNull(runnable, "runnable must not be null");
        final String name = "BaseExecutor-" + threadNumber.getAndIncrement();
        final Thread thread = new Thread(runnable, name);
        thread.setPriority(Thread.MIN_PRIORITY);
        thread.setDaemon(true);
        return thread;
    }

    /**
     * Returns the singleton instance of this factory.
     *
     * @return the instance
     */
    @NonNull
    public static BaseExecutorThreadFactory getInstance() {
        return instance;
    }
}
