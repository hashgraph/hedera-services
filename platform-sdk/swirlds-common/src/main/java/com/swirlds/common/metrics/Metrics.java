/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

import static com.swirlds.common.metrics.Metric.ValueType.VALUE;

import com.swirlds.base.state.Startable;
import com.swirlds.common.system.NodeId;
import java.util.Collection;

/**
 * Entry-point to the metrics-system.
 * <p>
 * The interface {@code Metrics} provides functionality to add and delete metrics. There are also several
 * methods to request registered metrics.
 * <p>
 * In addition, one can register an updater which will be called once per second to update metrics periodically.
 */
@SuppressWarnings("unused")
public interface Metrics extends Startable {

    String INTERNAL_CATEGORY = "internal";
    String PLATFORM_CATEGORY = "platform";
    String INFO_CATEGORY = "platform.info";

    /**
     * Returns the {@link NodeId} which metrics this {@code Metrics} manages. If this {@code Metrics}
     * manages the global metrics, this method returns {@code null}.
     *
     * @return
     * 		The {@code NodeId} or {@code null}
     */
    NodeId getNodeId();

    /**
     * Checks if this {@code Metrics} manages global metrics.
     *
     * @return
     * 		{@code true} if this {@code Metrics} manages global metrics, {@code false} otherwise
     */
    default boolean isGlobalMetrics() {
        return getNodeId() == null;
    }

    /**
     * Checks if this {@code Metrics} manages platform metrics.
     *
     * @return
     * 		{@code true} if this {@code Metrics} manages platform metrics, {@code false} otherwise
     */
    default boolean isPlatformMetrics() {
        return getNodeId() != null;
    }

    /**
     * Get a single {@link Metric} identified by its category and name
     *
     * @param category
     * 		the category of the wanted category
     * @param name
     * 		the name of the wanted category
     * @return the {@code Metric} if one is found, {@code null} otherwise
     * @throws IllegalArgumentException
     * 		if one of the parameters is {@code null}
     */
    Metric getMetric(final String category, final String name);

    /**
     * Get all metrics with the given category.
     * <p>
     * Categories are structured hierarchically, e.g. if there are two categories "crypto.signature" and
     * "crypto.digest" one will receive metrics from both categories if searching for "crypto"
     * <p>
     * The returned {@link Collection} is backed by the original data-structure, i.e. future changes are
     * automatically reflected. The {@code Collection} is not modifiable.
     * <p>
     * The returned values are ordered by category and name.
     *
     * @param category
     * 		the category of the wanted metrics
     * @return all metrics that have the category or a sub-category
     * @throws IllegalArgumentException
     * 		if {@code category} is {@code null}
     */
    Collection<Metric> findMetricsByCategory(final String category);

    /**
     * Get a list of all metrics that are currently registered.
     * <p>
     * The returned {@link Collection} is backed by the original data-structure, i.e. future changes are
     * automatically reflected. The {@code Collection} is not modifiable.
     * <p>
     * The returned values are ordered by category and name.
     *
     * @return all registered metrics
     */
    Collection<Metric> getAll();

    /**
     * Get the value of a metric directly. Calling this method is equivalent to calling
     * {@code getMetric(category, name).get(Metric.ValueType.VALUE)}
     *
     * @param category
     * 		the category of the wanted category
     * @param name
     * 		the name of the wanted category
     * @return the {@code value} of the {@link Metric}, if one is found, {@code null} otherwise
     * @throws IllegalArgumentException
     * 		if one of the parameters is {@code null}
     */
    default Object getValue(final String category, final String name) {
        final Metric metric = getMetric(category, name);
        return metric != null ? metric.get(VALUE) : null;
    }

    /**
     * Resets all metrics
     */
    default void resetAll() {
        for (final Metric metric : getAll()) {
            metric.reset();
        }
    }

    /**
     * Checks if a {@link Metric} with the category and name as specified in the config-object exists and
     * returns it. If there is no such {@code Metric}, a new one is created, registered, and returned.
     *
     * @param config
     * 		the configuration of the {@code Metric}
     * @param <T>
     * 		class of the {@code Metric} that will be returned
     * @return the registered {@code Metric} (either existing or newly generated)
     * @throws IllegalArgumentException
     * 		if {@code config} is {@code null}
     * @throws IllegalStateException
     * 		if a {@code Metric} with the same category and name exists, but has a different type
     */
    <T extends Metric> T getOrCreate(final MetricConfig<T, ?> config);

    /**
     * Remove the {@link Metric} with the given category and name
     *
     * @param category
     * 		the category of the {@code Metric}, that should be removed
     * @param name
     * 		the name of the {@code Metric}, that should be removed
     * @throws IllegalArgumentException
     * 		if one of the parameters is {@code null}
     */
    void remove(final String category, final String name);

    /**
     * Remove the {@link Metric}.
     * <p>
     * Please note that two metrics are equal, if their class, category, and name match.
     *
     * @param metric
     * 		the {@code Metric}, that should be removed
     * @throws IllegalArgumentException
     * 		if ({code metric} is {@code null}
     */
    void remove(final Metric metric);

    /**
     * Remove the {@link Metric} with the given configuration.
     * <p>
     * Please note that two metrics are equal, if their class, category, and name match.
     *
     * @param config
     * 		the {@link MetricConfig} of the {@code Metric}, that should be removed
     * @throws IllegalArgumentException
     * 		if {@code config} is {@code null}
     */
    void remove(final MetricConfig<?, ?> config);

    /**
     * Add an updater that will be called once per second. An updater should only be used to update metrics regularly.
     *
     * @param updater
     * 		the updater
     * @throws IllegalArgumentException
     * 		if {@code updater} is {@code null}
     */
    void addUpdater(final Runnable updater);

    /**
     * Remove an updater that was previously added.
     *
     * @param updater
     * 		the updater
     * @throws IllegalArgumentException
     * 		if {@code updater} is {@code null}
     */
    void removeUpdater(final Runnable updater);
}
