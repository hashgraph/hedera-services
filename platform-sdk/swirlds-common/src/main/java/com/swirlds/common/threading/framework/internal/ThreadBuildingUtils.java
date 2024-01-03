/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

import com.swirlds.common.threading.framework.config.QueueThreadMetricsConfiguration;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Static utility methods for the threading framework
 */
final class ThreadBuildingUtils {
    private ThreadBuildingUtils() {}

    /**
     * Get the queue. If it doesn't exist then initialize with a default queue, and return that new queue. If the
     * {@code metrics} field was set and any queue metrics are enabled, then {@code MeasuredBlockingQueue} will be
     * initialized to be monitored with the enabled metrics.
     *
     * @return the queue that should be used
     */
    static <T> BlockingQueue<T> getOrBuildQueue(@NonNull final AbstractQueueThreadConfiguration<?, T> config) {
        BlockingQueue<T> queue = config.getQueue();
        if (queue == null) {
            // if no queue is set, build a default queue
            if (config.getCapacity() > 0) {
                queue = new LinkedBlockingQueue<>(config.getCapacity());
            } else {
                queue = new LinkedBlockingQueue<>();
            }
        }

        final QueueThreadMetricsConfiguration metricsConfig = config.getMetricsConfiguration();
        if (metricsConfig != null
                && (metricsConfig.isMinSizeMetricEnabled() || metricsConfig.isMaxSizeMetricEnabled())) {
            queue = new MeasuredBlockingQueue<>(
                    queue,
                    new MeasuredBlockingQueue.Config(
                                    metricsConfig.getMetrics(), metricsConfig.getCategory(), config.getThreadName())
                            .withMaxSizeMetricEnabled(metricsConfig.isMaxSizeMetricEnabled())
                            .withMinSizeMetricEnabled(metricsConfig.isMinSizeMetricEnabled()));
        }

        // this is needed for unit tests, not a great solution
        config.setQueue(queue);
        return queue;
    }
}
