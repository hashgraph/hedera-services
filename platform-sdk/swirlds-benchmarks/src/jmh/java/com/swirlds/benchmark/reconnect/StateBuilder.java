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

package com.swirlds.benchmark.reconnect;

import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.LongStream;

/**
 * A utility class to help build random states.
 */
public record StateBuilder<K, V>(
        /** Build a key for index 1..size. */
        Function<Long, K> keyBuilder,
        /** Build a value for key index 1..size. */
        Function<Long, V> valueBuilder) {

    /** Return {@code true} with the given probability. */
    private static boolean isRandomOutcome(final Random random, final double probability) {
        return random.nextDouble(1.) < probability;
    }

    /**
     * Build a random state and pass it to the provided teacher and learner populators.
     * <p>
     * The process starts by creating two identical states with the specified size for both the teacher and the learner.
     * It then uses the provided probabilities to modify the teacher state in order to emulate a scenario
     * where the learner has disconnected from the network and hasn't updated its map with the latest changes:
     * <ul>
     *     <li>teacherAddProbability - a new node is added to the teacher state
     *     <li>teacherRemoveProbability - an existing node is removed from the teacher state
     *     <li>teacherModifyProbability - an existing node is updated with a new value in the teacher state
     * </ul>
     * <p>
     * Note that the state populators must correctly support additions, updates, and removals (when the value is null).
     *
     * @param random a Random instance
     * @param size the number of nodes in the learner state.
     *          The teacher may have a slightly different number of nodes depending on the probabilities below.
     * @param teacherAddProbability the probability of a key to be added to the teacher state
     * @param teacherRemoveProbability the probability of a key to be removed from the teacher state
     * @param teacherModifyProbability the probability of a node under a given key in the teacher state
     *          to have a value that is different from the value under the same key in the learner state.
     * @param teacherPopulator a BiConsumer that persists the teacher state (Map::put or similar)
     * @param learnerPopulator a BiConsumer that persists the learner state (Map::put or similar)
     * @param storageOptimizer a Consumer<Long> that could optimize the underlying state storage
     *          (e.g. compacting it, or splitting it into multiple units such as files, etc.)
     *          based on the current node index passed as a parameter
     */
    public void buildState(
            final Random random,
            final long size,
            final double teacherAddProbability,
            final double teacherRemoveProbability,
            final double teacherModifyProbability,
            final BiConsumer<K, V> teacherPopulator,
            final BiConsumer<K, V> learnerPopulator,
            final Consumer<Long> storageOptimizer) {
        System.err.printf("Building a state of size %,d\n", size);

        LongStream.range(1, size).forEach(i -> {
            storageOptimizer.accept(i);

            final K key = keyBuilder.apply(i);
            // Original values indexes 1..size-1
            final V value = valueBuilder.apply(i);
            teacherPopulator.accept(key, value);
            learnerPopulator.accept(key, value);
        });

        // Current size of the teacher state.
        final AtomicLong curSize = new AtomicLong(size - 1);

        LongStream.range(1, size).forEach(i -> {
            storageOptimizer.accept(i);

            // Make all random outcomes independent of each other:
            final boolean teacherAdd = isRandomOutcome(random, teacherAddProbability);
            final boolean teacherModify = isRandomOutcome(random, teacherModifyProbability);
            final boolean teacherRemove = isRandomOutcome(random, teacherRemoveProbability);

            if (teacherAdd) {
                final K key = keyBuilder.apply(i + size);
                // Added values indexes (size + 1)..(2 * size)
                final V value = valueBuilder.apply(i + size);
                teacherPopulator.accept(key, value);
                curSize.incrementAndGet();
            }

            final long iModify = random.nextLong(curSize.get()) + 1;
            final long iRemove = random.nextLong(curSize.get()) + 1;

            if (teacherModify) {
                final K key = keyBuilder.apply(iModify);
                // Modified values indexes (2 * size + 1)..(3 * size)
                final V value = valueBuilder.apply(iModify + 2L * size);
                teacherPopulator.accept(key, value);
            }

            if (teacherRemove) {
                final K key = keyBuilder.apply(iRemove);
                teacherPopulator.accept(key, null);
                curSize.decrementAndGet();
            }
        });
    }
}
