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
    private final @NonNull ConvertedMetricValues values;

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
        this.values = new ConvertedMetricValues(unitless, metric);
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
     * A holder for common metric values supporting automatic naming rules conversions. Instances of this class are
     * created based on a {@code Metric}, and naming rules conversions will be applied to the category, name, and unit
     * fields according to {@link NameConverter}.
     *
     * <p>
     * The class facilitates the creation of Prometheus-compatible metric values by storing the fixed and converted
     * category, name, unit, and help fields.
     * </p>
     */
    private static class ConvertedMetricValues {
        private final boolean unitless;

        @NonNull
        private final String subSystem;

        @NonNull
        private final String name;

        @NonNull
        private final String unit;

        @NonNull
        private final String help;

        private ConvertedMetricValues(boolean unitless, final @NonNull Metric metric) {
            Objects.requireNonNull(metric, "metric must not be null");
            this.unitless = unitless;
            this.subSystem = fix(metric.getCategory());
            this.name = fix(metric.getName());
            this.unit = fix(metric.getUnit());
            this.help = metric.getDescription();
            verifyMetricNamingComponents(metric);
        }

        <C extends SimpleCollector<?>, T extends SimpleCollector.Builder<T, C>> T fill(
                SimpleCollector.Builder<T, C> collectorBuilder) {
            final T builder = collectorBuilder.subsystem(subSystem).name(name).help(help);
            return unitless ? builder : builder.unit(unit);
        }

        /**
         * identifies changes in the metrics name components (category, name, and unit). If a change is detected, error
         * log statements with the purpose of failing JRS are generated to inform developers that adjustments to the
         * metric name may be required.
         * </p>
         * It is not throwing exceptions in order to minimize the possibility for runtime errors produced by
         * non-critical misconfigurations
         *
         * @param metric the metric to check against
         */
        private void verifyMetricNamingComponents(@NonNull final Metric metric) {
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
