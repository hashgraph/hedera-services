package com.swirlds.common.threading.framework.internal;

import com.swirlds.common.metrics.extensions.BusyTime;
import com.swirlds.common.threading.framework.config.QueueThreadMetricsConfiguration;

/**
 * A class that holds the metrics for a queue thread
 */
public class QueueThreadMetrics {
	/** Tracks how busy a thread is */
	final BusyTime busyTime;

	/**
	 * Constructs a new {@link QueueThreadMetrics} instance
	 *
	 * @param configuration
	 * 		the configuration for the queue thread
	 */
	public QueueThreadMetrics(final AbstractQueueThreadConfiguration<?, ?> configuration) {
		final QueueThreadMetricsConfiguration metricsConfig = configuration.getMetricsConfiguration();
		this.busyTime = metricsConfig.isBusyTimeMetricEnabled() ? new BusyTime(metricsConfig.getTime()) : null;
		if (busyTime != null) {
			busyTime.addMetric(
					metricsConfig.getMetrics(),
					metricsConfig.getCategory(),
					buildBusyTimeMetricName(configuration.getThreadName()),
					"The busy time of the queue thread called " + configuration.getThreadName()
			);
		}
	}

	/**
	 * Builds the name of the busy time metric
	 *
	 * @param threadName
	 * 		the name of the thread
	 * @return the name of the busy time metric
	 */
	public static String buildBusyTimeMetricName(final String threadName){
		return "thread-busy-" + threadName;
	}

	/**
	 * Notifies the metric that work has started
	 */
	public void startingWork() {
		if (busyTime != null) {
			busyTime.startingWork();
		}
	}

	/**
	 * Notifies the metric that work has finished
	 */
	public void finishedWork() {
		if (busyTime != null) {
			busyTime.finishedWork();
		}
	}
}
