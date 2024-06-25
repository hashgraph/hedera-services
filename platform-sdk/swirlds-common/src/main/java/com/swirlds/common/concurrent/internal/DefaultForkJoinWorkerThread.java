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
import java.util.concurrent.ForkJoinWorkerThread;

/**
 * A default implementation of {@link ForkJoinWorkerThread}.
 */
public class DefaultForkJoinWorkerThread extends ForkJoinWorkerThread {

    /**
     * The runnable to run on startup.
     */
    private final Runnable onStartup;

    /**
     * Create a new instance of {@link DefaultForkJoinWorkerThread}.
     *
     * @param name                 the name of the thread
     * @param group                the thread group
     * @param pool                 the fork join pool
     * @param preserveThreadLocals whether to preserve thread locals
     * @param onStartup            the runnable to run on startup
     */
    public DefaultForkJoinWorkerThread(
            @NonNull final String name,
            @NonNull final ThreadGroup group,
            @NonNull final ForkJoinPool pool,
            final boolean preserveThreadLocals,
            @Nullable final Runnable onStartup) {
        super(group, Objects.requireNonNull(pool, "pool must not be null"), preserveThreadLocals);
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(group, "group must not be null");
        setName(name);
        this.onStartup = onStartup;
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (onStartup != null) {
            onStartup.run();
        }
    }
}
