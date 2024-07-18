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

package com.swirlds.platform.util;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Random;

/**
 * A utility for building random number generators.
 */
public class RandomBuilder {

    private final Random seedSource;

    /**
     * Constructor. Random seed is used.
     */
    public RandomBuilder() {
        seedSource = new Random();
    }

    /**
     * Constructor.
     *
     * @param seed the seed for the random number generator
     */
    public RandomBuilder(final long seed) {
        seedSource = new Random(seed);
    }

    /**
     * Build a non-cryptographic random number generator.
     *
     * @return a non-cryptographic random number generator
     */
    @NonNull
    public Random buildNonCryptographicRandom() {
        return new Random(seedSource.nextLong());
    }
}
