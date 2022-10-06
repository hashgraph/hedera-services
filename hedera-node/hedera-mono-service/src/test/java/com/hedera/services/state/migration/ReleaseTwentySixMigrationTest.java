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
import static com.hedera.services.state.migration.ReleaseTwentySixMigration.THREAD_COUNT;
import static com.hedera.services.state.migration.ReleaseTwentySixMigration.makeStorageIterable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

import com.hedera.services.ServicesState;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.virtual.ContractKey;
import com.hedera.services.state.virtual.ContractValue;
import com.hedera.services.state.virtual.IterableContractValue;
import com.hedera.services.store.contracts.SizeLimitedStorage;
import com.hedera.services.utils.EntityNum;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.virtualmap.VirtualMap;
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
}
