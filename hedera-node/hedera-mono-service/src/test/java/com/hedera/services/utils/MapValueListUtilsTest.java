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
package com.hedera.services.utils;

import static com.hedera.services.utils.MapValueListUtils.internalDetachFromMapValueList;
import static com.hedera.services.utils.MapValueListUtils.removeFromMapValueList;
import static com.hedera.services.utils.MapValueListUtils.unlinkInPlaceFromMapValueList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.services.state.expiry.TokenRelsListMutation;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.swirlds.merkle.map.MerkleMap;
import org.junit.jupiter.api.Test;

class MapValueListUtilsTest {
    private MerkleMap<EntityNumPair, MerkleTokenRelStatus> tokenRels = new MerkleMap<>();

    @Test
    void sequentialRemovalWorksAsExpected() {
        initializeRels();

        final var relsListRemoval = new TokenRelsListMutation(accountNum.longValue(), tokenRels);

        final var k1 = removeFromMapValueList(aRelKey, aRelKey, relsListRemoval);
        assertEquals(bRelKey, k1);
        assertFalse(tokenRels.containsKey(aRelKey));

        final var k2 = removeFromMapValueList(k1, k1, relsListRemoval);
        assertEquals(cRelKey, k2);
        assertFalse(tokenRels.containsKey(bRelKey));

        final var k3 = removeFromMapValueList(k2, k2, relsListRemoval);
        assertNull(k3);
        assertTrue(tokenRels.isEmpty());
    }

    @Test
    void interiorRemovalWorksAsExpected() {
        initializeRels();

        final var relsListRemoval = new TokenRelsListMutation(accountNum.longValue(), tokenRels);

        final var k1 = removeFromMapValueList(bRelKey, aRelKey, relsListRemoval);
        assertEquals(aRelKey, k1);
        assertFalse(tokenRels.containsKey(bRelKey));
    }

    @Test
    void tailRemovalWorksAsExpected() {
        initializeRels();

        final var relsListRemoval = new TokenRelsListMutation(accountNum.longValue(), tokenRels);

        final var k1 = removeFromMapValueList(cRelKey, aRelKey, relsListRemoval);
        assertEquals(aRelKey, k1);
        assertFalse(tokenRels.containsKey(cRelKey));
    }

    @Test
    void unlinkingWorksWithGetForModify() {
        initializeRels();

        final var relsListRemoval = new TokenRelsListMutation(accountNum.longValue(), tokenRels);

        final var newRoot = unlinkInPlaceFromMapValueList(bRelKey, aRelKey, relsListRemoval);
        assertSame(aRelKey, newRoot);
        final var unlinkedValue = tokenRels.get(bRelKey);
        assertEquals(0, unlinkedValue.prevKey());
        assertEquals(0, unlinkedValue.nextKey());
        // and:
        final var newRootValue = tokenRels.get(aRelKey);
        assertEquals(c.longValue(), newRootValue.nextKey());
        // and:
        final var newTailValue = tokenRels.get(cRelKey);
        assertEquals(a.longValue(), newTailValue.prevKey());
        // and:
        assertTrue(tokenRels.containsKey(bRelKey));
    }

    @Test
    void unlinkingWorksWithOverwriting() {
        initializeRels();

        final var relsListRemoval = new TokenRelsListMutation(accountNum.longValue(), tokenRels);

        final var newRoot =
                internalDetachFromMapValueList(
                        bRelKey, aRelKey, relsListRemoval, false, false, true);
        assertSame(aRelKey, newRoot);
        final var unlinkedValue = tokenRels.get(bRelKey);
        assertEquals(0, unlinkedValue.prevKey());
        assertEquals(0, unlinkedValue.nextKey());
        // and:
        final var newRootValue = tokenRels.get(aRelKey);
        assertEquals(c.longValue(), newRootValue.nextKey());
        // and:
        final var newTailValue = tokenRels.get(cRelKey);
        assertEquals(a.longValue(), newTailValue.prevKey());
        // and:
        assertTrue(tokenRels.containsKey(bRelKey));
    }

    private void initializeRels() {
        aRel.setNext(b.longValue());
        tokenRels.put(aRelKey, aRel);
        bRel.setPrev(a.longValue());
        bRel.setNext(c.longValue());
        tokenRels.put(bRelKey, bRel);
        cRel.setPrev(b.longValue());
        tokenRels.put(cRelKey, cRel);
    }

    private static final EntityNum accountNum = EntityNum.fromLong(2);
    private static final EntityNum a = EntityNum.fromLong(4);
    private static final EntityNum b = EntityNum.fromLong(8);
    private static final EntityNum c = EntityNum.fromLong(16);
    private static final EntityNumPair aRelKey = EntityNumPair.fromNums(accountNum, a);
    private static final EntityNumPair bRelKey = EntityNumPair.fromNums(accountNum, b);
    private static final EntityNumPair cRelKey = EntityNumPair.fromNums(accountNum, c);
    private MerkleTokenRelStatus aRel = new MerkleTokenRelStatus(1L, true, false, true);
    private MerkleTokenRelStatus bRel = new MerkleTokenRelStatus(2L, true, false, true);
    private MerkleTokenRelStatus cRel = new MerkleTokenRelStatus(3L, true, false, true);
}
