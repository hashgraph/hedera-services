package com.swirlds.common.threading.framework.internal;

import com.swirlds.common.threading.framework.config.QueueThreadMetricsConfiguration;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class Utils {
	/**
	 * Get the queue. If it doesn't exist then initialize with a default queue, and return that new queue. If the
	 * {@code metrics} field was set and any queue metrics are enabled, then {@code MeasuredBlockingQueue} will be
	 * initialized to be monitored with the enabled metrics.
	 *
	 * @return the queue that should be used
	 */
	public static <T> BlockingQueue<T> getOrBuildQueue(final AbstractQueueThreadConfiguration<?,T> config){
		BlockingQueue<T> queue = config.getQueue();
		if(queue == null) {
			// if no queue is set, build a default queue
			if (config.getCapacity() > 0) {
				queue = new LinkedBlockingQueue<>(config.getCapacity());
			} else {
				queue = new LinkedBlockingQueue<>();
			}
		}

		final QueueThreadMetricsConfiguration metricsConfig = config.getMetricsConfiguration();
		if (metricsConfig == null ||
				(!metricsConfig.isMinSizeMetricEnabled() && !metricsConfig.isMaxSizeMetricEnabled())) {
			return queue;
		}

		queue = new MeasuredBlockingQueue<>(
				queue,
				new MeasuredBlockingQueue.Config(
						metricsConfig.getMetrics(),
						metricsConfig.getCategory(),
						config.getThreadName())
						.withMaxSizeMetricEnabled(metricsConfig.isMaxSizeMetricEnabled())
						.withMinSizeMetricEnabled(metricsConfig.isMinSizeMetricEnabled())
		);

		// this is needed for a unit test, not a great solution
		config.setQueue(queue);

		return queue;
	}
}
