/*
 * Copyright (C) 2018-2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.test;

import java.util.Random;

/**
 * A {@link Random} that can be reset to its initial state.
 */
public class ResettableRandom extends Random {

    private final long initialSeed;

    /**
     * Create a source of randomness with a random initial seed.
     */
    public ResettableRandom() {
        this(new Random().nextLong());
    }

    /**
     * Create a source of randomness with a particular initial seed.
     *
     * @param seed
     * 		the initial seed
     */
    public ResettableRandom(final long seed) {
        super(seed);
        initialSeed = seed;
    }

    /**
     * Reset this object back to its initial state.
     */
    public void reset() {
        setSeed(initialSeed);
    }

    public long getInitialSeed() {
        return initialSeed;
    }
}
