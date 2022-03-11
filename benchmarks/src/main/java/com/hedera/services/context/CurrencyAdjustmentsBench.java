package com.hedera.services.context;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

@State(Scope.Benchmark)
@Fork(1)
@Warmup(iterations = 1, time = 5)
@Measurement(iterations = 5, time = 10)
public class CurrencyAdjustmentsBench {
	@Benchmark
	public void getTrackedCurrencyAdjustments(Blackhole blackhole) {
		final var account = 2L;
		SideEffectsTracker tracker = new SideEffectsTracker();
		for (int i = 0; i < 20; i++) {
			tracker.trackHbarChange(account, 10L);
		}
		final var result = tracker.getNetTrackedHbarChanges();
		blackhole.consume(result);
	}
}
