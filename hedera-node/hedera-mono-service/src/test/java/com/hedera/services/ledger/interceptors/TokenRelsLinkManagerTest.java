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
package com.hedera.services.ledger.interceptors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.migration.AccountStorageAdapter;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.EntityNumPair;
import com.swirlds.merkle.map.MerkleMap;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TokenRelsLinkManagerTest {
    private MerkleMap<EntityNum, MerkleAccount> accounts = new MerkleMap<>();
    private MerkleMap<EntityNumPair, MerkleTokenRelStatus> tokenRels = new MerkleMap<>();

    private TokenRelsLinkManager subject;

    @BeforeEach
    void setUp() {
        subject =
                new TokenRelsLinkManager(
                        () -> AccountStorageAdapter.fromInMemory(accounts), () -> tokenRels);
    }

    @Test
    void updatesToNonEmptyListAsExpected() {
        setupMaps();

        eRel.setKey(EntityNumPair.fromNums(accountNum, e));
        dRel.setKey(EntityNumPair.fromNums(accountNum, d));
        subject.updateLinks(accountNum, List.of(c, a), List.of(eRel, dRel));

        final var updatedAccount = accounts.get(accountNum);
        assertEquals(d.longValue(), updatedAccount.getHeadTokenId());

        final var updatedRoot = tokenRels.get(dRelKey);
        assertNotNull(updatedRoot);
        assertEquals(eRelKey.getLowOrderAsLong(), updatedRoot.nextKey());
        assertEquals(0, updatedRoot.prevKey());
        final var updatedTail = tokenRels.get(bRelKey);
        assertNotNull(updatedTail);
        assertEquals(eRelKey.getLowOrderAsLong(), updatedTail.prevKey());
        assertEquals(0, updatedTail.nextKey());
        final var updatedMiddle = tokenRels.get(eRelKey);
        assertNotNull(updatedMiddle);
        assertEquals(dRelKey.getLowOrderAsLong(), updatedMiddle.prevKey());
        assertEquals(bRelKey.getLowOrderAsLong(), updatedMiddle.nextKey());
    }

    @Test
    void justDissociatesAsExpected() {
        setupMaps();

        subject.updateLinks(accountNum, List.of(a), null);

        final var updatedAccount = accounts.get(accountNum);
        assertEquals(bRelKey.getLowOrderAsLong(), updatedAccount.getHeadTokenId());
    }

    @Test
    void justAssociatesAsExpected() {
        setupMaps();
        eRel.setKey(EntityNumPair.fromNums(accountNum, e));

        subject.updateLinks(accountNum, null, List.of(eRel));

        final var updatedAccount = accounts.get(accountNum);
        assertEquals(eRelKey.getLowOrderAsLong(), updatedAccount.getHeadTokenId());
    }

    @Test
    void updatesToEmptyListAsExpected() {
        setupMaps();

        subject.updateLinks(accountNum, List.of(c, a, b), List.of());

        final var updatedAccount = accounts.get(accountNum);
        assertEquals(0, updatedAccount.getHeadTokenId());
        assertTrue(tokenRels.isEmpty());
    }

    private void setupMaps() {
        account.setHeadTokenId(aRelKey.getLowOrderAsLong());
        accounts.put(accountNum, account);
        cRel.setPrev(bRelKey.getLowOrderAsLong());
        bRel.setPrev(aRelKey.getLowOrderAsLong());
        bRel.setNext(cRelKey.getLowOrderAsLong());
        aRel.setNext(bRelKey.getLowOrderAsLong());
        tokenRels.put(aRelKey, aRel);
        tokenRels.put(bRelKey, bRel);
        tokenRels.put(cRelKey, cRel);
    }

    private static final EntityNum accountNum = EntityNum.fromLong(2);
    private static final EntityNum a = EntityNum.fromLong(4);
    private static final EntityNum b = EntityNum.fromLong(8);
    private static final EntityNum c = EntityNum.fromLong(16);
    private static final EntityNum d = EntityNum.fromLong(32);
    private static final EntityNum e = EntityNum.fromLong(64);
    private static final EntityNumPair aRelKey = EntityNumPair.fromNums(accountNum, a);
    private static final EntityNumPair bRelKey = EntityNumPair.fromNums(accountNum, b);
    private static final EntityNumPair cRelKey = EntityNumPair.fromNums(accountNum, c);
    private static final EntityNumPair dRelKey = EntityNumPair.fromNums(accountNum, d);
    private static final EntityNumPair eRelKey = EntityNumPair.fromNums(accountNum, e);
    private MerkleTokenRelStatus aRel = new MerkleTokenRelStatus(1L, true, false, true);
    private MerkleTokenRelStatus bRel = new MerkleTokenRelStatus(2L, true, false, true);
    private MerkleTokenRelStatus cRel = new MerkleTokenRelStatus(3L, true, false, true);
    private MerkleTokenRelStatus dRel = new MerkleTokenRelStatus(4L, false, true, false);
    private MerkleTokenRelStatus eRel = new MerkleTokenRelStatus(5L, false, true, false);
    private MerkleAccount account = new MerkleAccount();
}
