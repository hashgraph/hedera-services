package com.swirlds.common.metrics.extensions;

public class ExtensionUtils {
	/**
	 * An implementation of a {@link com.swirlds.common.metrics.IntegerAccumulator} that does not change the value
	 */
	public static int noChangeAccumulator(final int currentValue, final int ignored) {
		return currentValue;
	}
}
