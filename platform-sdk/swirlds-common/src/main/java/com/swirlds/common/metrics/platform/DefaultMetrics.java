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

package com.swirlds.common.metrics.platform;

import static com.swirlds.common.metrics.platform.MetricsEvent.Type.ADDED;
import static com.swirlds.common.metrics.platform.MetricsEvent.Type.REMOVED;
import static com.swirlds.common.utility.CommonUtils.throwArgNull;

import com.swirlds.common.metrics.Metric;
import com.swirlds.common.metrics.MetricConfig;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.metrics.MetricsFactory;
import com.swirlds.common.metrics.config.MetricsConfig;
import com.swirlds.common.system.NodeId;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.NavigableMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Default implementation of the {@link Metrics} interface.
 */
public class DefaultMetrics implements Metrics {

    /**
     * Threshold for the number of similar {@link Exception} that are thrown by regular metrics-tasks
     */
    public static final int EXCEPTION_RATE_THRESHOLD = 10;

    private static final String CATEGORY = "category";
    private static final String NAME = "name";

    // A reference to the NodeId of the current node
    private final NodeId selfId;

    // The MetricKeyRegistry ensures that no two conflicting metrics with the same key exist
    private final MetricKeyRegistry metricKeyRegistry;

    // A map of metric-keys to metrics
    private final NavigableMap<String, Metric> metricMap = new ConcurrentSkipListMap<>();

    // A read-only view of all registered metrics
    private final Collection<Metric> metricsView = Collections.unmodifiableCollection(metricMap.values());

    // A map of all global metrics in the system (only used if this instance maintains platform metrics
    private final Map<String, String> globalMetricKeys = new ConcurrentHashMap<>();

    // Factory that creates specific implementation of Metric
    private final MetricsFactory factory;

    // Helper-class that implements the Observer-pattern for MetricsEvents
    private final MetricsEventBus<MetricsEvent> eventBus;

    // Helper class that maintains a list of all metrics, which need to be updated in regular intervals
    private final MetricsUpdateService updateService;

