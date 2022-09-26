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

import static com.hedera.services.state.migration.ReleaseThirtyMigration.rebuildNftOwners;
import static com.hedera.services.utils.NftNumPair.MISSING_NFT_NUM_PAIR;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;

import com.hedera.services.ServicesState;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.EntityNumPair;
import com.swirlds.common.constructable.ClassConstructorPair;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.merkle.utility.MerkleLong;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.merkle.tree.MerkleBinaryTree;
import com.swirlds.merkle.tree.MerkleTreeInternalNode;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReleaseThirtyMigrationTest {
    @Mock private ServicesState initializingState;

    @Test
    void grantsAutoRenewToNonDeletedContracts() throws ConstructableRegistryException {
        registerForMerkleMap();

        final var aKey = EntityNum.fromLong(1L);
        final var bKey = EntityNum.fromLong(2L);
        final var cKey = EntityNum.fromLong(3L);

        final var a = new MerkleAccount();
        a.setExpiry(1234L);
        a.setSmartContract(true);
        final var b = new MerkleAccount();
        b.setExpiry(2345L);
        b.setSmartContract(true);
        final var c = new MerkleAccount();
        c.setExpiry(3456L);
        c.setDeleted(true);
        c.setSmartContract(true);
        final var accountsMap = new MerkleMap<EntityNum, MerkleAccount>();
        accountsMap.put(aKey, a);
        accountsMap.put(bKey, b);
        accountsMap.put(cKey, c);
        final var instant = Instant.ofEpochSecond(123456789L);

        given(initializingState.accounts()).willReturn(accountsMap);

        ReleaseThirtyMigration.grantFreeAutoRenew(initializingState, instant);

        final var newAExpiry = accountsMap.get(aKey).getExpiry();
        final var newBExpiry = accountsMap.get(bKey).getExpiry();
        final var newCExpiry = accountsMap.get(cKey).getExpiry();
        assertTrue(newAExpiry > 1234L);
        assertTrue(newBExpiry > 2345L);
        assertEquals(3456L, newCExpiry);
    }

    @Test
    void migratesToIterableOwnedNftsAsExpected() {
        final MerkleMap<EntityNum, MerkleAccount> accounts = new MerkleMap<>();
        final MerkleMap<EntityNumPair, MerkleUniqueToken> uniqueTokens = new MerkleMap<>();

        final EntityNum accountNum1 = EntityNum.fromLong(1234L);
        final EntityNum accountNum2 = EntityNum.fromLong(1235L);
        final EntityNumPair nftId1 = EntityNumPair.fromLongs(2222, 1);
        final EntityNumPair nftId2 = EntityNumPair.fromLongs(2222, 2);
        final EntityNumPair nftId3 = EntityNumPair.fromLongs(2222, 3);
        final EntityNumPair nftId4 = EntityNumPair.fromLongs(2222, 4);
        final EntityNumPair nftId5 = EntityNumPair.fromLongs(2222, 5);
        final EntityNumPair nftId6 = EntityNumPair.fromLongs(666, 1);

        final MerkleAccount account1 = new MerkleAccount();
        account1.setHeadNftId(2222);
        account1.setHeadNftSerialNum(3);
        final MerkleAccount account2 = new MerkleAccount();
        account2.setHeadNftId(2222);
        account2.setHeadNftSerialNum(1);
        final MerkleUniqueToken nft1 = new MerkleUniqueToken();
        nft1.setOwner(accountNum1.toEntityId());
        nft1.setPrev(nftId2.asNftNumPair());
        nft1.setNext(nftId3.asNftNumPair());
        final MerkleUniqueToken nft2 = new MerkleUniqueToken();
        nft2.setOwner(accountNum2.toEntityId());
        final MerkleUniqueToken nft3 = new MerkleUniqueToken();
        nft3.setOwner(accountNum1.toEntityId());
        final MerkleUniqueToken nft4 = new MerkleUniqueToken();
        nft4.setOwner(accountNum2.toEntityId());
        nft4.setPrev(nftId2.asNftNumPair());
        nft4.setNext(nftId3.asNftNumPair());
        final MerkleUniqueToken nft5 = new MerkleUniqueToken();
        nft5.setOwner(accountNum1.toEntityId());
        final MerkleUniqueToken nft6 = new MerkleUniqueToken();
        nft6.setOwner(new EntityId(0L, 0L, 666L));

        accounts.put(accountNum1, account1);
        accounts.put(accountNum2, account2);
        uniqueTokens.put(nftId1, nft1);
        uniqueTokens.put(nftId2, nft2);
        uniqueTokens.put(nftId3, nft3);
        uniqueTokens.put(nftId4, nft4);
        uniqueTokens.put(nftId5, nft5);
        uniqueTokens.put(nftId6, nft6);

        rebuildNftOwners(accounts, uniqueTokens);
        // keySet() returns values in the order 2,5,4,1,3
        assertEquals(nftId3.getHiOrderAsLong(), accounts.get(accountNum1).getHeadNftTokenNum());
        assertEquals(nftId3.getLowOrderAsLong(), accounts.get(accountNum1).getHeadNftSerialNum());
        assertEquals(nftId4.getHiOrderAsLong(), accounts.get(accountNum2).getHeadNftTokenNum());
        assertEquals(nftId4.getLowOrderAsLong(), accounts.get(accountNum2).getHeadNftSerialNum());
        assertEquals(MISSING_NFT_NUM_PAIR, uniqueTokens.get(nftId5).getNext());
        assertEquals(nftId1.asNftNumPair(), uniqueTokens.get(nftId5).getPrev());
        assertEquals(nftId5.asNftNumPair(), uniqueTokens.get(nftId1).getNext());
        assertEquals(nftId3.asNftNumPair(), uniqueTokens.get(nftId1).getPrev());
        assertEquals(MISSING_NFT_NUM_PAIR, uniqueTokens.get(nftId3).getPrev());
        assertEquals(nftId1.asNftNumPair(), uniqueTokens.get(nftId3).getNext());
        assertEquals(MISSING_NFT_NUM_PAIR, uniqueTokens.get(nftId2).getNext());
        assertEquals(nftId4.asNftNumPair(), uniqueTokens.get(nftId2).getPrev());
        assertEquals(MISSING_NFT_NUM_PAIR, uniqueTokens.get(nftId4).getPrev());
        assertEquals(nftId2.asNftNumPair(), uniqueTokens.get(nftId4).getNext());
    }

    private static void registerForMerkleMap() throws ConstructableRegistryException {
        ConstructableRegistry.registerConstructable(
                new ClassConstructorPair(MerkleMap.class, MerkleMap::new));
        ConstructableRegistry.registerConstructable(
                new ClassConstructorPair(MerkleBinaryTree.class, MerkleBinaryTree::new));
        ConstructableRegistry.registerConstructable(
                new ClassConstructorPair(MerkleLong.class, MerkleLong::new));
        ConstructableRegistry.registerConstructable(
                new ClassConstructorPair(
                        MerkleTreeInternalNode.class, MerkleTreeInternalNode::new));
        ConstructableRegistry.registerConstructable(
                new ClassConstructorPair(MerkleAccount.class, MerkleAccount::new));
    }
}
