/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.swirlds.merkle.map.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.test.dummy.Key;
import com.swirlds.common.test.dummy.Value;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.test.framework.TestComponentTags;
import com.swirlds.test.framework.TestTypeTags;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Random;
import java.util.function.BiConsumer;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@Tag(TestTypeTags.INFREQUENT_EXEC_ONLY)
class MerkleMapMemoryTests {

    private static final Random PRNG_PROVIDER = new Random(System.currentTimeMillis());

    private static final int DATA_POINTS_PER_SAMPLE = 4;
    private static long[][] memoryStats;

    @BeforeAll
    static void startup() throws ConstructableRegistryException {
        ConstructableRegistry.getInstance().registerConstructables("com.swirlds");
    }

    /**
     * Creates the specified number of accounts and measures the initial, ending, and {@code N - 2} maxMemSamples.
     *
     * @param numberOfAccounts
     * 		the total of number accounts to create
     * @param numberOfCopies
     * 		the total number of fast copies to make
     * @param maxKeepCopies
     * 		the total number of fast copies to be retained in memory
     * @param maxMemSamples
     * 		the total number of memory samples to take throughout the test
     */
    @ParameterizedTest
    @Tag(TestComponentTags.MMAP)
    @Tag(TestTypeTags.FUNCTIONAL)
    @DisplayName("MerkleMap Memory Usage")
    @CsvSource(value = {"1000000, 1000, 10, 20"})
    void testCreationMemoryUsage(int numberOfAccounts, int numberOfCopies, int maxKeepCopies, int maxMemSamples) {
        buildAndExecute(null, 0, numberOfAccounts, numberOfCopies, maxKeepCopies, maxMemSamples);
    }

    /**
     * Creates the specified number of accounts, modifies the accounts starting at the specified offset, and measures
     * the initial, ending, and {@code N - 2} maxMemSamples.
     *
     * @param numberOfAccounts
     * 		the total of number accounts to create
     * @param numberOfCopies
     * 		the total number of fast copies to make
     * @param maxKeepCopies
     * 		the total number of fast copies to be retained in memory
     * @param maxMemSamples
     * 		the total number of memory samples to take throughout the test
     * @param opOffset
     * 		the offset at which to begin executing the modifications relative to the currently created account
     */
    @ParameterizedTest
    @Tag(TestComponentTags.MMAP)
    @Tag(TestTypeTags.FUNCTIONAL)
    @DisplayName("MerkleMap Memory Usage")
    @CsvSource(value = {"1000000, 1000, 10, 20, -1"})
    void testModificationMemoryUsage(
            int numberOfAccounts, int numberOfCopies, int maxKeepCopies, int maxMemSamples, int opOffset) {
        buildAndExecute(this::modifyAccount, opOffset, numberOfAccounts, numberOfCopies, maxKeepCopies, maxMemSamples);
    }

    private void buildAndExecute(
            final BiConsumer<MerkleMap<Key, Value>, Integer> op,
            final int opOffset,
            final int numberOfAccounts,
            final int numberOfCopies,
            final int maxKeepCopies,
            final int maxMemSamples) {
        MerkleMap<Key, Value> memoryMap = new MerkleMap<>();
        final Queue<MerkleMap<Key, Value>> copyQueue = new LinkedList<>();
        final int copyStepSize = (int) Math.floor(numberOfAccounts / (double) numberOfCopies);
        final int memStepSize = (int) Math.ceil(numberOfAccounts / (double) (maxMemSamples - 2));
        memoryStats = new long[maxMemSamples][DATA_POINTS_PER_SAMPLE];
        int memoryIndex = 1;

        measureMemoryUsage(0);

        for (int i = 0; i < numberOfAccounts; i++) {
            createAccount(memoryMap, i);

            if (op != null) {
                op.accept(memoryMap, i + opOffset);
            }

            if (i % copyStepSize == 0) {
                final MerkleMap<Key, Value> copy = memoryMap.copy();
                memoryMap.reserve();
                copyQueue.add(memoryMap);
                memoryMap = copy;

                cleanQueue(copyQueue, maxKeepCopies);
            }

            if (i % memStepSize == 0) {
                measureMemoryUsage(memoryIndex);
                memoryIndex++;
            }
        }

        cleanQueue(copyQueue, maxKeepCopies);

        assertEquals(
                maxKeepCopies,
                copyQueue.size(),
                String.format(
                        "Failed to maintain the correct number of fast-copies, expected %d but had %d copies at the test "
                                + "conclusion.",
                        maxKeepCopies, copyQueue.size()));

        try {
            for (int i = 0; i < 10; i++) {
                System.gc();
                Thread.sleep(1000);
            }

            measureMemoryUsage(memoryStats.length - 1);
            reportEstimatedUsage();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private void cleanQueue(final Queue<MerkleMap<Key, Value>> queue, final int maxSize) {
        try {
            while (queue.size() > maxSize) {
                final MerkleMap<Key, Value> map = queue.remove();
                map.release();
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void createAccount(final MerkleMap<Key, Value> map, final int index) {
        final Key key = new Key(0, 0, index);
        final Value value = new Value(
                PRNG_PROVIDER.nextLong(),
                PRNG_PROVIDER.nextLong(),
                PRNG_PROVIDER.nextLong(),
                PRNG_PROVIDER.nextBoolean());

        map.put(key, value);
    }

    private void modifyAccount(final MerkleMap<Key, Value> map, final int index) {
        if (index < 0) {
            return;
        }

        final Key key = new Key(0, 0, index);

        final Value value = map.getForModify(key);

        if (value == null) {
            return;
        }

        value.setBalance(PRNG_PROVIDER.nextLong());

        map.replace(key, value);
    }

    private void measureMemoryUsage(final int index) {
        System.gc();
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }

        final long free = Runtime.getRuntime().freeMemory();
        final long total = Runtime.getRuntime().totalMemory();
        final long max = Runtime.getRuntime().maxMemory();

        memoryStats[index][0] = free;
        memoryStats[index][1] = total;
        memoryStats[index][2] = total - free;
        memoryStats[index][3] = max;
    }

    private void reportEstimatedUsage() {
        for (int i = 0; i < memoryStats.length; i++) {
            System.out.println("=".repeat(80));
            System.out.printf("\t\t\t\t\t\t\t\tMemory Sample #%d%n", i + 1);
            System.out.println("-".repeat(80));
            System.out.printf(
                    "\t\tUsed: %,d\t\tFree: %,d\t\tTotal: %,d%n",
                    memoryStats[i][2], memoryStats[i][0], memoryStats[i][1]);
            System.out.println("=".repeat(80));
            System.out.println();
            System.out.println();
        }

        final long deltaUsed = memoryStats[memoryStats.length - 1][2] - memoryStats[0][2];

        System.out.printf(
                "Total Estimated MerkleMap Usage: %,d (%s)%n", deltaUsed, FileUtils.byteCountToDisplaySize(deltaUsed));
    }
}
