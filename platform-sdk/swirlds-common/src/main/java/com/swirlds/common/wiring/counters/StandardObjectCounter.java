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

package com.swirlds.common.wiring.counters;

import java.util.concurrent.atomic.AtomicLong;

/**
 * A utility for counting the number of objects in a various part of the pipeline.
 */
public class StandardObjectCounter extends ObjectCounter {

    // TODO write unit tests for this class

    private final AtomicLong count = new AtomicLong(0);

    /**
     * {@inheritDoc}
     */
    @Override
    public void onRamp() {
        count.incrementAndGet();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void interruptableOnRamp() {
        count.incrementAndGet();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean attemptOnRamp() {
        count.getAndIncrement();
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void forceOnRamp() {
        count.getAndIncrement();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void offRamp() {
        count.decrementAndGet();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getCount() {
        return count.get();
    }
}
