/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

package com.swirlds.merkledb;

import static com.swirlds.common.threading.manager.AdHocThreadManager.getStaticThreadManager;
import static com.swirlds.merkledb.VirtualMapSerializationTests.KEY_SERIALIZER;
import static com.swirlds.merkledb.VirtualMapSerializationTests.VALUE_SERIALIZER;
import static com.swirlds.merkledb.VirtualMapSerializationTests.constructBuilder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.base.utility.Pair;
import com.swirlds.common.test.fixtures.junit.tags.TestQualifierTags;
import com.swirlds.merkledb.test.fixtures.ExampleFixedSizeVirtualValue;
import com.swirlds.merkledb.test.fixtures.ExampleLongKeyFixedSize;
import com.swirlds.virtualmap.VirtualMap;
import com.swirlds.virtualmap.VirtualMapMigration;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

class MigrationTest {

    @Test
    @Tag(TestQualifierTags.TIME_CONSUMING)
    @DisplayName("extractVirtualMapData() Test")
    void extractVirtualMapDataTest() throws IOException, InterruptedException {

        final int size = 5_000_000;

        // Build a virtual map.
        VirtualMap<ExampleLongKeyFixedSize, ExampleFixedSizeVirtualValue> map =
                new VirtualMap<>("extractVirtualMapDataTest", KEY_SERIALIZER, VALUE_SERIALIZER, constructBuilder());
        for (int i = 0; i < size; i++) {
            if (((i + 1) % (size / 100) == 0)) {
                // Make a copy of the map in order to allow things to be flushed to disk
                VirtualMap<ExampleLongKeyFixedSize, ExampleFixedSizeVirtualValue> copy = map.copy();
                map.release();
                map = copy;
            }

            map.put(new ExampleLongKeyFixedSize(i), new ExampleFixedSizeVirtualValue(i * 2));
        }

        final List<Long> firstVisitOrder = new ArrayList<>(size);
        final List<Long> secondVisitOrder = new ArrayList<>(size);
        final Set<Long> visited = new HashSet<>();

        VirtualMapMigration.extractVirtualMapData(
                getStaticThreadManager(),
                map,
                (final Pair<ExampleLongKeyFixedSize, ExampleFixedSizeVirtualValue> pair) -> {
                    assertEquals(pair.key().getValue() * 2, pair.value().getId(), "key and value do not match");
                    firstVisitOrder.add(pair.key().getValue());
                    assertTrue(visited.add(pair.key().getValue()), "value should not have been already visited");
                },
                32);

        VirtualMapMigration.extractVirtualMapData(
                getStaticThreadManager(),
                map,
                (final Pair<ExampleLongKeyFixedSize, ExampleFixedSizeVirtualValue> pair) -> {
                    assertEquals(pair.key().getValue() * 2, pair.value().getId(), "key and value do not match");
                    secondVisitOrder.add(pair.key().getValue());
                    assertFalse(visited.add(pair.key().getValue()), "value should have already been visited");
                },
                31); // thread count should not matter for correctness

        assertEquals(size, firstVisitOrder.size(), "unexpected size");
        assertEquals(size, secondVisitOrder.size(), "unexpected size");

        for (int i = 0; i < size; i++) {
            assertEquals(firstVisitOrder.get(i), secondVisitOrder.get(i), "visitation order should be the same");
        }

        map.release();
    }

    private static long bytesToLong(byte[] bytes) {
        long result = 0L;
        for (byte b : bytes) {
            result = result * 256 + ((long) b & 0xff);
        }
        return result;
    }

    @Test
    @Tag(TestQualifierTags.TIME_CONSUMING)
    @DisplayName("Extract VirtualMap Data Concurrently")
    void extractDataConcurrentlyTest() throws IOException, InterruptedException {

        final int size = 5_000_000;

        // Build a virtual map.
        VirtualMap<ExampleLongKeyFixedSize, ExampleFixedSizeVirtualValue> map =
                new VirtualMap<>("extractDataConcurrentlyTest", KEY_SERIALIZER, VALUE_SERIALIZER, constructBuilder());

        final Random random = new Random(42);
        final byte[] value = new byte[ExampleFixedSizeVirtualValue.RANDOM_BYTES];
        long checkSum = 0L;
        for (int i = 0; i < size; i++) {
            if ((i + 1) % (size / 100) == 0) {
                // Make a copy of the map in order to allow things to be flushed to disk
                VirtualMap<ExampleLongKeyFixedSize, ExampleFixedSizeVirtualValue> copy = map.copy();
                map.release();
                map = copy;
            }

            random.nextBytes(value);
            map.put(new ExampleLongKeyFixedSize(i), new ExampleFixedSizeVirtualValue(i, value));
            checkSum += bytesToLong(value);
        }

        // Migrate the last copy concurrently
        final AtomicLong checkSum2 = new AtomicLong(0L);
        VirtualMapMigration.extractVirtualMapDataC(
                getStaticThreadManager(),
                map,
                (final Pair<ExampleLongKeyFixedSize, ExampleFixedSizeVirtualValue> pair) -> {
                    checkSum2.addAndGet(bytesToLong(pair.value().getData()));
                },
                32);
        assertEquals(checkSum, checkSum2.get());
    }
}
