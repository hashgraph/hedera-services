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
package com.hedera.services.state.migration;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.services.state.virtual.UniqueTokenValue;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UniqueTokenAdapterTest {

    public UniqueTokenAdapter merkleSubject;
    public UniqueTokenAdapter virtualSubject;

    @BeforeEach
    void setUp() {
        merkleSubject =
                new UniqueTokenAdapter(
                        new MerkleUniqueToken(
                                EntityId.fromNum(123L),
                                "hello".getBytes(),
                                RichInstant.MISSING_INSTANT));
        merkleSubject.setSpender(EntityId.fromNum(456L));

        virtualSubject =
                new UniqueTokenAdapter(
                        new UniqueTokenValue(
                                123L, 456L, "hello".getBytes(), RichInstant.MISSING_INSTANT));
    }

    @Test
    void testIsVirtual() {
        assertTrue(virtualSubject.isVirtual());
        assertFalse(merkleSubject.isVirtual());
    }

    @Test
    void testIsImmutable() {
        assertFalse(virtualSubject.isImmutable());
        virtualSubject.uniqueTokenValue().copy();
        assertTrue(virtualSubject.isImmutable());

        assertFalse(merkleSubject.isImmutable());
        merkleSubject.merkleUniqueToken().copy();
        assertTrue(merkleSubject.isImmutable());
    }

    @Test
    void testConstructedValues() {
        assertEquals(123L, virtualSubject.getOwner().num());
        assertEquals(456L, virtualSubject.getSpender().num());
        assertArrayEquals("hello".getBytes(), virtualSubject.getMetadata());
        assertEquals(0L, virtualSubject.getPackedCreationTime());

        assertEquals(123L, merkleSubject.getOwner().num());
        assertEquals(456L, merkleSubject.getSpender().num());
        assertArrayEquals("hello".getBytes(), merkleSubject.getMetadata());
        assertEquals(0L, merkleSubject.getPackedCreationTime());
    }

    @Test
    void testNullValuesPassedToStaticConstructors() {
        assertNull(UniqueTokenAdapter.wrap((MerkleUniqueToken) null));
        assertNull(UniqueTokenAdapter.wrap((UniqueTokenValue) null));
    }

    @Test
    void testSettersUpdateValues() {
        virtualSubject.setOwner(EntityId.fromNum(987L));
        assertEquals(987L, virtualSubject.getOwner().num());
        virtualSubject.setSpender(EntityId.fromNum(654L));
        assertEquals(654L, virtualSubject.getSpender().num());
        virtualSubject.setMetadata("goodbye".getBytes());
        assertArrayEquals("goodbye".getBytes(), virtualSubject.getMetadata());
        virtualSubject.setPackedCreationTime(999L);
        assertEquals(999L, virtualSubject.getPackedCreationTime());

        merkleSubject.setOwner(EntityId.fromNum(987L));
        assertEquals(987L, merkleSubject.getOwner().num());
        merkleSubject.setSpender(EntityId.fromNum(654L));
        assertEquals(654L, merkleSubject.getSpender().num());
        merkleSubject.setMetadata("goodbye".getBytes());
        assertArrayEquals("goodbye".getBytes(), merkleSubject.getMetadata());
        merkleSubject.setPackedCreationTime(999L);
        assertEquals(999L, merkleSubject.getPackedCreationTime());
    }

    @Test
    void testEqualsForSelfReferentialCases() {
        assertEquals(virtualSubject, virtualSubject);
        assertEquals(merkleSubject, merkleSubject);
    }

    @Test
    void testEqualsForSameTypes() {
        final var uniqueToken =
                new UniqueTokenValue(123L, 456L, "hello".getBytes(), RichInstant.MISSING_INSTANT);
        final var merkleUniqueToken =
                new MerkleUniqueToken(
                        EntityId.fromNum(123L), "hello".getBytes(), RichInstant.MISSING_INSTANT);
        merkleUniqueToken.setSpender(EntityId.fromNum(456L));

        assertEquals(UniqueTokenAdapter.wrap(uniqueToken), virtualSubject);
        assertEquals(UniqueTokenAdapter.wrap(merkleUniqueToken), merkleSubject);
    }

    @Test
    void testEqualsFalseForVirtualAndMerkleComparisons() {
        assertNotEquals(virtualSubject, merkleSubject);
        assertNotEquals(merkleSubject, virtualSubject);
    }

    @Test
    void testEqualsFalseForNullComparisons() {
        assertNotEquals(
                virtualSubject, null); // NOSONAR: explicitly test virtualSubject.equals(null)
        assertNotEquals(merkleSubject, null); // NOSONAR: explicitly test merkleSubject.equals(null)
    }

    @Test
    void testEqualsFalseForDifferentTypeComparisons() {
        assertNotEquals(virtualSubject, new Object());
        assertNotEquals(merkleSubject, new Object());
    }

    @Test
    void testEqualsFalseWhenFieldsDiffer() {
        assertNotEquals(
                virtualSubject,
                UniqueTokenAdapter.wrap(
                        new UniqueTokenValue(
                                1L, 456L, "hello".getBytes(), RichInstant.MISSING_INSTANT)));

        assertNotEquals(
                virtualSubject,
                UniqueTokenAdapter.wrap(
                        new UniqueTokenValue(
                                123L, 4L, "hello".getBytes(), RichInstant.MISSING_INSTANT)));

        assertNotEquals(
                virtualSubject,
                UniqueTokenAdapter.wrap(
                        new UniqueTokenValue(
                                123L, 456L, "h".getBytes(), RichInstant.MISSING_INSTANT)));

        assertNotEquals(
                virtualSubject,
                UniqueTokenAdapter.wrap(
                        new UniqueTokenValue(
                                123L, 456L, "hello".getBytes(), new RichInstant(3, 4))));

        UniqueTokenAdapter merkleValue =
                UniqueTokenAdapter.wrap(
                        new MerkleUniqueToken(
                                EntityId.fromNum(123L),
                                "hello".getBytes(),
                                RichInstant.MISSING_INSTANT));

        merkleValue.setSpender(EntityId.fromNum(456L));

        merkleValue.setOwner(EntityId.fromNum(1L));
        assertNotEquals(merkleSubject, merkleValue);

        merkleValue.setOwner(EntityId.fromNum(123L));
        merkleValue.setSpender(EntityId.fromNum(4L));
        assertNotEquals(merkleSubject, merkleValue);

        merkleValue.setSpender(EntityId.fromNum(456L));
        merkleValue.setMetadata("h".getBytes());
        assertNotEquals(merkleSubject, merkleValue);

        merkleValue.setMetadata("hello".getBytes());
        merkleValue.setPackedCreationTime(22L);
        assertNotEquals(merkleSubject, merkleValue);

        merkleValue.setPackedCreationTime(0L);
        assertEquals(merkleSubject, merkleValue);

        assertNotEquals(merkleSubject, UniqueTokenAdapter.newEmptyMerkleToken());
    }

    @Test
    void testDirectAccessGetters() {
        Assertions.assertNotNull(merkleSubject.merkleUniqueToken());
        Assertions.assertNull(merkleSubject.uniqueTokenValue());

        Assertions.assertNull(virtualSubject.merkleUniqueToken());
        Assertions.assertNotNull(virtualSubject.uniqueTokenValue());
    }

    @Test
    void testHashCode() {
        // Hashcode is deterministic
        assertEquals(virtualSubject.hashCode(), virtualSubject.hashCode());
        // Hashcode for different objects differ
        assertNotEquals(virtualSubject.hashCode(), merkleSubject.hashCode());
        // Hashcode for objects with same content is the same
        assertEquals(
                virtualSubject.hashCode(),
                UniqueTokenAdapter.wrap(virtualSubject.uniqueTokenValue()).hashCode());
        assertEquals(
                merkleSubject.hashCode(),
                UniqueTokenAdapter.wrap(merkleSubject.merkleUniqueToken()).hashCode());
    }
}
