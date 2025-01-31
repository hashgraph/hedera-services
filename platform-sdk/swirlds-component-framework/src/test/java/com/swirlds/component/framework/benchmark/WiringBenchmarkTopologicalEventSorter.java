/*
 * Copyright (C) 2023-2025 Hedera Hashgraph, LLC
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

package com.swirlds.component.framework.benchmark;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.function.Function;

public class WiringBenchmarkTopologicalEventSorter implements Function<WiringBenchmarkEvent, WiringBenchmarkEvent> {
    private static final int PRINT_FREQUENCY = 10_000_000;
    private long lastTimestamp;
    private long checkSum;

    public WiringBenchmarkTopologicalEventSorter() {
        this.checkSum = 0;
    }

    @NonNull
    @Override
    public WiringBenchmarkEvent apply(@NonNull final WiringBenchmarkEvent event) {
        final long number = event.number();
        checkSum += number + 1; // make 0 contribute to the sum
        if (number % PRINT_FREQUENCY == 0) {
            long curTimestamp = System.currentTimeMillis();
            if (number != 0) {
                System.out.format(
                        "Handled %d events, TPS: %d%n",
                        number, PRINT_FREQUENCY * 1000L / (curTimestamp - lastTimestamp));
            }
            lastTimestamp = curTimestamp;
        }
        return event;
    }

    public long getCheckSum() {
        return checkSum;
    }
}
