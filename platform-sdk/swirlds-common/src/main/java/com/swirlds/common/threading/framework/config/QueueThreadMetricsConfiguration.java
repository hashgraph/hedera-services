/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.threading.framework.config;

import static com.swirlds.base.ArgumentUtils.throwArgNull;

import com.swirlds.base.time.Time;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.time.OSTime;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Configuration for the metrics that will be applied to a queue thread
 */
public class QueueThreadMetricsConfiguration {
    /** The metrics system that will hold metrics */
    private final Metrics metrics;
    /** The category to use for metrics */
    private String category = Metrics.INTERNAL_CATEGORY;
    /** The time object to use for metrics */
    private Time time = OSTime.getInstance();
    /** If enabled, the max size metric will be applied to the queue.*/
    private boolean maxSizeMetricEnabled;
    /** If enabled, the min size metric will be applied to the queue.*/
    private boolean minSizeMetricEnabled;
    /** If true, this thread will add a busy time metric */
    private boolean busyTimeMetricEnabled;

    /**
     * Create a new configuration object
     *
     * @param metrics
     * 		The metrics system that will hold metrics
     */
    public QueueThreadMetricsConfiguration(@NonNull final Metrics metrics) {
        this.metrics = throwArgNull(metrics, "metrics");
    }

    /**
     * Set the category to use for metrics
     *
     * @param category
     * 		The category to use for metrics
     * @return this object
     */
    public @NonNull QueueThreadMetricsConfiguration setCategory(@NonNull final String category) {
        this.category = category;
        return this;
    }

    /**
     * Set the time object to use for metrics
     *
     * @param time
     * 		The time object to use for metrics
     * @return this object
     */
    public @NonNull QueueThreadMetricsConfiguration setTime(@NonNull final Time time) {
        this.time = time;
        return this;
    }

    /**
     * Enables the metric that tracks the maximum queue size
     *
     * @return this object
     */
    public @NonNull QueueThreadMetricsConfiguration enableMaxSizeMetric() {
        this.maxSizeMetricEnabled = true;
        return this;
    }

    /**
     * Enables the metric that tracks the minimum queue size
     *
     * @return this object
     */
    public @NonNull QueueThreadMetricsConfiguration enableMinSizeMetric() {
        this.minSizeMetricEnabled = true;
        return this;
    }

    /**
     * Enables the metric that tracks the busy time of the queue thread
     * @return this object
     */
    public @NonNull QueueThreadMetricsConfiguration enableBusyTimeMetric() {
        this.busyTimeMetricEnabled = true;
        return this;
    }

    /**
     * @return The metrics system that will hold metrics
     */
    public @NonNull Metrics getMetrics() {
        return metrics;
    }

    /**
     * @return The category to use for metrics
     */
    public @NonNull String getCategory() {
        return category;
    }

    /**
     * @return The time object to use for metrics
     */
    public @NonNull Time getTime() {
        return time;
    }

    /**
     * @return is the max size metric enabled
     */
    public boolean isMaxSizeMetricEnabled() {
        return maxSizeMetricEnabled;
    }

    /**
     * @return is the min size metric enabled
     */
    public boolean isMinSizeMetricEnabled() {
        return minSizeMetricEnabled;
    }

    /**
     * @return is the busy time metric enabled
     */
    public boolean isBusyTimeMetricEnabled() {
        return busyTimeMetricEnabled;
    }
}
