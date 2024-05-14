/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

import com.swirlds.metrics.api.Counter;
import com.swirlds.metrics.api.Counter.Config;
import com.swirlds.metrics.api.DoubleAccumulator;
import com.swirlds.metrics.api.DoubleGauge;
import com.swirlds.metrics.api.IntegerAccumulator;
import com.swirlds.metrics.api.IntegerGauge;
import com.swirlds.metrics.api.LongAccumulator;
import com.swirlds.metrics.api.LongGauge;
import com.swirlds.metrics.api.MetricsFactory;
import edu.umd.cs.findbugs.annotations.NonNull;

public class NoOpMetricsFactory implements MetricsFactory {

    private static final class InstanceHolder {
        private static final MetricsFactory INSTANCE = new NoOpMetricsFactory();
    }

    private NoOpMetricsFactory() {}

    @NonNull
    @Override
    public Counter createCounter(@NonNull Config config) {
        return new NoOpCounter(config);
    }

    @NonNull
    @Override
    public DoubleAccumulator createDoubleAccumulator(@NonNull DoubleAccumulator.Config config) {
        return new NoOpDoubleAccumulator(config);
    }

    @NonNull
    @Override
    public DoubleGauge createDoubleGauge(@NonNull DoubleGauge.Config config) {
        return new NoOpDoubleGauge(config);
    }

    @NonNull
    @Override
    public IntegerAccumulator createIntegerAccumulator(@NonNull IntegerAccumulator.Config config) {
        return new NoOpIntegerAccumulator(config);
    }

    @NonNull
    @Override
    public IntegerGauge createIntegerGauge(@NonNull IntegerGauge.Config config) {
        return new NoOpIntegerGauge(config);
    }

    @NonNull
    @Override
    public LongAccumulator createLongAccumulator(@NonNull LongAccumulator.Config config) {
        return new NoOpLongAccumulator(config);
    }

    @NonNull
    @Override
    public LongGauge createLongGauge(@NonNull LongGauge.Config config) {
        return new NoOpLongGauge(config);
    }

    @NonNull
    public static MetricsFactory getInstance() {
        return InstanceHolder.INSTANCE;
    }
}
