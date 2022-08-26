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

import static com.hedera.services.state.migration.ReleaseTwentySixMigration.INSERTIONS_PER_COPY;
import static com.hedera.services.state.migration.ReleaseTwentySixMigration.SEVEN_DAYS_IN_SECONDS;
import static com.hedera.services.state.migration.ReleaseTwentySixMigration.THREAD_COUNT;
import static com.hedera.services.state.migration.ReleaseTwentySixMigration.buildAccountNftsOwnedLinkedList;
import static com.hedera.services.state.migration.ReleaseTwentySixMigration.grantFreeAutoRenew;
import static com.hedera.services.state.migration.ReleaseTwentySixMigration.makeStorageIterable;
import static com.hedera.services.txns.crypto.AutoCreationLogic.THREE_MONTHS_IN_SECONDS;
import static com.hedera.services.utils.NftNumPair.MISSING_NFT_NUM_PAIR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.hedera.services.ServicesState;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.state.virtual.ContractKey;
import com.hedera.services.state.virtual.ContractValue;
import com.hedera.services.state.virtual.IterableContractValue;
import com.hedera.services.store.contracts.SizeLimitedStorage;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.EntityNumPair;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.platform.RandomExtended;
import com.swirlds.virtualmap.VirtualMap;
import java.time.Instant;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReleaseTwentySixMigrationTest {
    @Mock private MerkleMap<EntityNum, MerkleAccount> accounts;
    @Mock private VirtualMap<ContractKey, ContractValue> contractStorage;
    @Mock private VirtualMap<ContractKey, IterableContractValue> iterableContractStorage;
    @Mock private VirtualMap<ContractKey, IterableContractValue> finalContractStorage;
    @Mock private ServicesState initializingState;
    @Mock private KvPairIterationMigrator migrator;
    @Mock private ReleaseTwentySixMigration.MigratorFactory migratorFactory;
    @Mock private ReleaseTwentySixMigration.MigrationUtility migrationUtility;
    @Mock private MerkleAccount merkleAccount;

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

        final MerkleAccount account1 = new MerkleAccount();
        final MerkleAccount account2 = new MerkleAccount();
        final MerkleUniqueToken nft1 = new MerkleUniqueToken();
        nft1.setOwner(accountNum1.toEntityId());
        final MerkleUniqueToken nft2 = new MerkleUniqueToken();
        nft2.setOwner(accountNum2.toEntityId());
        final MerkleUniqueToken nft3 = new MerkleUniqueToken();
        nft3.setOwner(accountNum1.toEntityId());
        final MerkleUniqueToken nft4 = new MerkleUniqueToken();
        nft4.setOwner(accountNum2.toEntityId());
        final MerkleUniqueToken nft5 = new MerkleUniqueToken();
        nft5.setOwner(accountNum1.toEntityId());

        accounts.put(accountNum1, account1);
        accounts.put(accountNum2, account2);
        uniqueTokens.put(nftId1, nft1);
        uniqueTokens.put(nftId2, nft2);
        uniqueTokens.put(nftId3, nft3);
        uniqueTokens.put(nftId4, nft4);
        uniqueTokens.put(nftId5, nft5);

        buildAccountNftsOwnedLinkedList(accounts, uniqueTokens);
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

    @Test
    void migratesToIterableStorageAsExpected() throws InterruptedException {
        given(initializingState.accounts()).willReturn(accounts);
        given(initializingState.getChild(StateChildIndices.CONTRACT_STORAGE))
                .willReturn(contractStorage);
        given(
                        migratorFactory.from(
                                eq(INSERTIONS_PER_COPY),
                                eq(accounts),
                                any(SizeLimitedStorage.IterableStorageUpserter.class),
                                eq(iterableContractStorage)))
                .willReturn(migrator);
        given(migrator.getMigratedStorage()).willReturn(finalContractStorage);

        makeStorageIterable(
                initializingState, migratorFactory, migrationUtility, iterableContractStorage);

        verify(migrationUtility).extractVirtualMapData(contractStorage, migrator, THREAD_COUNT);
        verify(migrator).finish();
        verify(initializingState)
                .setChild(StateChildIndices.CONTRACT_STORAGE, finalContractStorage);
    }

    @Test
    void translatesInterruptedExceptionToIse() throws InterruptedException {
        given(initializingState.accounts()).willReturn(accounts);
        given(initializingState.getChild(StateChildIndices.CONTRACT_STORAGE))
                .willReturn(contractStorage);
        given(
                        migratorFactory.from(
                                eq(INSERTIONS_PER_COPY),
                                eq(accounts),
                                any(SizeLimitedStorage.IterableStorageUpserter.class),
                                eq(iterableContractStorage)))
                .willReturn(migrator);
        willThrow(InterruptedException.class)
                .given(migrationUtility)
                .extractVirtualMapData(contractStorage, migrator, THREAD_COUNT);

        Assertions.assertThrows(
                IllegalStateException.class,
                () ->
                        makeStorageIterable(
                                initializingState,
                                migratorFactory,
                                migrationUtility,
                                iterableContractStorage));
    }

    @Test
    void grantsAutoRenewToContracts() {
        final var accountsMap = new MerkleMap<EntityNum, MerkleAccount>();
        accountsMap.put(EntityNum.fromLong(1L), merkleAccount);
        accountsMap.put(EntityNum.fromLong(2L), merkleAccount);
        final var instant = Instant.ofEpochSecond(123456789L);

        final var rand = new RandomExtended(8682588012L);

        given(initializingState.accounts()).willReturn(accountsMap);
        given(merkleAccount.isSmartContract()).willReturn(true);
        given(merkleAccount.getExpiry()).willReturn(1234L).willReturn(2345L);

        grantFreeAutoRenew(initializingState, instant);

        final var expectedExpiry1 = getExpectedExpiry(1234L, instant.getEpochSecond(), rand);
        final var expectedExpiry2 = getExpectedExpiry(2345L, instant.getEpochSecond(), rand);

        verify(merkleAccount, times(2)).isSmartContract();
        verify(merkleAccount).setExpiry(expectedExpiry1);
        verify(merkleAccount).setExpiry(expectedExpiry2);
    }

    private long getExpectedExpiry(
            final long currentExpiry, final long instant, final RandomExtended rand) {
        return Math.max(
                currentExpiry,
                instant + THREE_MONTHS_IN_SECONDS + rand.nextLong(0, SEVEN_DAYS_IN_SECONDS));
    }
}
