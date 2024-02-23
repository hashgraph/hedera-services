/*
 * Copyright (C) 2018-2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.io.extendable.extensions.internal;

import java.util.concurrent.atomic.AtomicLong;

/**
 * A thread-safe implementation of the {@link Counter}
 */
public class ThreadSafeCounter implements Counter {

    /**
     * the count of bytes passed through the stream
     */
    private final AtomicLong count = new AtomicLong(0);

    /**
     * {@inheritDoc}
     */
    @Override
    public long addToCount(long value) {
        return count.addAndGet(value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void resetCount() {
        count.set(0);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getCount() {
        return count.get();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getAndResetCount() {
        return count.getAndSet(0);
    }
}
