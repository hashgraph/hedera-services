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
package com.hedera.services.files.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BytesStoreAdapterTest {
    private final Pattern DIGITS_PATTERN = Pattern.compile("\\d+");
    private final String TPL = "%s-%d";
    private final String PREFIX = "Testing123";
    private final Function<byte[], StringBuilder> toSb =
            bytes ->
                    new StringBuilder()
                            .append(new String(Optional.ofNullable(bytes).orElse(new byte[0])));
    private final Function<StringBuilder, byte[]> fromSb = sb -> sb.toString().getBytes();
    private final Function<Integer, String> fromInteger = i -> String.format(TPL, PREFIX, i);
    private final Function<String, Integer> toInteger =
            s -> Integer.parseInt(s.substring(PREFIX.length() + 1));
    private final Predicate<String> IS_VALID_KEY =
            key -> {
                return key.length() >= (PREFIX.length() + 2)
                        && DIGITS_PATTERN.matcher(key.substring(PREFIX.length() + 1)).matches();
            };

    Map<String, byte[]> delegate;
    BytesStoreAdapter<Integer, StringBuilder> subject;

    @BeforeEach
    void setup() {
        delegate = new HashMap<>();
        delegate.put(fromInteger.apply(0), bytes("ALREADY HERE"));
        subject =
                new BytesStoreAdapter<>(
                        Integer.class, toSb, fromSb, toInteger, fromInteger, delegate);
    }

    @Test
    void preservesSemantics() {
        subject.put(1, new StringBuilder().append("First byte"));
        subject.put(2, new StringBuilder().append("Second byte"));
        subject.put(3, new StringBuilder().append("Third byte"));

        assertFalse(subject.isEmpty());
        assertEquals(4, subject.size());
        subject.remove(2);
        assertEquals(3, subject.size());
        assertEquals(
                "0->ALREADY HERE, 1->First byte, 3->Third byte",
                subject.entrySet().stream()
                        .sorted(Comparator.comparing(Map.Entry::getKey))
                        .map(
                                entry ->
                                        String.format(
                                                "%d->%s",
                                                entry.getKey(), new String(entry.getValue())))
                        .collect(Collectors.joining(", ")));

        assertTrue(subject.containsKey(1));
        assertFalse(subject.containsKey(2));

        subject.clear();
        assertTrue(subject.isEmpty());
    }

    @Test
    void assertByteStoreAdapterGetter() {
        var firstByte = new StringBuilder().append("First byte");
        var secondByte = new StringBuilder().append("Second byte");
        var thirdByte = new StringBuilder().append("Third byte");
        subject.put(1, firstByte);
        subject.put(2, secondByte);
        subject.put(3, thirdByte);

        assertEquals(firstByte.toString(), subject.get(1).toString());
        assertEquals(secondByte.toString(), subject.get(2).toString());
        assertEquals(thirdByte.toString(), subject.get(3).toString());
    }

    @Test
    void usesFilterIfPresent() {
        // setup:
        delegate.put("NOT-A-VALID_KEY", bytes("Nope."));

        // given:
        subject.setDelegateEntryFilter(IS_VALID_KEY);

        // when:
        subject.put(1, new StringBuilder().append("First byte"));

        // expect:
        assertEquals(
                "0->ALREADY HERE, 1->First byte",
                subject.entrySet().stream()
                        .sorted(Comparator.comparing(Map.Entry::getKey))
                        .map(
                                entry ->
                                        String.format(
                                                "%d->%s",
                                                entry.getKey(), new String(entry.getValue())))
                        .collect(Collectors.joining(", ")));
    }

    private byte[] bytes(String s) {
        return s.getBytes();
    }
}
