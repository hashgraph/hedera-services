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

public class WiringBenchmarkEventVerifier implements Function<WiringBenchmarkEvent, WiringBenchmarkEvent> {

    public WiringBenchmarkEventVerifier() {}

    @Override
    @NonNull
    public WiringBenchmarkEvent apply(@NonNull final WiringBenchmarkEvent event) {
        // Pretend like we did verification by sleeping for a few microseconds
        busySleep(2000);
        return event;
    }

    public static void busySleep(long nanos) {
        long elapsed;
        final long startTime = System.nanoTime();
        do {
            elapsed = System.nanoTime() - startTime;
        } while (elapsed < nanos);
    }
}
