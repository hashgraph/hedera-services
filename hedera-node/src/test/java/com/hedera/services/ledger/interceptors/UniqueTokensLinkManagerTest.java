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

import static com.hedera.services.utils.NftNumPair.MISSING_NFT_NUM_PAIR;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.EntityNumPair;
import com.hedera.services.utils.NftNumPair;
import com.hedera.test.extensions.LogCaptor;
import com.hedera.test.extensions.LogCaptureExtension;
import com.hedera.test.extensions.LoggingSubject;
import com.hedera.test.extensions.LoggingTarget;
import com.swirlds.merkle.map.MerkleMap;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(LogCaptureExtension.class)
class UniqueTokensLinkManagerTest {
    private final MerkleMap<EntityNum, MerkleAccount> accounts = new MerkleMap<>();
    private final MerkleMap<EntityNum, MerkleToken> tokens = new MerkleMap<>();
    private final MerkleMap<EntityNumPair, MerkleUniqueToken> uniqueTokens = new MerkleMap<>();

    @LoggingTarget private LogCaptor logCaptor;
    @LoggingSubject private UniqueTokensLinkManager subject;

    @BeforeEach
    void setUp() {
        subject = new UniqueTokensLinkManager(() -> accounts, () -> tokens, () -> uniqueTokens);
    }

    @Test
    void logsAtErrorIfOwnerHasNoHeadLink() {
        setUpEntities();
        setUpMaps();
        oldOwnerAccount.setHeadNftId(0);
        oldOwnerAccount.setHeadNftSerialNum(0);

        subject.updateLinks(oldOwner, newOwner, nftKey1);

        assertThat(logCaptor.errorLogs(), contains(Matchers.startsWith("Invariant failure")));
    }

    @Test
    void updatesOldOwnersHeadAsExpected() {
        setUpEntities();
        setUpMaps();

        assertEquals(tokenNum, accounts.get(oldOwner).getHeadNftTokenNum());
        assertEquals(serialNum1, accounts.get(oldOwner).getHeadNftSerialNum());

        subject.updateLinks(oldOwner, newOwner, nftKey1);

        assertEquals(MISSING_NFT_NUM_PAIR, uniqueTokens.get(nftKey2).getPrev());
        assertEquals(MISSING_NFT_NUM_PAIR, uniqueTokens.get(nftKey1).getNext());
        assertEquals(tokenNum, accounts.get(newOwner).getHeadNftTokenNum());
        assertEquals(serialNum1, accounts.get(newOwner).getHeadNftSerialNum());
        assertEquals(tokenNum, accounts.get(oldOwner).getHeadNftTokenNum());
        assertEquals(serialNum2, accounts.get(oldOwner).getHeadNftSerialNum());
    }

    @Test
    void updatesLinksAsExpected() {
        setUpEntities();
        setUpMaps();

        assertEquals(tokenNum, accounts.get(oldOwner).getHeadNftTokenNum());
        assertEquals(serialNum1, accounts.get(oldOwner).getHeadNftSerialNum());

        subject.updateLinks(oldOwner, newOwner, nftKey2);

        assertEquals(nftNumPair1, uniqueTokens.get(nftKey3).getPrev());
        assertEquals(nftNumPair3, uniqueTokens.get(nftKey1).getNext());
        assertEquals(tokenNum, accounts.get(newOwner).getHeadNftTokenNum());
        assertEquals(serialNum2, accounts.get(newOwner).getHeadNftSerialNum());
        assertEquals(tokenNum, accounts.get(oldOwner).getHeadNftTokenNum());
        assertEquals(serialNum1, accounts.get(oldOwner).getHeadNftSerialNum());
    }

    @Test
    void fromTreasuryDoesntUpdateTreasuryAccountLinks() {
        newOwnerAccount.setHeadNftId(tokenNum);
        newOwnerAccount.setHeadNftSerialNum(2L);
        nftToken.setTreasury(treasury.toEntityId());
        nft1.setNext(MISSING_NFT_NUM_PAIR);
        nft1.setPrev(MISSING_NFT_NUM_PAIR);
        nft2.setNext(MISSING_NFT_NUM_PAIR);
        nft2.setPrev(MISSING_NFT_NUM_PAIR);
        setUpMaps();

        assertDoesNotThrow(() -> subject.updateLinks(treasury, newOwner, nftKey1));

        assertEquals(tokenNum, accounts.get(newOwner).getHeadNftTokenNum());
        assertEquals(serialNum1, accounts.get(newOwner).getHeadNftSerialNum());
        assertEquals(nftNumPair1, uniqueTokens.get(nftKey2).getPrev());
        assertEquals(nftNumPair2, uniqueTokens.get(nftKey1).getNext());
    }

