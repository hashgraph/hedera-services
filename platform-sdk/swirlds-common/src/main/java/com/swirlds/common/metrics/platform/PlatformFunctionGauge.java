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
import com.swirlds.common.metrics.FunctionGauge;
import com.swirlds.common.metrics.PlatformMetric;
import com.swirlds.metrics.api.MetricConfig;
import com.swirlds.metrics.api.snapshot.Snapshot.SnapshotEntry;
import com.swirlds.metrics.impl.AbstractMetric;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.function.Supplier;

/**
 * Platform-implementation of {@link FunctionGauge}
 */
public class PlatformFunctionGauge<T> extends AbstractMetric implements PlatformMetric, FunctionGauge<T> {

    private final DataType dataType;
    private final Supplier<T> supplier;

    /**
     * Constructs a new PlatformFunctionGauge with the given configuration.
     * @param config the configuration for this function gauge
     */
    public PlatformFunctionGauge(@NonNull final FunctionGauge.Config<T> config) {
        super(config);
        this.dataType = MetricConfig.mapDataType(config.getType());
        this.supplier = config.getSupplier();
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
        return List.of(new SnapshotEntry(VALUE, get()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public T get() {
        return supplier.get();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .appendSuper(super.toString())
                .append("value", supplier.get())
                .toString();
    }
}
