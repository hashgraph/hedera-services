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

package com.swirlds.common.metrics.extensions;

import com.swirlds.common.metrics.Metrics;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * A NoOp implementation of {@link FractionalTimer}.
 */
public class NoOpFractionalTimer implements FractionalTimer {

    private static final NoOpFractionalTimer INSTANCE = new NoOpFractionalTimer();

    private NoOpFractionalTimer() {}

    /**
     * Get the singleton instance.
     *
     * @return the singleton instance
     */
    public static FractionalTimer getInstance() {
        return INSTANCE;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void registerMetric(
            @NonNull Metrics metrics, @NonNull String category, @NonNull String name, @NonNull String description) {}

    /**
     * {@inheritDoc}
     */
    @Override
    public void activate(final long now) {}

    /**
     * {@inheritDoc}
     */
    @Override
    public void activate() {}

    /**
     * {@inheritDoc}
     */
    @Override
    public void deactivate(final long now) {}

    /**
     * {@inheritDoc}
     */
    @Override
    public void deactivate() {}

    /**
     * {@inheritDoc}
     */
    @Override
    public double getActiveFraction() {
        return 0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getAndReset() {
        return 0;
    }
}