    /**
     * Constructor of {@code DefaultMetrics}
     *
     * @param selfId
     * 		the {@link NodeId} of the platform, {@code null} if these are the global metrics
     * @param metricKeyRegistry
     * 		the {@link MetricKeyRegistry} that ensures no conflicting metrics are registered
     * @param executor
     * 		the {@link ScheduledExecutorService} that will be used by this {@code DefaultMetrics}
     * @param factory
     * 		the {@link MetricsFactory} that will be used to create new instances of {@link Metric}
     * @param metricsConfig
     *      the {@link MetricsConfig} for metrics configuration
     */
    public DefaultMetrics(
            final NodeId selfId,
            final MetricKeyRegistry metricKeyRegistry,
            final ScheduledExecutorService executor,
            final MetricsFactory factory,
            final MetricsConfig metricsConfig) {
        this.selfId = selfId;
        this.metricKeyRegistry = throwArgNull(metricKeyRegistry, "metricsKeyRegistry");
        this.factory = throwArgNull(factory, "factory");
        throwArgNull(executor, "executor");
        this.eventBus = new MetricsEventBus<>(executor);
        throwArgNull(metricsConfig, "metricsConfig");
        this.updateService = metricsConfig.metricsUpdatePeriodMillis() <= 0
                ? null
                : new MetricsUpdateService(executor, metricsConfig.metricsUpdatePeriodMillis(), TimeUnit.MILLISECONDS);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public NodeId getNodeId() {
        return selfId;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Metric getMetric(final String category, final String name) {
        throwArgNull(category, CATEGORY);
        throwArgNull(name, NAME);
        return metricMap.get(calculateMetricKey(category, name));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Collection<Metric> findMetricsByCategory(final String category) {
        throwArgNull(category, CATEGORY);
        final String start = category + ".";
        // The character '/' is the successor of '.' in Unicode. We use it to define the first metric-key,
        // which is not part of the result set anymore.
        final String end = category + "/";
        return Collections.unmodifiableCollection(metricMap.subMap(start, end).values());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Collection<Metric> getAll() {
        return metricsView;
    }

    /**
     * Adds a subscriber that will be notified about new and removed {@link Metric}s.
     * <p>
     * A new subscriber will immediately receive ADD-events for all metrics, that are already registered.
     * <p>
     * If the list of metrics is modified while a new subscriber is added, it may happen, that the new subscriber
     * gets two ADD-events for the same {@code Metric} or a REMOVE-event for a {@code Metric} that was not added before.
     *
     * @param subscriber
     * 		the new {@code subscriber}
     * @return a {@link Runnable} that, when called, unsubscribes the subscriber
     */
    public Runnable subscribe(final Consumer<? super MetricsEvent> subscriber) {
        final Supplier<Stream<MetricsEvent>> previousEventsSupplier =
                () -> metricMap.values().stream().map(metric -> new MetricsEvent(ADDED, selfId, metric));
        return eventBus.subscribe(subscriber, previousEventsSupplier);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T extends Metric> T getOrCreate(final MetricConfig<T, ?> config) {
        throwArgNull(config, "config");

        // first we check the happy path, if the metric is already registered
        final String key = calculateMetricKey(config);
        Metric metric = metricMap.get(key);
        if (metric == null) {
            // no metric registered, therefore we will try to register it now
            // before anything else, we try to reserve the category and name
            if (!metricKeyRegistry.register(selfId, key, config.getResultClass())) {
                throw new IllegalStateException(String.format(
                        "A different metric with the category '%s' and name '%s' was already registered",
                        config.getCategory(), config.getName()));
            }

            // it is not registered, we prepare a new one and try to set it
            final T newMetric = factory.createMetric(config);
            metric = metricMap.putIfAbsent(key, newMetric);
            // Map.putIfAbsent() returns the old value, i.e. it is null, if there was none
            // (metric may be non-null at this point, if another metric was added concurrently)
            if (metric == null) {
                // metric was definitely just added, we send out a notification
                final MetricsEvent event = new MetricsEvent(ADDED, selfId, newMetric);
                eventBus.submit(event);
                return newMetric;
            }
        }

        final Class<T> clazz = config.getResultClass();
        if (clazz.isInstance(metric)) {
            return clazz.cast(metric);
        }
        throw new IllegalStateException(
                "A metric with this category and name exists, but it has a different type: " + metric);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void remove(final String category, final String name) {
        throwArgNull(category, CATEGORY);
        throwArgNull(name, NAME);
        final String metricKey = calculateMetricKey(category, name);
        throwIfGlobal(metricKey);
        final Metric metric = metricMap.remove(metricKey);
        if (metric != null) {
            metricKeyRegistry.unregister(selfId, metricKey);
            final MetricsEvent event = new MetricsEvent(REMOVED, selfId, metric);
            eventBus.submit(event);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void remove(final Metric metric) {
        throwArgNull(metric, "metric");
        final String metricKey = calculateMetricKey(metric);
        throwIfGlobal(metricKey);
        final boolean removed = metricMap.remove(metricKey, metric);
        if (removed) {
            metricKeyRegistry.unregister(selfId, metricKey);
            final MetricsEvent event = new MetricsEvent(REMOVED, selfId, metric);
            eventBus.submit(event);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void remove(final MetricConfig<?, ?> config) {
        throwArgNull(config, "config");
        final String metricKey = calculateMetricKey(config);
        throwIfGlobal(metricKey);
        metricMap.computeIfPresent(metricKey, (key, oldValue) -> {
            if (!config.getResultClass().isInstance(oldValue)) {
                return oldValue;
            }
            metricKeyRegistry.unregister(selfId, key);
            final MetricsEvent event = new MetricsEvent(REMOVED, selfId, oldValue);
            eventBus.submit(event);
            return null;
        });
    }

    private void throwIfGlobal(final String metricKey) {
        if (globalMetricKeys.containsKey(metricKey)) {
            throw new IllegalArgumentException(String.format(
                    "Not possible to remove the global Metric (%s) from a non-global Metrics", metricKey));
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addUpdater(final Runnable updater) {
        throwArgNull(updater, "updater");
        if (updateService != null) {
            updateService.addUpdater(updater);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void removeUpdater(final Runnable updater) {
        throwArgNull(updater, "updater");
        if (updateService != null) {
            updateService.removeUpdater(updater);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void start() {
        if (updateService != null) {
            updateService.start();
        }
    }

    /**
     * Shuts down the service
     *
     * @return {@code true} if the shutdown finished on time, {@code false} if the call ran into a timeout
     * @throws InterruptedException
     * 		if the current thread was interrupted while waiting
     */
    public boolean shutdown() throws InterruptedException {
        metricMap.entrySet().stream()
                .filter(entry -> !globalMetricKeys.containsKey(entry.getKey()))
                .map(entry -> new MetricsEvent(REMOVED, selfId, entry.getValue()))
                .forEach(eventBus::submit);
        return updateService == null || updateService.shutdown();
    }

    /**
     * Calculates a unique key for a given {@code category} and {@code name}
     * <p>
     * The generated key is compatible with keys generated by {@link #calculateMetricKey(Metric)} and
     * {@link #calculateMetricKey(MetricConfig)}.
     *
     * @param category
     * 		the {@code category} used in the key
     * @param name
     * 		the {@code name} used in the key
     * @return the calculated key
     */
    public static String calculateMetricKey(final String category, final String name) {
        return category + "." + name;
    }

    /**
     * Calculates a unique key for a given {@link Metric}
     * <p>
     * The generated key is compatible with keys generated by {@link #calculateMetricKey(String, String)} and
     * {@link #calculateMetricKey(MetricConfig)}.
     *
     * @param metric
     * 		the {@code Metric} for which the key should be calculated
     * @return the calculated key
     */
    public static String calculateMetricKey(final Metric metric) {
        return calculateMetricKey(metric.getCategory(), metric.getName());
    }

    /**
     * Calculates a unique key for a given {@link MetricConfig}
     * <p>
     * The generated key is compatible with keys generated by {@link #calculateMetricKey(String, String)} and
     * {@link #calculateMetricKey(Metric)}.
     *
     * @param config
     * 		the {@code MetricConfig} for which the key should be calculated
     * @return the calculated key
     */
    public static String calculateMetricKey(final MetricConfig<?, ?> config) {
        return calculateMetricKey(config.getCategory(), config.getName());
    }

    /**
     * Handles new and removed global metrics.
     *
     * @param event
     * 		The {@link MetricsEvent} with information about the change
     */
    public void handleGlobalMetrics(final MetricsEvent event) {
        final Metric metric = event.metric();
        final String metricKey = calculateMetricKey(metric);
        switch (event.type()) {
            case ADDED -> {
                globalMetricKeys.put(metricKey, metricKey);
                metricMap.put(metricKey, metric);
            }
            case REMOVED -> {
                metricMap.remove(metricKey);
                globalMetricKeys.remove(metricKey);
            }
        }
    }
}
