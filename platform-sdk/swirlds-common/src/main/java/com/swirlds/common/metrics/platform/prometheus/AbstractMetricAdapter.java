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

package com.swirlds.common.metrics.platform.prometheus;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class AbstractMetricAdapter implements MetricAdapter {

    protected final PrometheusEndpoint.AdapterType adapterType;
    private final AtomicInteger referenceCount = new AtomicInteger();

    /**
     * @throws NullPointerException in case {@code adapterType} parameter is {@code null}
     */
    protected AbstractMetricAdapter(final PrometheusEndpoint.AdapterType adapterType) {
        this.adapterType = Objects.requireNonNull(adapterType, "adapterType must not be null");
    }

    @Override
    public int incAndGetReferenceCount() {
        return referenceCount.incrementAndGet();
    }

    @Override
    public int decAndGetReferenceCount() {
        return referenceCount.decrementAndGet();
    }
}
