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
package com.hedera.services.state.expiry;

import static com.hedera.services.utils.NftNumPair.MISSING_NFT_NUM_PAIR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.state.merkle.internals.BitPackUtils;
import com.hedera.services.utils.EntityNumPair;
import com.hedera.services.utils.NftNumPair;
import com.swirlds.merkle.map.MerkleMap;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UniqueTokensListMutationTest {
    @Mock private MerkleMap<EntityNumPair, MerkleUniqueToken> uniqueTokens;

    private UniqueTokensListMutation subject;

    @BeforeEach
    void setUp() {
        subject = new UniqueTokensListMutation(uniqueTokens);
    }

    @Test
    void delegatesGet() {
        given(uniqueTokens.get(rootNftKey)).willReturn(rootNft);

        assertSame(rootNft, subject.get(rootNftKey));
    }

    @Test
    void delegatesGet4M() {
        given(uniqueTokens.getForModify(rootNftKey)).willReturn(rootNft);

        assertSame(rootNft, subject.getForModify(rootNftKey));
    }

    @Test
    void delegatesRemove() {
        subject.remove(rootNftKey);

        verify(uniqueTokens).remove(rootNftKey);
    }

    @Test
    void marksHeadAsExpected() {
        nextNft.setPrev(rootPair);

        subject.markAsHead(nextNft);

        assertEquals(MISSING_NFT_NUM_PAIR, nextNft.getPrev());
    }

    @Test
    void marksTailAsExpected() {
        targetNft.setNext(nextPair);

        subject.markAsTail(targetNft);

        assertEquals(MISSING_NFT_NUM_PAIR, targetNft.getNext());
    }

    @Test
    void setsPrevAsExpected() {
        subject.updatePrev(targetNft, rootNftKey);

        assertEquals(rootPair, targetNft.getPrev());
    }

    @Test
    void setsNextAsExpected() {
        subject.updateNext(targetNft, nextNftKey);

        assertEquals(nextPair, targetNft.getNext());
    }

    @Test
    void getsExpectedPrev() {
        targetNft.setPrev(rootPair);

        final var ans = subject.prev(targetNft);

        assertEquals(rootNftKey, ans);
    }

    @Test
    void getsNullPrevIfNoneSet() {
        final var ans = subject.prev(targetNft);

        assertNull(ans);
    }

    @Test
    void getsExpectedNext() {
        targetNft.setNext(nextPair);

        final var ans = subject.next(targetNft);

        assertEquals(nextNftKey, ans);
    }

    @Test
    void getsNullNextIfNoneSet() {
        final var ans = subject.next(targetNft);

        assertNull(ans);
    }

    @Test
    void nextAndPrevReturnsNullForCopies() {
        targetNft.setNext(NftNumPair.fromLongs(0, 0));
        targetNft.setPrev(NftNumPair.fromLongs(0, 0));
        assertNull(subject.next(targetNft));
        assertNull(subject.prev(targetNft));
    }

    private final long tokenNum = 1_234L;
    private final int ownerNum = 1_235;
    private final long rootNum = 2L;
    private final long nextNum = 8L;
    private final long targetNum = 4L;
    private final long seconds = 1_234_567L;
    private final int nanos = 890;
    private final NftNumPair rootPair = NftNumPair.fromLongs(tokenNum, rootNum);
    private final NftNumPair nextPair = NftNumPair.fromLongs(tokenNum, nextNum);
    private final NftNumPair targetPair = NftNumPair.fromLongs(tokenNum, targetNum);
    private final long packedTime = BitPackUtils.packedTime(seconds, nanos);
    private final EntityNumPair rootNftKey = EntityNumPair.fromLongs(tokenNum, rootNum);
    private final EntityNumPair nextNftKey = EntityNumPair.fromLongs(tokenNum, nextNum);
    private final EntityNumPair targetNftKey = EntityNumPair.fromLongs(tokenNum, targetNum);
    private final MerkleUniqueToken rootNft =
            new MerkleUniqueToken(
                    ownerNum, "aa".getBytes(StandardCharsets.UTF_8), packedTime, rootNum);
    private final MerkleUniqueToken nextNft =
            new MerkleUniqueToken(
                    ownerNum, "bb".getBytes(StandardCharsets.UTF_8), packedTime, nextNum);
    private final MerkleUniqueToken targetNft =
            new MerkleUniqueToken(
                    ownerNum, "cc".getBytes(StandardCharsets.UTF_8), packedTime, targetNum);
}
