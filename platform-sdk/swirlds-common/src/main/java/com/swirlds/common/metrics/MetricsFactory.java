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

package com.swirlds.common.metrics;

/**
 * Factory for basic {@link Metric}-implementations
 */
public interface MetricsFactory {

    /**
     * Creates a {@link Counter}
     *
     * @param config the configuration
     * @return the new {@code Counter}
     * @throws IllegalArgumentException if {@code config} is {@code null}
     */
    Counter createCounter(final Counter.Config config);

    /**
     * Creates a {@link DoubleAccumulator}
     *
     * @param config the configuration
     * @return the new {@code DoubleAccumulator}
     * @throws IllegalArgumentException if {@code config} is {@code null}
     */
    DoubleAccumulator createDoubleAccumulator(final DoubleAccumulator.Config config);

    /**
     * Creates a {@link DoubleGauge}
     *
     * @param config the configuration
     * @return the new {@code DoubleGauge}
     * @throws IllegalArgumentException if {@code config} is {@code null}
     */
    DoubleGauge createDoubleGauge(final DoubleGauge.Config config);

    /**
     * Creates a {@link IntegerAccumulator}
     *
     * @param config the configuration
     * @return the new {@code IntegerAccumulator}
     * @throws IllegalArgumentException if {@code config} is {@code null}
     */
    IntegerAccumulator createIntegerAccumulator(IntegerAccumulator.Config config);

    /**
     * Creates a {@link IntegerGauge}
     *
     * @param config the configuration
     * @return the new {@code IntegerGauge}
     * @throws IllegalArgumentException if {@code config} is {@code null}
     */
    IntegerGauge createIntegerGauge(final IntegerGauge.Config config);

    /**
     * Creates a {@link LongAccumulator}
     *
     * @param config the configuration
     * @return the new {@code LongAccumulator}
     * @throws IllegalArgumentException if {@code config} is {@code null}
     */
    LongAccumulator createLongAccumulator(LongAccumulator.Config config);

    /**
     * Creates a {@link LongGauge}
     *
     * @param config the configuration
     * @return the new {@code LongGauge}
     * @throws IllegalArgumentException if {@code config} is {@code null}
     */
    LongGauge createLongGauge(final LongGauge.Config config);
}
