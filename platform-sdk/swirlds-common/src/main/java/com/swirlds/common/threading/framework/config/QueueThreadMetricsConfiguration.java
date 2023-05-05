package com.swirlds.common.threading.framework.config;

import com.swirlds.common.metrics.Metrics;

import static com.swirlds.base.ArgumentUtils.throwArgNull;

public class QueueThreadMetricsConfiguration {
	/** The metrics system that will hold metrics */
	private final Metrics metrics;
	/** The category to use for metrics */
	private String category = Metrics.INTERNAL_CATEGORY;
	/** If enabled, the max size metric will be applied to the queue.*/
	private boolean maxSizeMetricEnabled;
	/** If enabled, the min size metric will be applied to the queue.*/
	private boolean minSizeMetricEnabled;
	/** If true, this thread will add a busy time metric */
	private boolean busyTimeMetricEnabled;

	public QueueThreadMetricsConfiguration(final Metrics metrics) {
		this.metrics = throwArgNull(metrics, "metrics");
	}

	public QueueThreadMetricsConfiguration setCategory(final String category) {
		this.category = category;
		return this;
	}

	/**
	 * Enables the metric that tracks the maximum queue size
	 *
	 * @return this object
	 */
	public QueueThreadMetricsConfiguration enableMaxSizeMetric() {
		this.maxSizeMetricEnabled = true;
		return this;
	}

	/**
	 * Enables the metric that tracks the minimum queue size
	 *
	 * @return this object
	 */
	public QueueThreadMetricsConfiguration enableMinSizeMetric() {
		this.minSizeMetricEnabled = true;
		return this;
	}

	/**
	 * Enables the metric that tracks the busy time of the queue thread
	 * @return this object
	 */
	public QueueThreadMetricsConfiguration enableBusyTimeMetric() {
		this.busyTimeMetricEnabled = true;
		return this;
	}

	/**
	 * @return The metrics system that will hold metrics
	 */
	public Metrics getMetrics() {
		return metrics;
	}

	/**
	 * @return The category to use for metrics
	 */
	public String getCategory() {
		return category;
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
