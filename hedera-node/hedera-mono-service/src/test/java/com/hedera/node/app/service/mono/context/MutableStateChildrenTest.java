/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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
package com.hedera.node.app.service.mono.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;

import com.google.protobuf.ByteString;
import com.hedera.node.app.service.mono.ServicesState;
import com.hedera.node.app.service.mono.state.adapters.MerkleMapLike;
import com.hedera.node.app.service.mono.state.adapters.VirtualMapLike;
import com.hedera.node.app.service.mono.state.merkle.MerkleNetworkContext;
import com.hedera.node.app.service.mono.state.merkle.MerkleScheduledTransactions;
import com.hedera.node.app.service.mono.state.merkle.MerkleSpecialFiles;
import com.hedera.node.app.service.mono.state.merkle.MerkleStakingInfo;
import com.hedera.node.app.service.mono.state.merkle.MerkleToken;
import com.hedera.node.app.service.mono.state.merkle.MerkleTopic;
import com.hedera.node.app.service.mono.state.migration.AccountStorageAdapter;
import com.hedera.node.app.service.mono.state.migration.TokenRelStorageAdapter;
import com.hedera.node.app.service.mono.state.migration.UniqueTokenMapAdapter;
import com.hedera.node.app.service.mono.state.virtual.ContractKey;
import com.hedera.node.app.service.mono.state.virtual.IterableContractValue;
import com.hedera.node.app.service.mono.state.virtual.VirtualBlobKey;
import com.hedera.node.app.service.mono.state.virtual.VirtualBlobValue;
import com.hedera.node.app.service.mono.stream.RecordsRunningHashLeaf;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.fchashmap.FCHashMap;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.virtualmap.VirtualMap;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MutableStateChildrenTest {
    @Mock private ServicesState state;
    @Mock private AccountStorageAdapter accounts;
    @Mock private VirtualMap<VirtualBlobKey, VirtualBlobValue> storage;
    @Mock private VirtualMap<ContractKey, IterableContractValue> contractStorage;
    @Mock private MerkleMap<EntityNum, MerkleTopic> topics;
    @Mock private MerkleMap<EntityNum, MerkleToken> tokens;
    @Mock private TokenRelStorageAdapter tokenAssociations;
    @Mock private MerkleScheduledTransactions scheduleTxs;
    @Mock private MerkleNetworkContext networkCtx;
    @Mock private AddressBook addressBook;
    @Mock private MerkleSpecialFiles specialFiles;
    @Mock private UniqueTokenMapAdapter uniqueTokens;
    @Mock private RecordsRunningHashLeaf runningHashLeaf;
    @Mock private FCHashMap<ByteString, EntityNum> aliases;
    @Mock private MerkleMap<EntityNum, MerkleStakingInfo> stakingInfo;

    private final MutableStateChildren subject = new MutableStateChildren();

    @Test
    void refusesToUpdateFromUninitializedState() {
        assertThrows(
                IllegalArgumentException.class, () -> subject.updateFromImmutable(state, signedAt));
    }

    @Test
    void childrenGetUpdatedAsExpected() {
        givenStateWithMockChildren();
        given(state.isInitialized()).willReturn(true);

        subject.updateFromImmutable(state, signedAt);

        assertChildrenAreExpectedMocks();
        assertEquals(signedAt, subject.signedAt());
    }

    @Test
    void getsSizes() {
        givenStateWithMockChildren();
        given(state.isInitialized()).willReturn(true);
        subject.updateFromImmutable(state, signedAt);

        given(accounts.size()).willReturn(1L);
        given(storage.size()).willReturn(2L);
        given(contractStorage.size()).willReturn(3L);
        given(scheduleTxs.getNumSchedules()).willReturn(4L);
        given(tokens.size()).willReturn(5);
        given(tokenAssociations.size()).willReturn(6L);
        given(topics.size()).willReturn(7);
        given(uniqueTokens.size()).willReturn(8L);

        assertEquals(1L, subject.numAccountAndContracts());
        assertEquals(2L, subject.numBlobs());
        assertEquals(3L, subject.numStorageSlots());
        assertEquals(4L, subject.numSchedules());
        assertEquals(5L, subject.numTokens());
        assertEquals(6L, subject.numTokenRels());
        assertEquals(7L, subject.numTopics());
        assertEquals(8L, subject.numNfts());
    }

    private void givenStateWithMockChildren() {
        given(state.accounts()).willReturn(accounts);
        given(state.storage()).willReturn(VirtualMapLike.from(storage));
        given(state.contractStorage()).willReturn(VirtualMapLike.from(contractStorage));
        given(state.topics()).willReturn(MerkleMapLike.from(topics));
        given(state.tokens()).willReturn(MerkleMapLike.from(tokens));
        given(state.tokenAssociations()).willReturn(tokenAssociations);
        given(state.scheduleTxs()).willReturn(scheduleTxs);
        given(state.networkCtx()).willReturn(networkCtx);
        given(state.addressBook()).willReturn(addressBook);
        given(state.specialFiles()).willReturn(specialFiles);
        given(state.uniqueTokens()).willReturn(uniqueTokens);
        given(state.runningHashLeaf()).willReturn(runningHashLeaf);
        given(state.aliases()).willReturn(aliases);
        given(state.stakingInfo()).willReturn(MerkleMapLike.from(stakingInfo));
    }

    private void assertChildrenAreExpectedMocks() {
        assertSame(accounts, subject.accounts());
        assertSame(storage, subject.storage());
        assertSame(contractStorage, subject.contractStorage());
        assertSame(topics, subject.topics());
        assertSame(tokens, subject.tokens());
        assertSame(tokenAssociations, subject.tokenAssociations());
        assertSame(scheduleTxs, subject.schedules());
        assertSame(networkCtx, subject.networkCtx());
        assertSame(addressBook, subject.addressBook());
        assertSame(specialFiles, subject.specialFiles());
        assertSame(uniqueTokens, subject.uniqueTokens());
        assertSame(runningHashLeaf, subject.runningHashLeaf());
        assertSame(aliases, subject.aliases());
        assertSame(stakingInfo, subject.stakingInfo());
    }

    private static final Instant signedAt = Instant.ofEpochSecond(1_234_567, 890);
}
