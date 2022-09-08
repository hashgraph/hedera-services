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
package com.hedera.services.ledger.backing;

import static com.hedera.services.state.submerkle.EntityId.MISSING_ENTITY_ID;
import static com.hedera.services.state.submerkle.RichInstant.MISSING_INSTANT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.state.migration.UniqueTokenAdapter;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.services.state.virtual.UniqueTokenValue;
import com.hedera.services.store.models.NftId;
import com.hedera.services.utils.EntityNumPair;
import com.swirlds.merkle.map.MerkleMap;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BackingNftsTest {
    private final NftId aNftId = new NftId(0, 0, 3, 4);
    private final NftId bNftId = new NftId(0, 0, 4, 5);
    private final NftId cNftId = new NftId(0, 0, 5, 6);
    private final EntityNumPair aKey = EntityNumPair.fromLongs(3, 4);
    private final EntityNumPair bKey = EntityNumPair.fromLongs(4, 5);
    private final EntityNumPair cKey = EntityNumPair.fromLongs(5, 6);
    private final UniqueTokenAdapter aValue =
            UniqueTokenAdapter.wrap(
                    new MerkleUniqueToken(
                            new EntityId(0, 0, 3),
                            "abcdefgh".getBytes(),
                            new RichInstant(1_234_567L, 1)));
    private final MerkleUniqueToken theToken =
            new MerkleUniqueToken(
                    MISSING_ENTITY_ID, "HI".getBytes(StandardCharsets.UTF_8), MISSING_INSTANT);
    private final MerkleUniqueToken notTheToken =
            new MerkleUniqueToken(
                    MISSING_ENTITY_ID, "IH".getBytes(StandardCharsets.UTF_8), MISSING_INSTANT);

    private MerkleMap<EntityNumPair, MerkleUniqueToken> delegate;

    private BackingNfts subject;

    @BeforeEach
    void setUp() {
        delegate = new MerkleMap<>();

        delegate.put(aKey, theToken);
        delegate.put(bKey, notTheToken);

        subject = new BackingNfts(() -> delegate);
    }

    @Test
    void doSupportGettingIdSet() {
        // when:
        subject = new BackingNfts(() -> delegate);

        // expect:
        assertNotNull(subject.idSet());
        assertEquals(2, subject.size());
    }

    @Test
    void containsWorks() {
        // expect:
        assertTrue(subject.contains(aNftId));
        assertTrue(subject.contains(bNftId));
        assertFalse(subject.contains(cNftId));
    }

    @Test
    void getRefDelegatesToGetForModify() {
        // when:
        final var mutable = subject.getRef(aNftId);

        // then:
        assertEquals(theToken, mutable.merkleUniqueToken());
        assertFalse(mutable.isImmutable());
    }

    @Test
    void getImmutableRefDelegatesToGet() {
        // when:
        final var immutable = subject.getImmutableRef(aNftId);

        // then:
        assertEquals(theToken, immutable.merkleUniqueToken());
    }

    @Test
    void putWorks() {
        // when:
        subject.put(aNftId, aValue);
        subject.put(cNftId, aValue);

        // then:
        assertEquals(aValue, subject.getImmutableRef(cNftId));
    }

    @Test
    void removeWorks() {
        // when:
        subject.remove(aNftId);

        // then:
        assertFalse(subject.contains(aNftId));
    }

    @Test
    void sizePropagatesCallToDelegate() {
        assertEquals(delegate.size(), subject.size());
    }

    @Test
    void putVirtualToken() {
        subject.remove(aNftId);
        final var token =
                UniqueTokenAdapter.wrap(
                        new UniqueTokenValue(123L, 456L, "hello".getBytes(), MISSING_INSTANT));
        assertThrows(UnsupportedOperationException.class, () -> subject.put(aNftId, token));
    }
}
