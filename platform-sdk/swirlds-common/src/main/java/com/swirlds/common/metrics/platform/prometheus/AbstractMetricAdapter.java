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

import com.swirlds.metrics.api.Metric;
import edu.umd.cs.findbugs.annotations.NonNull;
import io.prometheus.client.SimpleCollector;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public abstract class AbstractMetricAdapter implements MetricAdapter {
    private static final Logger log = LogManager.getLogger(AbstractMetricAdapter.class);
    protected final PrometheusEndpoint.AdapterType adapterType;
    private final @NonNull CommonMetricValues values;

    private final AtomicInteger referenceCount = new AtomicInteger();

    /**
     * @param metric the metric being adapted
     * @throws NullPointerException in case {@code adapterType} parameter is {@code null}
     * @throws NullPointerException in case {@code metric} parameter is {@code null}
     */
    protected AbstractMetricAdapter(
            @NonNull final PrometheusEndpoint.AdapterType adapterType, @NonNull final Metric metric) {
        this(adapterType, metric, false);
    }

    /**
     * @param metric   the metric being adapted
     * @param unitless if the {@code metric} being adapted represents a dimensionless metric. Prometheus will fail in
     *                 case of setting a unit to an invalid metric type.
     * @throws NullPointerException in case {@code adapterType} parameter is {@code null}
     * @throws NullPointerException in case {@code metric} parameter is {@code null}
     */
    protected AbstractMetricAdapter(
            @NonNull final PrometheusEndpoint.AdapterType adapterType,
            @NonNull final Metric metric,
            final boolean unitless) {
        this.adapterType = Objects.requireNonNull(adapterType, "adapterType must not be null");
        this.values = new CommonMetricValues(unitless, Objects.requireNonNull(metric, "metric must not be null"));
        this.values.checkAgainst(metric);
    }

    protected <C extends SimpleCollector<?>, T extends SimpleCollector.Builder<T, C>> T setCommonValues(
            SimpleCollector.Builder<T, C> collectorBuilder) {
        return values.fill(collectorBuilder);
    }

    @Override
    public int incAndGetReferenceCount() {
        return referenceCount.incrementAndGet();
    }

    @Override
    public int decAndGetReferenceCount() {
        return referenceCount.decrementAndGet();
    }

    /**
     * A holder for all metric common values. It can be created out of a {@code Metric} and naming rule conversions will
     * be performed according to {@link NameConverter}
     */
    private record CommonMetricValues(
            boolean unitless,
            @NonNull String subSystem,
            @NonNull String name,
            @NonNull String unit,
            @NonNull String help) {

        private CommonMetricValues(boolean unitless, @NonNull Metric metric) {
            this(
                    unitless,
                    fix(metric.getCategory()),
                    fix(metric.getName()),
                    fix(metric.getUnit()),
                    metric.getDescription());
        }

        <C extends SimpleCollector<?>, T extends SimpleCollector.Builder<T, C>> T fill(
                SimpleCollector.Builder<T, C> collectorBuilder) {
            final T builder = collectorBuilder.subsystem(subSystem).name(name).help(help);
            return unitless ? builder : builder.unit(unit);
        }

        /**
         * Verifies if there was any modification performed to the values that would result in a change of the metric
         * name in the underlying system.
         *
         * @param metric the values to check against
         */
        void checkAgainst(Metric metric) {
            if (!Objects.equals(this.subSystem, metric.getCategory())) {
                log.error(
                        EXCEPTION.getMarker(),
                        "category field changed for metric:{} from:{} to:{}",
                        metric,
                        metric.getCategory(),
                        this.subSystem);
            }
            if (!Objects.equals(this.name, metric.getName())) {
                log.error(
                        EXCEPTION.getMarker(),
                        "name field changed for metric:{} from:{} to:{}",
                        metric,
                        metric.getName(),
                        this.name);
            }
            if (!unitless && !Objects.equals(this.unit, metric.getUnit())) {
                log.error(
                        EXCEPTION.getMarker(),
                        "unit field changed for metric:{} from:{} to:{}",
                        metric,
                        metric.getUnit(),
                        this.unit);
            }
        }
    }
}