    @Test
    void toTreasuryDoesntUpdateTreasuryAccountLinks() {
        setUpEntities();
        setUpMaps();

        assertDoesNotThrow(() -> subject.updateLinks(oldOwner, treasury, nftKey2));

        assertEquals(nftNumPair1, uniqueTokens.get(nftKey3).getPrev());
        assertEquals(nftNumPair3, uniqueTokens.get(nftKey1).getNext());
        assertEquals(tokenNum, accounts.get(oldOwner).getHeadNftTokenNum());
        assertEquals(serialNum1, accounts.get(oldOwner).getHeadNftSerialNum());
    }

    @Test
    void multiStageNonTreasuryMintAlsoCreatesLinks() {
        nftToken.setTreasury(treasury.toEntityId());
        tokens.put(token, nftToken);
        accounts.put(newOwner, newOwnerAccount);
        newOwnerAccount.setHeadNftId(tokenNum);
        newOwnerAccount.setHeadNftSerialNum(serialNum1);
        uniqueTokens.put(nftKey1, nft1);

        final var mintedNft = subject.updateLinks(null, newOwner, nftKey2);

        final var updatedNft1 = uniqueTokens.get(nftKey1);
        assertEquals(nftNumPair2, updatedNft1.getPrev());
        final var updatedNft2 = uniqueTokens.get(nftKey2);
        assertSame(updatedNft2, mintedNft);
        assertEquals(nftNumPair1, updatedNft2.getNext());
        final var updatedOwner = accounts.get(newOwner);
        assertEquals(tokenNum, updatedOwner.getHeadNftTokenNum());
        assertEquals(serialNum2, updatedOwner.getHeadNftSerialNum());
    }

    void setUpEntities() {
        oldOwnerAccount.setHeadNftId(tokenNum);
        oldOwnerAccount.setHeadNftSerialNum(serialNum1);
        nftToken.setTreasury(treasury.toEntityId());
        nft1.setPrev(MISSING_NFT_NUM_PAIR);
        nft1.setNext(nftNumPair2);
        nft2.setPrev(nftNumPair1);
        nft2.setNext(nftNumPair3);
        nft3.setPrev(nftNumPair2);
        nft3.setNext(MISSING_NFT_NUM_PAIR);
    }

    void setUpMaps() {
        accounts.put(oldOwner, oldOwnerAccount);
        accounts.put(newOwner, newOwnerAccount);
        tokens.put(token, nftToken);
        uniqueTokens.put(nftKey1, nft1);
        uniqueTokens.put(nftKey2, nft2);
        uniqueTokens.put(nftKey3, nft3);
    }

    final long oldOwnerNum = 1234L;
    final long newOwnerNum = 1235L;
    final long treasuryNum = 1236L;
    final long tokenNum = 1237L;
    final long serialNum1 = 1L;
    final long serialNum2 = 2L;
    final long serialNum3 = 3L;
    final EntityNum oldOwner = EntityNum.fromLong(oldOwnerNum);
    final EntityNum newOwner = EntityNum.fromLong(newOwnerNum);
    final EntityNum treasury = EntityNum.fromLong(treasuryNum);
    final EntityNum token = EntityNum.fromLong(tokenNum);
    final EntityNumPair nftKey1 = EntityNumPair.fromLongs(tokenNum, serialNum1);
    final EntityNumPair nftKey2 = EntityNumPair.fromLongs(tokenNum, serialNum2);
    final EntityNumPair nftKey3 = EntityNumPair.fromLongs(tokenNum, serialNum3);
    final NftNumPair nftNumPair1 = nftKey1.asNftNumPair();
    final NftNumPair nftNumPair2 = nftKey2.asNftNumPair();
    final NftNumPair nftNumPair3 = nftKey3.asNftNumPair();
    private MerkleAccount oldOwnerAccount = new MerkleAccount();
    private MerkleAccount newOwnerAccount = new MerkleAccount();
    private MerkleToken nftToken = new MerkleToken();
    private MerkleUniqueToken nft1 = new MerkleUniqueToken();
    private MerkleUniqueToken nft2 = new MerkleUniqueToken();
    private MerkleUniqueToken nft3 = new MerkleUniqueToken();
}
