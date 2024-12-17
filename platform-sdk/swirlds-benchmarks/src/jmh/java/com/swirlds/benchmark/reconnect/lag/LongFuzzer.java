/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.swirlds.benchmark.reconnect.lag;

import java.util.Random;

/**
 * A generator of random long values in a given percentage range around a given base value.
 *
 * @param value a base value
 * @param random a Random instance, or null for no fuzz
 * @param rangePercent a rangePercent, e.g. 0.15 for a -15%..+15% range around the base value.
 */
public record LongFuzzer(long value, Random random, double rangePercent) {
    /**
     * Returns the next long value in the range -rangePercent..+rangePercent around the base value.
     * @return the next long value
     */
    public long next() {
        if (random == null || rangePercent == .0) return value;

        // Generate a random fuzz percentage in the range -rangePercent..+rangePercent, e.g. -0.15..+0.15 for 15% range
        final double fuzzPercent = random.nextDouble(rangePercent * 2.) - rangePercent;
        return (long) ((double) value * (1. + fuzzPercent));
    }
}
