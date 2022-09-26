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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.EntityNumPair;
import com.swirlds.merkle.map.MerkleMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TokenRelsListMutationTest {
    private static final long accountNum = 1_234L;

    @Mock private MerkleMap<EntityNumPair, MerkleTokenRelStatus> tokenRels;

    private TokenRelsListMutation subject;

    @BeforeEach
    void setUp() {
        subject = new TokenRelsListMutation(accountNum, tokenRels);
    }

    @Test
    void delegatesGet() {
        given(tokenRels.get(rootRelKey)).willReturn(rootRel);

        assertSame(rootRel, subject.get(rootRelKey));
    }

    @Test
    void delegatesGet4M() {
        given(tokenRels.getForModify(rootRelKey)).willReturn(rootRel);

        assertSame(rootRel, subject.getForModify(rootRelKey));
    }

    @Test
    void delegatesPut() {
        subject.put(rootRelKey, rootRel);

        verify(tokenRels).put(rootRelKey, rootRel);
    }

    @Test
    void delegatesRemove() {
        subject.remove(rootRelKey);

        verify(tokenRels).remove(rootRelKey);
    }

    @Test
    void marksHeadAsExpected() {
        nextRel.setPrev(rootNum);

        subject.markAsHead(nextRel);

        assertEquals(EntityNum.MISSING_NUM.longValue(), nextRel.prevKey());
    }

    @Test
    void marksTailAsExpected() {
        targetRel.setNext(nextNum);

        subject.markAsTail(targetRel);

        assertEquals(EntityNum.MISSING_NUM.longValue(), targetRel.nextKey());
    }

    @Test
    void setsPrevAsExpected() {
        subject.updatePrev(targetRel, rootRelKey);

        assertEquals(rootNum, targetRel.prevKey());
    }

    @Test
    void setsNextAsExpected() {
        subject.updateNext(targetRel, nextRelKey);

        assertEquals(nextNum, targetRel.nextKey());
    }

    @Test
    void getsExpectedPrev() {
        targetRel.setPrev(rootNum);

        final var ans = subject.prev(targetRel);

        assertEquals(rootRelKey, ans);
    }

    @Test
    void getsNullPrevIfNoneSet() {
        final var ans = subject.prev(targetRel);

        assertNull(ans);
    }

    @Test
    void getsExpectedNext() {
        targetRel.setNext(nextNum);

        final var ans = subject.next(targetRel);

        assertEquals(nextRelKey, ans);
    }

    @Test
    void getsNullNextIfNoneSet() {
        final var ans = subject.next(targetRel);

        assertNull(ans);
    }

    private final long rootNum = 2L;
    private final long nextNum = 8L;
    private final long targetNum = 4L;
    private final EntityNumPair rootRelKey = EntityNumPair.fromLongs(accountNum, rootNum);
    private final EntityNumPair nextRelKey = EntityNumPair.fromLongs(accountNum, nextNum);
    private final EntityNumPair targetRelKey = EntityNumPair.fromLongs(accountNum, targetNum);
    private final MerkleTokenRelStatus rootRel =
            new MerkleTokenRelStatus(1, true, false, true, rootRelKey.value());
    private final MerkleTokenRelStatus nextRel =
            new MerkleTokenRelStatus(2, false, true, false, nextRelKey.value());
    private final MerkleTokenRelStatus targetRel =
            new MerkleTokenRelStatus(2, true, false, true, targetRelKey.value());
}
