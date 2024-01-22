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

import static com.swirlds.common.metrics.platform.prometheus.NameConverter.fix;
import static com.swirlds.logging.legacy.LogMarker.EXCEPTION;

import com.swirlds.common.metrics.Metric;
import edu.umd.cs.findbugs.annotations.NonNull;
import io.prometheus.client.SimpleCollector;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public abstract class AbstractMetricAdapter implements MetricAdapter {
    private static final Logger log = LogManager.getLogger(AbstractMetricAdapter.class);
    protected final PrometheusEndpoint.AdapterType adapterType;
    private final String subSystem;
    private final String name;
    private final String unit;
    private final String help;


    private final AtomicInteger referenceCount = new AtomicInteger();

    /**
     * @throws NullPointerException in case {@code adapterType} parameter is {@code null}
     */
    protected AbstractMetricAdapter(@NonNull final PrometheusEndpoint.AdapterType adapterType,@NonNull final Metric metric) {
        this.adapterType = Objects.requireNonNull(adapterType, "adapterType must not be null");
        this.subSystem = fix(metric.getCategory());
        this.name = fix(metric.getName());
        this.unit = fix(metric.getUnit());
        this.help = metric.getDescription();

        if (!Objects.equals(subSystem, metric.getCategory())) {
            log.error(EXCEPTION.getMarker(), "category field changed for metric:{} from:{} to:{}", metric, metric.getCategory(),
                    subSystem);
        }
        if (!Objects.equals(name,metric.getName())) {
            log.error(EXCEPTION.getMarker(), "name field changed for metric:{} from:{} to:{}", metric, metric.getName(),
                    name);
        }
        if (!Objects.equals(unit,metric.getUnit())) {
            log.error(EXCEPTION.getMarker(), "unit field changed for metric:{} from:{} to:{}",metric, metric.getUnit(),
                    unit);
        }
    }

    protected <C extends SimpleCollector<?>, T extends SimpleCollector.Builder<T, C>> T fill(SimpleCollector.Builder<T, C> collectorBuilder) {
        return collectorBuilder.subsystem(subSystem)
                .name(fix(name))
                .help(help)
                .unit(fix(unit));

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
