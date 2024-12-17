/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.swirlds.base.time.internal;

import com.swirlds.base.time.Time;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;

/**
 * An implementation of {@link Time} that will return the true wall clock time (according to the OS).
 */
public final class OSTime implements Time {

    private static final class InstanceHolder {
        private static final Time INSTANCE = new OSTime();
    }

    private OSTime() {}

    /**
     * Get a static instance of a standard time implementation.
     */
    @NonNull
    public static Time getInstance() {
        return InstanceHolder.INSTANCE;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long nanoTime() {
        return System.nanoTime();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long currentTimeMillis() {
        return System.currentTimeMillis();
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public Instant now() {
        return Instant.now();
    }
}
