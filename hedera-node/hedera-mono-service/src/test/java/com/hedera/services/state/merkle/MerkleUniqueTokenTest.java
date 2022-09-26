/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.state.merkle;

import static com.hedera.services.state.merkle.internals.BitPackUtils.MAX_NUM_ALLOWED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.services.state.merkle.internals.BitPackUtils;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.services.utils.EntityNumPair;
import com.hedera.services.utils.NftNumPair;
import java.time.Instant;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MerkleUniqueTokenTest {
    private MerkleUniqueToken subject;

    private final long numbers = BitPackUtils.packedNums(123, 456);
    private EntityId owner;
    private EntityId spender;
    private EntityId otherOwner;
    private byte[] metadata;
    private byte[] otherMetadata;
    private RichInstant timestamp;
    private RichInstant otherTimestamp;
    private NftNumPair prev;
    private NftNumPair next;

    private static long timestampL = 1_234_567L;

    @BeforeEach
    void setup() {
        owner = new EntityId(0, 0, 3);
        otherOwner = new EntityId(0, 0, 4);
        spender = new EntityId(0, 0, 5);
        metadata = "Test NFT".getBytes();
        otherMetadata = "Test NFT2".getBytes();
        timestamp = RichInstant.fromJava(Instant.ofEpochSecond(timestampL));
        otherTimestamp = RichInstant.fromJava(Instant.ofEpochSecond(1_234_568L));
        prev = new NftNumPair(123, 1234);
        next = new NftNumPair(234, 2345);

        subject = new MerkleUniqueToken(owner, metadata, timestamp);
        subject.setKey(new EntityNumPair(numbers));
        subject.setSpender(spender);
        subject.setPrev(prev);
        subject.setNext(next);
    }

    @Test
    void equalsContractWorks() {
        // setup:
        final var key = new EntityNumPair(numbers);
        // given
        var other = new MerkleUniqueToken(owner, metadata, otherTimestamp);
        other.setSpender(spender);
        other.setKey(key);
        var other2 = new MerkleUniqueToken(owner, otherMetadata, timestamp);
        other2.setSpender(spender);
        other2.setKey(key);
        var other3 = new MerkleUniqueToken(otherOwner, metadata, timestamp);
        other3.setKey(key);
        var other4 = new MerkleUniqueToken(owner, metadata, timestamp);
        other4.setKey(new EntityNumPair(numbers + 1));
        var other5 = new MerkleUniqueToken(owner, metadata, timestamp);
        other5.setKey(key);
        var other6 = new MerkleUniqueToken(owner, metadata, timestamp);
        other6.setKey(key);
        other6.setSpender(spender);
        other6.setPrev(next);
        other6.setNext(prev);
        var other7 = new MerkleUniqueToken(owner, metadata, timestamp);
        other7.setKey(key);
        other7.setSpender(spender);
        other7.setPrev(prev);
        other7.setNext(prev);
        var identical = new MerkleUniqueToken(owner, metadata, timestamp);
        identical.setKey(key);
        identical.setSpender(spender);
        identical.setPrev(prev);
        identical.setNext(next);

        // expect
        assertNotEquals(subject, new Object());
        assertNotEquals(subject, other);
        assertNotEquals(subject, other2);
        assertNotEquals(subject, other3);
        assertNotEquals(subject, other4);
        assertNotEquals(subject, other5);
        assertNotEquals(subject, other6);
        assertNotEquals(subject, other7);
        assertEquals(subject, identical);
    }

    @Test
    void hashCodeWorks() {
        // given:
        var identical = new MerkleUniqueToken(owner, metadata, timestamp);
        identical.setKey(new EntityNumPair(numbers));
        identical.setSpender(spender);
        identical.setPrev(prev);
        identical.setNext(next);
        var other = new MerkleUniqueToken(otherOwner, otherMetadata, otherTimestamp);

        // expect:
        assertNotEquals(subject.hashCode(), other.hashCode());
        assertEquals(subject.hashCode(), identical.hashCode());
    }

    @Test
    void toStringWorks() {
        // given:
        assertEquals(
                "MerkleUniqueToken{"
                        + "id=0.0.123.456, owner=0.0.3, "
                        + "creationTime=1970-01-15T06:56:07Z, "
                        + "metadata="
                        + Arrays.toString(metadata)
                        + ", spender=0.0.5, prevNftOwned=0.0.123.1234, "
                        + "nextNftOwned=0.0.234.2345}",
                subject.toString());
    }

    @Test
    void copyWorks() {
        // given:
        var copyNft = subject.copy();
        var other = new Object();

        // expect:
        assertNotSame(copyNft, subject);
        assertEquals(subject, copyNft);
        assertNotEquals(subject, other);
    }

    @Test
    void merkleMethodsWork() {
        // expect;
        assertEquals(MerkleUniqueToken.CURRENT_VERSION, subject.getVersion());
        assertEquals(MerkleUniqueToken.RUNTIME_CONSTRUCTABLE_ID, subject.getClassId());
        assertTrue(subject.isLeaf());
    }

    @Test
    void setsAndGetsOwner() {
        // setup:
        final var smallNumOwner = new EntityId(0, 0, 1);
        final var largeNumOwner = new EntityId(0, 0, MAX_NUM_ALLOWED);

        // expect:
        subject.setOwner(smallNumOwner);
        assertEquals(smallNumOwner, subject.getOwner());

        // and expect:
        subject.setOwner(largeNumOwner);
        assertEquals(largeNumOwner, subject.getOwner());
    }

    @Test
    void getsMetadata() {
        assertEquals(metadata, subject.getMetadata());
    }

    @Test
    void getsCreationTime() {
        assertEquals(timestamp, subject.getCreationTime());
    }

    @Test
    void treasuryOwnershipCheckWorks() {
        // expect:
        assertFalse(subject.isTreasuryOwned());

        // and:
        subject.setOwner(EntityId.MISSING_ENTITY_ID);
        // then:
        assertTrue(subject.isTreasuryOwned());
    }
}
