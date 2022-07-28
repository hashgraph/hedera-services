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

import static com.hedera.services.state.migration.StorageLinksFixer.THREAD_COUNT;
import static com.hedera.services.state.migration.StorageLinksFixer.fixAnyBrokenLinks;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.hedera.services.ServicesState;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.virtual.ContractKey;
import com.hedera.services.state.virtual.IterableContractValue;
import com.hedera.services.utils.EntityNum;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.virtualmap.VirtualMap;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StorageLinksFixerTest {
    @Mock private MerkleMap<EntityNum, MerkleAccount> accounts;
    @Mock private VirtualMap<ContractKey, IterableContractValue> storage;
    @Mock private ServicesState initializingState;
    @Mock private LinkRepairs linkRepairs;
    @Mock private StorageLinksFixer.LinkRepairsFactory repairsFactory;
    @Mock private VirtualMapDataAccess<ContractKey, IterableContractValue> dataAccess;

    @Test
    void justScansForBrokenLinksIfNoneExist() throws InterruptedException {
        given(initializingState.accounts()).willReturn(accounts);
        given(initializingState.getChild(StateChildIndices.CONTRACT_STORAGE)).willReturn(storage);
        given(repairsFactory.from(accounts, storage)).willReturn(linkRepairs);

        fixAnyBrokenLinks(initializingState, repairsFactory, dataAccess);

        verify(linkRepairs).markScanComplete();
        verify(dataAccess).extractVirtualMapData(storage, linkRepairs, THREAD_COUNT);
        verify(linkRepairs).hasBrokenLinks();
    }

    @Test
    void rescansAndFixesBrokenLinksIfExist() throws InterruptedException {
        given(initializingState.accounts()).willReturn(accounts);
        given(initializingState.getChild(StateChildIndices.CONTRACT_STORAGE)).willReturn(storage);
        given(repairsFactory.from(accounts, storage)).willReturn(linkRepairs);
        given(linkRepairs.hasBrokenLinks()).willReturn(true);

        fixAnyBrokenLinks(initializingState, repairsFactory, dataAccess);

        verify(dataAccess, times(2)).extractVirtualMapData(storage, linkRepairs, THREAD_COUNT);
        verify(linkRepairs).markScanComplete();
        verify(linkRepairs).hasBrokenLinks();
        verify(linkRepairs).fixAnyBrokenLinks();
    }

    @Test
    void translatesInterruptedExceptionToIse() throws InterruptedException {
        given(initializingState.accounts()).willReturn(accounts);
        given(initializingState.getChild(StateChildIndices.CONTRACT_STORAGE)).willReturn(storage);
        given(repairsFactory.from(accounts, storage)).willReturn(linkRepairs);
        willThrow(InterruptedException.class)
                .given(dataAccess)
                .extractVirtualMapData(storage, linkRepairs, THREAD_COUNT);

        Assertions.assertThrows(
                IllegalStateException.class,
                () -> fixAnyBrokenLinks(initializingState, repairsFactory, dataAccess));
    }
}
