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

import static com.hedera.services.utils.MapValueListUtils.insertInPlaceAtMapValueListHead;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.hedera.services.state.expiry.TokenRelsListMutation;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.swirlds.merkle.map.MerkleMap;
import org.junit.jupiter.api.Test;

class InPlaceHeadInsertionTest {
    private MerkleMap<EntityNumPair, MerkleTokenRelStatus> tokenRels = new MerkleMap<>();

    @Test
    void canInsertToEmptyList() {
        final var listInsertion = new TokenRelsListMutation(accountNum.longValue(), tokenRels);

        final var newRoot =
                insertInPlaceAtMapValueListHead(aRelKey, aRel, null, null, listInsertion);

        assertSame(aRelKey, newRoot);
        assertSame(aRel, tokenRels.get(aRelKey));
    }

    @Test
    void canInsertToNonEmptyListWithNullValue() {
        tokenRels.put(bRelKey, bRel);
        final var listInsertion = new TokenRelsListMutation(accountNum.longValue(), tokenRels);

        final var newRoot =
                insertInPlaceAtMapValueListHead(aRelKey, aRel, bRelKey, null, listInsertion);

        assertSame(aRelKey, newRoot);
        final var newRootValue = tokenRels.get(aRelKey);
        assertEquals(bRelKey.getLowOrderAsLong(), newRootValue.nextKey());
        final var newNextValue = tokenRels.get(bRelKey);
        assertEquals(aRelKey.getLowOrderAsLong(), newNextValue.prevKey());
    }

    @Test
    void canInsertToNonEmptyListWithNonNullValue() {
        tokenRels.put(bRelKey, bRel);
        final var listInsertion = new TokenRelsListMutation(accountNum.longValue(), tokenRels);

        final var newRoot =
                insertInPlaceAtMapValueListHead(aRelKey, aRel, bRelKey, bRel, listInsertion);

        assertSame(aRelKey, newRoot);
        final var newRootValue = tokenRels.get(aRelKey);
        assertEquals(bRelKey.getLowOrderAsLong(), newRootValue.nextKey());
        final var newNextValue = tokenRels.get(bRelKey);
        assertEquals(aRelKey.getLowOrderAsLong(), newNextValue.prevKey());
    }

    private static final EntityNum accountNum = EntityNum.fromLong(2);
    private static final EntityNum a = EntityNum.fromLong(4);
    private static final EntityNum b = EntityNum.fromLong(8);
    private static final EntityNum c = EntityNum.fromLong(16);
    private static final EntityNumPair aRelKey = EntityNumPair.fromNums(accountNum, a);
    private static final EntityNumPair bRelKey = EntityNumPair.fromNums(accountNum, b);
    private MerkleTokenRelStatus aRel = new MerkleTokenRelStatus(1L, true, false, true);
    private MerkleTokenRelStatus bRel = new MerkleTokenRelStatus(2L, true, false, true);
}
