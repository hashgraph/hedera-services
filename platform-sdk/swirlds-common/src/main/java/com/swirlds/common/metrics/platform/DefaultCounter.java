/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

import com.swirlds.common.metrics.Counter;
import com.swirlds.common.metrics.platform.Snapshot.SnapshotEntry;
import java.util.List;
import java.util.concurrent.atomic.LongAdder;
import org.apache.commons.lang3.builder.ToStringBuilder;

/**
 * Platform-implementation of {@link Counter}
 */
public class DefaultCounter extends DefaultMetric implements Counter {

    private static final String INCREASE_ONLY_ERROR_MESSAGE = "The value of a a Counter can only be increased";

    private final LongAdder adder = new LongAdder();

    public DefaultCounter(final Counter.Config config) {
        super(config);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<SnapshotEntry> takeSnapshot() {
        return List.of(new SnapshotEntry(VALUE, get()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long get() {
        return adder.sum();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void add(final long value) {
        if (value <= 0) {
            throw new IllegalArgumentException(INCREASE_ONLY_ERROR_MESSAGE);
        }
        adder.add(value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void increment() {
        adder.increment();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return new ToStringBuilder(this, SHORT_PREFIX_STYLE)
                .appendSuper(super.toString())
                .append("value", adder.sum())
                .toString();
    }
}
