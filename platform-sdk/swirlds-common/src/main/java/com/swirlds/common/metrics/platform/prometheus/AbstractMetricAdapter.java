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

package com.swirlds.common.metrics.platform.prometheus;

import static com.swirlds.common.utility.CommonUtils.throwArgNull;
import static java.lang.Boolean.TRUE;

import java.util.concurrent.atomic.AtomicInteger;

public abstract class AbstractMetricAdapter implements MetricAdapter {

    private static final double TRUE_VALUE = 1.0;
    private static final double FALSE_VALUE = 0.0;

    protected final PrometheusEndpoint.AdapterType adapterType;
    private final AtomicInteger referenceCount = new AtomicInteger();

    protected AbstractMetricAdapter(final PrometheusEndpoint.AdapterType adapterType) {
        this.adapterType = throwArgNull(adapterType, "adapterType");
    }

    @Override
    public int incAndGetReferenceCount() {
        return referenceCount.incrementAndGet();
    }

    @Override
    public int decAndGetReferenceCount() {
        return referenceCount.decrementAndGet();
    }

    protected static double convertBoolean(final Object value) {
        return TRUE.equals(value) ? TRUE_VALUE : FALSE_VALUE;
    }

    protected static double convertDouble(final Object value) {
        return ((Number) value).doubleValue();
    }


}
