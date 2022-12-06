/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.node.app.service.mono.files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.primitives.Longs;
import com.hedera.node.app.service.mono.fees.calculation.FeeCalcUtilsTest;
import com.hedera.node.app.service.mono.state.submerkle.EntityId;
import com.hedera.test.utils.IdUtils;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class EntityExpiryMapFactoryTest {
    @Test
    void toFidConversionWorks() {
        // given:
        final var key = "/666/e888";
        // and:
        final var expected = new EntityId(0, 666, 888);

        // when:
        final var actual = EntityExpiryMapFactory.toEid(key);

        // then:
        assertEquals(expected, actual);
    }

    @Test
    void toKeyConversionWorks() {
        // given:
        final var expected = "/2/e3";

        // when:
        final var actual = EntityExpiryMapFactory.toKeyString(new EntityId(0, 2, 3));

        // then:
        assertEquals(expected, actual);
    }

    private String asLegacyPath(final String fid) {
        return FeeCalcUtilsTest.pathOf(IdUtils.asFile(fid)).replace("f", "e");
    }

    @Test
    void productHasMapSemantics() {
        // setup:
        final Map<String, byte[]> delegate = new HashMap<>();
        delegate.put(asLegacyPath("0.2.7"), Longs.toByteArray(111));
        // and:
        final var eid1 = new EntityId(0, 2, 3);
        final var eid2 = new EntityId(0, 3333, 4);
        final var eid3 = new EntityId(0, 4, 555555);
        // and:
        final var theData = 222L;
        final var someData = 333L;
        final var moreData = 444L;
        final var overwrittenData = 555L;

        // given:
        final var expiryMap = EntityExpiryMapFactory.entityExpiryMapFrom(delegate);

        // when:
        expiryMap.put(eid1, overwrittenData);
        expiryMap.put(eid1, someData);
        expiryMap.put(eid2, moreData);
        expiryMap.put(eid3, theData);

        assertFalse(expiryMap.isEmpty());
        assertEquals(4, expiryMap.size());
        assertEquals(
                java.util.Optional.of(moreData),
                java.util.Optional.ofNullable(expiryMap.remove(eid2)));
        assertEquals(3, expiryMap.size());
        assertEquals(
                "/2/e3->333, /4/e555555->222, /2/e7->111",
                delegate.entrySet().stream()
                        .sorted(
                                Comparator.comparingLong(
                                        entry ->
                                                Long.parseLong(
                                                        entry.getKey()
                                                                .substring(
                                                                        entry.getKey().indexOf('e')
                                                                                + 1,
                                                                        entry.getKey().indexOf('e')
                                                                                + 2))))
                        .map(
                                entry ->
                                        String.format(
                                                "%s->%d",
                                                entry.getKey(),
                                                Longs.fromByteArray(entry.getValue())))
                        .collect(Collectors.joining(", ")));

        assertTrue(expiryMap.containsKey(eid1));
        assertFalse(expiryMap.containsKey(eid2));

        expiryMap.clear();
        assertTrue(expiryMap.isEmpty());
    }

    @Test
    void toLongPropagatesNull() {
        // expect:
        assertNull(EntityExpiryMapFactory.toLong(null));
    }

    @Test
    void throwsIaeOnNonsense() {
        final var bytes = "wtf".getBytes();
        // expect:
        assertThrows(IllegalArgumentException.class, () -> EntityExpiryMapFactory.toLong(bytes));
    }

    @Test
    void cannotBeConstructed() {
        // expect:
        assertThrows(IllegalStateException.class, EntityExpiryMapFactory::new);
    }
}
