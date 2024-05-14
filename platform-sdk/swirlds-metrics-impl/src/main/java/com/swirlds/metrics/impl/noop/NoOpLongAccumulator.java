/*
 * Copyright (C) 2018-2024 Hedera Hashgraph, LLC
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

package com.swirlds.metrics.impl.noop;

import com.swirlds.metrics.api.LongAccumulator;
import com.swirlds.metrics.api.MetricConfig;

/**
 * A no-op implementation of a long accumulator.
 */
public class NoOpLongAccumulator extends AbstractNoOpMetric implements LongAccumulator {

    public NoOpLongAccumulator(final MetricConfig<?, ?> config) {
        super(config);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final long get() {
        return 0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final long getInitialValue() {
        return 0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final void update(final long other) {}
}
