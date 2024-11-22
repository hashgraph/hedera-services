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

package com.swirlds.virtualmap.benchmark.reconnect;

import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.test.fixtures.RandomUtils;
import com.swirlds.virtualmap.test.fixtures.TestKey;
import com.swirlds.virtualmap.test.fixtures.TestValue;
import java.util.Random;
import java.util.function.BiConsumer;
import java.util.stream.LongStream;

/**
 * A utility class to help build random states.
 */
public class StateBuilder {

    /** Return {@code true} with the given probability. */
    private static boolean isRandomOutcome(final Random random, final double probability) {
        return random.nextDouble(1.) < probability;
    }

    /**
     * Build a random state and pass it to the provided teacher and learner populators.
     *
     * @param random a Random instance
     * @param size the number of nodes in the teacher state.
     *          The learner will have the same number of nodes or less.
     * @param learnerMissingProbability the probability of a key to be missing in the learner state
     * @param learnerDifferentProbability the probability of a node under a given key in the learner state
     *          to have a value that is different from the value under the same key in the teacher state.
     * @param teacherPopulator a BiConsumer that persists the teacher state (Map::put or similar)
     * @param learnerPopulator a BiConsumer that persists the learner state (Map::put or similar)
     */
    public static void buildState(
            final Random random,
            final long size,
            final double learnerMissingProbability,
            final double learnerDifferentProbability,
            final BiConsumer<Bytes, Bytes> teacherPopulator,
            final BiConsumer<Bytes, Bytes> learnerPopulator) {
        LongStream.range(1, size).forEach(i -> {
            final Bytes key = TestKey.longToKey(i);

            final Bytes teacherValue = TestValue.stringToValue(RandomUtils.randomString(random, random.nextInt(1, 64)));
            teacherPopulator.accept(key, teacherValue);

            if (isRandomOutcome(random, learnerMissingProbability)) {
                return;
            }

            final Bytes learnerValue;
            if (isRandomOutcome(random, learnerDifferentProbability)) {
                learnerValue = TestValue.stringToValue(RandomUtils.randomString(random, random.nextInt(1, 64)));
            } else {
                learnerValue = teacherValue;
            }
            learnerPopulator.accept(key, learnerValue);
        });
    }
}
