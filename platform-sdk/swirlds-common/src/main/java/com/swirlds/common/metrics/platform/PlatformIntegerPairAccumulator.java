/*
 * Copyright (C) 2016-2024 Hedera Hashgraph, LLC
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

import static com.swirlds.metrics.api.Metric.ValueType.VALUE;

import com.swirlds.base.utility.ToStringBuilder;
import com.swirlds.common.metrics.IntegerPairAccumulator;
import com.swirlds.common.metrics.PlatformMetric;
import com.swirlds.common.threading.atomic.AtomicIntPair;
import com.swirlds.metrics.api.MetricConfig;
import com.swirlds.metrics.api.snapshot.Snapshot.SnapshotEntry;
import com.swirlds.metrics.impl.AbstractMetric;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.IntSupplier;

/**
 * Platform-implementation of {@link IntegerPairAccumulator}
 */
public class PlatformIntegerPairAccumulator<T> extends AbstractMetric
        implements PlatformMetric, IntegerPairAccumulator<T> {

    private final DataType dataType;
    private final AtomicIntPair container;
    private final BiFunction<Integer, Integer, T> resultFunction;
    private final IntSupplier leftInitializer;
    private final IntSupplier rightInitializer;

    /**
     * Constructs a new PlatformIntegerPairAccumulator with the given configuration.
     * @param config the configuration for this integer pair accumulator
     */
    public PlatformIntegerPairAccumulator(@NonNull final Config<T> config) {
        super(config);
        this.dataType = MetricConfig.mapDataType(config.getType());
        this.container = new AtomicIntPair(config.getLeftAccumulator(), config.getRightAccumulator());
        this.resultFunction = config.getResultFunction();
        this.leftInitializer = config.getLeftInitializer();
        this.rightInitializer = config.getRightInitializer();

        this.container.set(leftInitializer.getAsInt(), rightInitializer.getAsInt());
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public DataType getDataType() {
        return dataType;
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public List<SnapshotEntry> takeSnapshot() {
        final T result =
                container.computeAndSet(resultFunction, leftInitializer.getAsInt(), rightInitializer.getAsInt());
        return List.of(new SnapshotEntry(VALUE, result));
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public T get() {
        return container.compute(resultFunction);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void reset() {
        container.set(leftInitializer.getAsInt(), rightInitializer.getAsInt());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getLeft() {
        return container.getLeft();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getRight() {
        return container.getRight();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void update(final int leftValue, final int rightValue) {
        container.accumulate(leftValue, rightValue);
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
