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

package com.swirlds.common.threading.framework.internal;

import com.swirlds.common.metrics.extensions.FractionalTimer;
import com.swirlds.common.metrics.extensions.StandardFractionalTimer;
import com.swirlds.common.threading.framework.config.QueueThreadMetricsConfiguration;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * A class that holds the metrics for a queue thread
 */
public class QueueThreadMetrics {
    /** Tracks how busy a thread is */
    private final FractionalTimer busyTime;

    /**
     * Constructs a new {@link QueueThreadMetrics} instance
     *
     * @param configuration
     * 		the configuration for the queue thread
     */
    public QueueThreadMetrics(@NonNull final AbstractQueueThreadConfiguration<?, ?> configuration) {
        final QueueThreadMetricsConfiguration metricsConfig = configuration.getMetricsConfiguration();
        if (metricsConfig == null || !metricsConfig.isBusyTimeMetricEnabled()) {
            this.busyTime = null;
            return;
        }
        this.busyTime = new StandardFractionalTimer(metricsConfig.getTime());
        busyTime.registerMetric(
                metricsConfig.getMetrics(),
                metricsConfig.getCategory(),
                buildBusyTimeMetricName(configuration.getThreadName()),
                "The busy time of the queue thread called " + configuration.getThreadName());
    }

    /**
     * Builds the name of the busy time metric
     *
     * @param threadName
     * 		the name of the thread
     * @return the name of the busy time metric
     */
    public static String buildBusyTimeMetricName(@NonNull final String threadName) {
        return "thread-busy-" + threadName;
    }

    /**
     * Notifies the metric that work has started
     */
    public void startingWork() {
        if (busyTime != null) {
            busyTime.activate();
        }
    }

    /**
     * Notifies the metric that work has finished
     */
    public void finishedWork() {
        if (busyTime != null) {
            busyTime.deactivate();
        }
    }
}
