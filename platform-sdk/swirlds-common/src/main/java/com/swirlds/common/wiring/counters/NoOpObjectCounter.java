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

package com.swirlds.common.wiring.counters;

/**
 * A counter that doesn't actually count. Saves us from having to do a (counter == null) check in the standard case.
 */
public class NoOpObjectCounter extends ObjectCounter {

    private static final NoOpObjectCounter INSTANCE = new NoOpObjectCounter();

    /**
     * Get the singleton instance.
     *
     * @return the singleton instance
     */
    public static NoOpObjectCounter getInstance() {
        return INSTANCE;
    }

    /**
     * Constructor.
     */
    private NoOpObjectCounter() {}

    /**
     * {@inheritDoc}
     */
    @Override
    public void onRamp(final long delta) {}

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean attemptOnRamp(final long delta) {
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void forceOnRamp(final long delta) {}

    /**
     * {@inheritDoc}
     */
    @Override
    public void offRamp(final long delta) {}

    /**
     * {@inheritDoc}
     */
    @Override
    public long getCount() {
        return COUNT_UNDEFINED;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void waitUntilEmpty() {}
}
