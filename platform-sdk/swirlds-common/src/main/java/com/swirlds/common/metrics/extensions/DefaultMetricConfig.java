package com.swirlds.common.metrics.extensions;

import com.swirlds.common.metrics.Metric;

import static com.swirlds.common.utility.CommonUtils.throwArgBlank;

public class DefaultMetricConfig {
	private final String category;
	private final String name;
	private final String description;

	public DefaultMetricConfig(
			final String category,
			final String name,
			final String description) {
		this.category = throwArgBlank(category, "category");
		this.name = throwArgBlank(name, "name");
		this.description = throwArgBlank(description, "description");
	}

	/**
	 * Getter of the {@link Metric#getCategory() Metric.category}
	 *
	 * @return the {@code category}
	 */
	public String getCategory() {
		return category;
	}

	/**
	 * Getter of the {@link Metric#getName() Metric.name}
	 *
	 * @return the {@code name}
	 */
	public String getName() {
		return name;
	}

	/**
	 * Getter of the {@link Metric#getDescription() Metric.description}
	 *
	 * @return the {@code description}
	 */
	public String getDescription() {
		return description;
	}
}
