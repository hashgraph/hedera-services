package com.hedera.services.context;

/*-
 * ‌
 * Hedera Services JMH benchmarks
 * ​
 * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 */

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
	RESULT : 
	1. Using TransferList 102379.684 ops/s
	2. using long[] array in SideEffectsTracker 505066 ops.s
	*/
}
