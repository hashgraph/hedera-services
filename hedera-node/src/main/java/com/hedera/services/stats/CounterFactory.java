package com.hedera.services.stats;

import com.swirlds.common.StatEntry;

import java.util.function.Supplier;

public interface CounterFactory {
	default StatEntry from(String name, String desc, Supplier<Object> sample) {
		return new StatEntry( "app", name, desc, "%d", null, null, null, sample);
	}
}
