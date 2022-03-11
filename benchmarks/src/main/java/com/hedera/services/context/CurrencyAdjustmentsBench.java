package com.hedera.services.context;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.util.Random;

@State(Scope.Benchmark)
@Fork(1)
@Warmup(iterations = 1, time = 5)
@Measurement(iterations = 5, time = 10)
public class CurrencyAdjustmentsBench {
	private static final long SEED = 1_234_321L;
	private static final Random r = new Random(SEED);

	@Benchmark
	public void getTrackedCurrencyAdjustments(Blackhole blackhole) {
		final var account = r.nextInt(1000);
		SideEffectsTracker tracker = new SideEffectsTracker();
		for (int i = 0; i < 20; i++) {
			tracker.trackHbarChange(account, r.nextInt(1000));
		}
		final var result = tracker.getNetTrackedHbarChanges();
		blackhole.consume(result);
	}
}
