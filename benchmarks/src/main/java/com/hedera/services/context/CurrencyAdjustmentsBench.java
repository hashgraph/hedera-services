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
		var account = 1000;
		var amount = 2000;

		SideEffectsTracker tracker = new SideEffectsTracker();
		for (int i = 0; i < 10; i++) {
			tracker.trackHbarChange(account, amount + 10);
			tracker.trackHbarChange(account, amount - 10);
			account++;
		}
		for (int i = 0; i < 5; i++) {
			tracker.trackHbarChange(account, amount);
			tracker.trackHbarChange(account, -1 * amount);
			account++;
		}
		final var result = tracker.getNetTrackedHbarChanges();
		blackhole.consume(result);
	}

	/*
	BEFORE THE CHANGE --- FOR REF, WILL BE DELETED BEFORE MERGING
		@Benchmark
	public void getTrackedCurrencyAdjustments(Blackhole blackhole) {
		var acc = 1000;
		var amount = 2000;
		SideEffectsTracker tracker = new SideEffectsTracker();
		for (int i = 0; i < 10; i++) {
			var account = AccountID.newBuilder().setAccountNum(acc).build();
			tracker.trackHbarChange(account, amount + 10);
			tracker.trackHbarChange(account, amount - 10);
			acc++;
		}
		for (int i = 0; i < 5; i++) {
			var account = AccountID.newBuilder().setAccountNum(acc).build();
			tracker.trackHbarChange(account, amount);
			tracker.trackHbarChange(account, -1 * amount);
			acc++;
		}
		final var netChanges = tracker.getNetTrackedHbarChanges();
		final var result = CurrencyAdjustments.fromGrpc(netChanges);
		blackhole.consume(result);
	}
	 */
}
