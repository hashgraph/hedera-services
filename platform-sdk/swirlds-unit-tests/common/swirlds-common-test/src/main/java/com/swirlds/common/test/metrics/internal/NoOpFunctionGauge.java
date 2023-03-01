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

package com.swirlds.common.test.metrics.internal;

import com.swirlds.common.metrics.FunctionGauge;
import com.swirlds.common.metrics.MetricConfig;

/**
 * A no-op implementation of a function gauge.
 *
 * @param <T>
 * 		the type of the function gauge
 */
public class NoOpFunctionGauge<T> extends AbstractNoOpMetric implements FunctionGauge<T> {

    private final T value;

    public NoOpFunctionGauge(final MetricConfig<?> config, final T value) {
        super(config);
        this.value = value;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public T get() {
        return value;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DataType getDataType() {
        return DataType.INT;
    }
}
