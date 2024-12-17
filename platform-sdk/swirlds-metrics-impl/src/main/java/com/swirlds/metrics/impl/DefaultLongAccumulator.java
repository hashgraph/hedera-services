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

package com.swirlds.metrics.impl;

import static com.swirlds.metrics.api.Metric.ValueType.VALUE;

import com.swirlds.base.utility.ToStringBuilder;
import com.swirlds.metrics.api.LongAccumulator;
import com.swirlds.metrics.api.snapshot.Snapshot.SnapshotEntry;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongBinaryOperator;
import java.util.function.LongSupplier;

/**
 * Platform-implementation of {@link LongAccumulator}
 */
public class DefaultLongAccumulator extends AbstractMetric implements LongAccumulator {

    private final AtomicLong container;
    private final LongBinaryOperator accumulator;
    private final LongSupplier initializer;

    public DefaultLongAccumulator(@NonNull final Config config) {
        super(config);
        final long initialValue = config.getInitialValue();
        final LongSupplier configInitializer = config.getInitializer();

        this.accumulator = config.getAccumulator();
        this.initializer = configInitializer != null ? configInitializer : () -> initialValue;
        this.container = new AtomicLong(this.initializer.getAsLong());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getInitialValue() {
        return initializer.getAsLong();
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public List<SnapshotEntry> takeSnapshot() {
        return List.of(new SnapshotEntry(VALUE, container.getAndSet(initializer.getAsLong())));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long get() {
        return container.get();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void update(final long other) {
        container.accumulateAndGet(other, accumulator);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void reset() {
        container.set(initializer.getAsLong());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .appendSuper(super.toString())
                .append("value", get())
                .toString();
    }
}
