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

package com.swirlds.common.metrics.platform;

import static com.swirlds.common.metrics.Metric.ValueType.VALUE;

import com.swirlds.common.metrics.IntegerAccumulator;
import com.swirlds.common.metrics.platform.Snapshot.SnapshotEntry;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntBinaryOperator;
import java.util.function.IntSupplier;
import org.apache.commons.lang3.builder.ToStringBuilder;

/**
 * Platform-implementation of {@link IntegerAccumulator}
 */
public class DefaultIntegerAccumulator extends DefaultMetric implements IntegerAccumulator {

    private final AtomicInteger container;
    private final IntBinaryOperator accumulator;
    private final IntSupplier initializer;

    public DefaultIntegerAccumulator(final IntegerAccumulator.Config config) {
        super(config);
        this.accumulator = config.getAccumulator();
        final int initialValue = config.getInitialValue();
        this.initializer = config.getInitializer() != null ? config.getInitializer() : () -> initialValue;
        this.container = new AtomicInteger(this.initializer.getAsInt());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getInitialValue() {
        return initializer.getAsInt();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<SnapshotEntry> takeSnapshot() {
        return List.of(new SnapshotEntry(VALUE, container.getAndSet(initializer.getAsInt())));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int get() {
        return container.get();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void update(final int other) {
        container.accumulateAndGet(other, accumulator);
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
