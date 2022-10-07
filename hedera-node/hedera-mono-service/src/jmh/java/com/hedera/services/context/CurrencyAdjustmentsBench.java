/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
 *
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
 */
package com.hedera.services.context;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

@State(Scope.Benchmark)
@Fork(1)
@Warmup(iterations = 1, time = 5)
@Measurement(iterations = 5, time = 10)
public class CurrencyAdjustmentsBench {
    @State(Scope.Benchmark)
    public static class MyState {
        @Setup(Level.Invocation)
        public void setUp() {
            tracker.reset();
        }

        public SideEffectsTracker tracker = new SideEffectsTracker();
    }

    @Benchmark
    public void getTrackedCurrencyAdjustments(Blackhole blackhole, MyState state) {
        var account = 1000;
        var amount = 2000;
        for (int i = 0; i < 10; i++) {
            state.tracker.trackHbarChange(account, amount + 10);
            state.tracker.trackHbarChange(account, amount - 10);
            account++;
        }
        for (int i = 0; i < 5; i++) {
            state.tracker.trackHbarChange(account, amount);
            state.tracker.trackHbarChange(account, -1 * amount);
            account++;
        }
        final var result = state.tracker.getNetTrackedHbarChanges();
        blackhole.consume(result);
    }

    /*
    RESULT :
    1. Using TransferList 102379.684 ops/s
    2. using long[] array in SideEffectsTracker 505066 ops.s
    */
}
