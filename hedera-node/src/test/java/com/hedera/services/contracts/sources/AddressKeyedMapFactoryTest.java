/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.contracts.sources;

import static com.hedera.services.contracts.sources.AddressKeyedMapFactory.LEGACY_BYTECODE_PATH_PATTERN;
import static com.hedera.services.contracts.sources.AddressKeyedMapFactory.LEGACY_BYTECODE_PATH_TEMPLATE;
import static com.hedera.services.contracts.sources.AddressKeyedMapFactory.bytecodeMapFrom;
import static com.hedera.services.contracts.sources.AddressKeyedMapFactory.storageMapFrom;
import static com.hedera.services.contracts.sources.AddressKeyedMapFactory.toAddressMapping;
import static com.hedera.services.contracts.sources.AddressKeyedMapFactory.toKeyMapping;
import static com.hedera.services.contracts.sources.AddressKeyedMapFactory.toRelevancyPredicate;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.services.utils.EntityIdUtils;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class AddressKeyedMapFactoryTest {
    @Test
    void toAddressConversion() {
        final var mapper = toAddressMapping(LEGACY_BYTECODE_PATH_PATTERN);
        final var key = "/666/s888";
        final var expected = EntityIdUtils.asEvmAddress(0, 666, 888);

        final var actual = mapper.apply(key);

        assertArrayEquals(expected, actual);
    }

    @Test
    void toKeyConversionWorks() {
        final var mapper = toKeyMapping(LEGACY_BYTECODE_PATH_TEMPLATE);
        final var address = EntityIdUtils.asEvmAddress(0, 666, 888);
        final var expected = "/666/s888";

        final var actual = mapper.apply(address);

        assertEquals(expected, actual);
    }

    @Test
    void isRelevantWorks() {
        final var realKey = "/666/s888";
        final var fakeKey = "/a66/s888";
        final var pred = toRelevancyPredicate(LEGACY_BYTECODE_PATH_PATTERN);

        assertTrue(pred.test(realKey));
        assertFalse(pred.test(fakeKey));
    }

    @Test
    void bytecodeProductHasMapSemantics() {
        final Map<String, byte[]> delegate = new HashMap<>();
        delegate.put(("/2/s7"), "APRIORI".getBytes());
        final var address1 = EntityIdUtils.asEvmAddress(0, 2, 3);
        final var address2 = EntityIdUtils.asEvmAddress(0, 3333, 4);
        final var address3 = EntityIdUtils.asEvmAddress(0, 4, 555555);
        final var theData = "THE".getBytes();
        final var someData = "SOME".getBytes();
        final var moreData = "MORE".getBytes();
        final var storageMap = bytecodeMapFrom(delegate);

        storageMap.put(address1, someData);
        storageMap.put(address2, moreData);
        storageMap.put(address3, theData);

        assertFalse(storageMap.isEmpty());
        assertEquals(4, storageMap.size());
        storageMap.remove(address2);
        assertEquals(3, storageMap.size());
        assertEquals(
                "/2/s3->SOME, /4/s555555->THE, /2/s7->APRIORI",
                delegate.entrySet().stream()
                        .sorted(
                                Comparator.comparingLong(
                                        entry ->
                                                Long.parseLong(
                                                        entry.getKey()
                                                                .substring(
                                                                        entry.getKey().indexOf('s')
                                                                                + 1,
                                                                        entry.getKey().indexOf('s')
                                                                                + 2))))
                        .map(
                                entry ->
                                        String.format(
                                                "%s->%s",
                                                entry.getKey(), new String(entry.getValue())))
                        .collect(Collectors.joining(", ")));

        assertTrue(storageMap.containsKey(address1));
        assertFalse(storageMap.containsKey(address2));

        storageMap.clear();
        assertTrue(storageMap.isEmpty());
    }

    @Test
    void productHasFilterSet() {
        final Map<String, byte[]> delegate = new HashMap<>();
        delegate.put(("NOT-REAL-KEY"), "APRIORI".getBytes());

        final var storageMap = bytecodeMapFrom(delegate);

        assertTrue(storageMap.entrySet().isEmpty());
    }

    @Test
    void storageProductHasMapSemantics() {
        final Map<String, byte[]> delegate = new HashMap<>();
        delegate.put(("/2/d7"), "APRIORI".getBytes());
        final var address1 = EntityIdUtils.asEvmAddress(0, 2, 3);
        final var address2 = EntityIdUtils.asEvmAddress(0, 3333, 4);
        final var address3 = EntityIdUtils.asEvmAddress(0, 4, 555555);
        final var theData = "THE".getBytes();
        final var someData = "SOME".getBytes();
        final var moreData = "MORE".getBytes();

        final var storageMap = storageMapFrom(delegate);

        storageMap.put(address1, someData);
        storageMap.put(address2, moreData);
        storageMap.put(address3, theData);

        assertFalse(storageMap.isEmpty());
        assertEquals(4, storageMap.size());
        storageMap.remove(address2);
        assertEquals(3, storageMap.size());
        assertEquals(
                "/2/d3->SOME, /4/d555555->THE, /2/d7->APRIORI",
                delegate.entrySet().stream()
                        .sorted(
                                Comparator.comparingLong(
                                        entry ->
                                                Long.parseLong(
                                                        entry.getKey()
                                                                .substring(
                                                                        entry.getKey().indexOf('d')
                                                                                + 1,
                                                                        entry.getKey().indexOf('d')
                                                                                + 2))))
                        .map(
                                entry ->
                                        String.format(
                                                "%s->%s",
                                                entry.getKey(), new String(entry.getValue())))
                        .collect(Collectors.joining(", ")));

        assertTrue(storageMap.containsKey(address1));
        assertFalse(storageMap.containsKey(address2));

        storageMap.clear();
        assertTrue(storageMap.isEmpty());
    }
}
