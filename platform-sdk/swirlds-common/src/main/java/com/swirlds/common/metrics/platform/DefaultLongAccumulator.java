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
import static org.apache.commons.lang3.builder.ToStringStyle.SHORT_PREFIX_STYLE;

import com.swirlds.common.metrics.LongAccumulator;
import com.swirlds.common.metrics.platform.Snapshot.SnapshotEntry;
import java.util.List;
import org.apache.commons.lang3.builder.ToStringBuilder;

/**
 * Platform-implementation of {@link LongAccumulator}
 */
public class DefaultLongAccumulator extends DefaultMetric implements LongAccumulator {

    private final java.util.concurrent.atomic.LongAccumulator container;
    private final long initialValue;

    public DefaultLongAccumulator(final LongAccumulator.Config config) {
        super(config);
        this.container =
                new java.util.concurrent.atomic.LongAccumulator(config.getAccumulator(), config.getInitialValue());
        this.initialValue = config.getInitialValue();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getInitialValue() {
        return initialValue;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<SnapshotEntry> takeSnapshot() {
        return List.of(new SnapshotEntry(VALUE, container.getThenReset()));
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
        container.accumulate(other);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return new ToStringBuilder(this, SHORT_PREFIX_STYLE)
                .appendSuper(super.toString())
                .append("initialValue", initialValue)
                .append("value", get())
                .toString();
    }
}
