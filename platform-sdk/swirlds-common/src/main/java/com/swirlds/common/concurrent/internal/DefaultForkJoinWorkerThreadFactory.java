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

package com.swirlds.common.concurrent.internal;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Objects;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinPool.ForkJoinWorkerThreadFactory;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.function.Supplier;

public class DefaultForkJoinWorkerThreadFactory implements ForkJoinWorkerThreadFactory {

    private final ThreadGroup threadGroup;

    private final Supplier<String> threadNameFactory;

    private final Runnable onStartup;

    public DefaultForkJoinWorkerThreadFactory(
            @NonNull final ThreadGroup threadGroup,
            @NonNull final Supplier<String> threadNameFactory,
            @Nullable final Runnable onStartup) {
        this.threadGroup = Objects.requireNonNull(threadGroup, "threadGroup must not be null");
        this.threadNameFactory = Objects.requireNonNull(threadNameFactory, "threadNameFactory must not be null");
        this.onStartup = onStartup;
    }

    @Override
    public ForkJoinWorkerThread newThread(@NonNull final ForkJoinPool pool) {
        return new DefaultForkJoinWorkerThread(threadNameFactory.get(), threadGroup, pool, true, onStartup);
    }

    @NonNull
    public static Supplier<String> createThreadNameFactory(@NonNull final String threadNamePrefix) {
        return DefaultThreadFactory.createThreadNameFactory(threadNamePrefix);
    }
}
