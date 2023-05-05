package com.swirlds.common.threading.framework.internal;

import com.swirlds.common.metrics.extensions.BusyTime;
import com.swirlds.common.threading.framework.config.QueueThreadMetricsConfiguration;

public class QueueThreadMetrics {
	final QueueThreadMetricsConfiguration metricsConfig;
	final BusyTime busyTime;

	public QueueThreadMetrics(final AbstractQueueThreadConfiguration<?, ?> configuration) {
		final QueueThreadMetricsConfiguration metricsConfig = configuration.getMetricsConfiguration();
		this.metricsConfig = metricsConfig;
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
