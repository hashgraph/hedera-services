/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.node.app.service.mono.state.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;

import com.google.protobuf.ByteString;
import com.hedera.node.app.service.mono.ServicesState;
import com.hedera.node.app.service.mono.state.merkle.MerkleAccount;
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
import com.hedera.node.app.service.mono.state.virtual.EntityNumVirtualKey;
import com.hedera.node.app.service.mono.state.virtual.IterableContractValue;
import com.hedera.node.app.service.mono.state.virtual.VirtualBlobKey;
import com.hedera.node.app.service.mono.state.virtual.VirtualBlobValue;
import com.hedera.node.app.service.mono.state.virtual.entities.OnDiskAccount;
import com.hedera.node.app.service.mono.state.virtual.schedule.ScheduleEqualityVirtualKey;
import com.hedera.node.app.service.mono.state.virtual.schedule.ScheduleEqualityVirtualValue;
import com.hedera.node.app.service.mono.state.virtual.schedule.ScheduleSecondVirtualValue;
import com.hedera.node.app.service.mono.state.virtual.schedule.ScheduleVirtualValue;
import com.hedera.node.app.service.mono.state.virtual.temporal.SecondSinceEpocVirtualKey;
import com.hedera.node.app.service.mono.stream.RecordsRunningHashLeaf;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.fchashmap.FCHashMap;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.virtualmap.VirtualMap;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StatesImplTest {
    private static final String ACCOUNTS = "ACCOUNTS";
    private static final String ALIASES = "ALIASES";
    private static final String TOKENS = "TOKENS";
    private static final String SCHEDULES_BY_ID = "SCHEDULES-BY-ID";
    private static final String SCHEDULES_BY_EQUALITY = "SCHEDULES-BY-EQUALITY";
    private static final String SCHEDULES_BY_EXPIRY = "SCHEDULES-BY-EXPIRY";
    @Mock private ServicesState state;
    @Mock private MerkleMap<EntityNum, MerkleAccount> inMemoryAccounts;
    @Mock private VirtualMap<EntityNumVirtualKey, OnDiskAccount> onDiskAccounts;
    @Mock private AccountStorageAdapter accountsAdapter;
    @Mock private VirtualMap<VirtualBlobKey, VirtualBlobValue> storage;
    @Mock private VirtualMap<ContractKey, IterableContractValue> contractStorage;
    @Mock private MerkleMap<EntityNum, MerkleTopic> topics;
    @Mock private MerkleMap<EntityNum, MerkleToken> tokens;
    @Mock private MerkleMap<EntityNumVirtualKey, ScheduleVirtualValue> scheduleById;
    @Mock private MerkleMap<SecondSinceEpocVirtualKey, ScheduleSecondVirtualValue> scheduleByExpiry;

    @Mock
    private MerkleMap<ScheduleEqualityVirtualKey, ScheduleEqualityVirtualValue> scheduleByEquality;

    @Mock private TokenRelStorageAdapter tokenAssociations;
    @Mock private MerkleScheduledTransactions scheduleTxs;
    @Mock private MerkleNetworkContext networkCtx;
    @Mock private AddressBook addressBook;
    @Mock private MerkleSpecialFiles specialFiles;
    @Mock private UniqueTokenMapAdapter uniqueTokens;
    @Mock private RecordsRunningHashLeaf runningHashLeaf;
    @Mock private FCHashMap<ByteString, EntityNum> aliases;
    @Mock private MerkleMap<EntityNum, MerkleStakingInfo> stakingInfo;

    private StatesImpl subject;

    @BeforeEach
    void setUp() {
        subject = new StatesImpl();
    }

    @Test
    void updatesChildrenFromImmutableState() {
        final var lastHandledTime = Instant.ofEpochSecond(1_234_567L);
        givenStateWithMockChildren();
        given(scheduleTxs.byId()).willReturn(scheduleById);
        given(scheduleTxs.byEquality()).willReturn(scheduleByEquality);
        given(scheduleTxs.byExpirationSecond()).willReturn(scheduleByExpiry);

        given(state.getTimeOfLastHandledTxn()).willReturn(lastHandledTime);
        given(state.isInitialized()).willReturn(true);

        subject.updateChildren(state);
        assertChildrenAreExpectedMocks();
        assertEquals(lastHandledTime, subject.getChildren().signedAt());
    }

    @Test
    void returnsInMemoryAccountsWhenAppropriate() {
        final var lastHandledTime = Instant.ofEpochSecond(1_234_567L);
        givenStateWithMockChildren();
        given(state.getTimeOfLastHandledTxn()).willReturn(lastHandledTime);
        given(state.isInitialized()).willReturn(true);
        given(accountsAdapter.getInMemoryAccounts()).willReturn(inMemoryAccounts);

        subject.updateChildren(state);

        final var state = subject.get(ACCOUNTS);

        assertEquals(lastHandledTime, state.getLastModifiedTime());
        assertTrue(state instanceof InMemoryStateImpl);
    }

    @Test
    void returnsAliasesFromChildren() {
        final var lastHandledTime = Instant.ofEpochSecond(1_234_567L);
        givenStateWithMockChildren();
        given(state.getTimeOfLastHandledTxn()).willReturn(lastHandledTime);
        given(state.isInitialized()).willReturn(true);

        subject.updateChildren(state);

        final var state = subject.get(ALIASES);

        assertEquals(lastHandledTime, state.getLastModifiedTime());
        assertTrue(state instanceof RebuiltStateImpl);
    }

    @Test
    void returnsTokensFromChildren() {
        final var lastHandledTime = Instant.ofEpochSecond(1_234_567L);
        givenStateWithMockChildren();
        given(state.getTimeOfLastHandledTxn()).willReturn(lastHandledTime);
        given(state.isInitialized()).willReturn(true);

        subject.updateChildren(state);
        final var state = subject.get(TOKENS);

        assertEquals(lastHandledTime, state.getLastModifiedTime());
        assertTrue(state instanceof InMemoryStateImpl);
    }

    @Test
    void returnsSchedulesFromChildren() {
        final var lastHandledTime = Instant.ofEpochSecond(1_234_567L);
        givenStateWithMockChildren();
        given(scheduleTxs.byId()).willReturn(scheduleById);
        given(scheduleTxs.byEquality()).willReturn(scheduleByEquality);
        given(scheduleTxs.byExpirationSecond()).willReturn(scheduleByExpiry);

        given(state.getTimeOfLastHandledTxn()).willReturn(lastHandledTime);
        given(state.isInitialized()).willReturn(true);

        subject.updateChildren(state);

        final var schedulesById = subject.get(SCHEDULES_BY_ID);
        final var schedulesByEquality = subject.get(SCHEDULES_BY_EQUALITY);
        final var schedulesByExpiry = subject.get(SCHEDULES_BY_EXPIRY);

        assertEquals(lastHandledTime, schedulesById.getLastModifiedTime());
        assertTrue(schedulesById instanceof InMemoryStateImpl);

        assertEquals(lastHandledTime, schedulesByEquality.getLastModifiedTime());
        assertTrue(schedulesByEquality instanceof InMemoryStateImpl);

        assertEquals(lastHandledTime, schedulesByExpiry.getLastModifiedTime());
        assertTrue(schedulesByExpiry instanceof InMemoryStateImpl);
    }

    @Test
    void returnsOnDiskAccountsWhenAppropriate() {
        final var lastHandledTime = Instant.ofEpochSecond(1_234_567L);
        givenStateWithMockChildren();
        given(state.getTimeOfLastHandledTxn()).willReturn(lastHandledTime);
        given(state.isInitialized()).willReturn(true);
        given(accountsAdapter.areOnDisk()).willReturn(true);
        given(accountsAdapter.getOnDiskAccounts()).willReturn(onDiskAccounts);

        subject.updateChildren(state);

        final var state = subject.get(ACCOUNTS);

        assertEquals(lastHandledTime, state.getLastModifiedTime());
        assertTrue(state instanceof OnDiskStateImpl);
    }

    private void givenStateWithMockChildren() {
        given(state.accounts()).willReturn(accountsAdapter);
        given(state.storage()).willReturn(storage);
        given(state.contractStorage()).willReturn(contractStorage);
        given(state.topics()).willReturn(topics);
        given(state.tokens()).willReturn(tokens);
        given(state.tokenAssociations()).willReturn(tokenAssociations);
        given(state.scheduleTxs()).willReturn(scheduleTxs);
        given(state.networkCtx()).willReturn(networkCtx);
        given(state.addressBook()).willReturn(addressBook);
        given(state.specialFiles()).willReturn(specialFiles);
        given(state.uniqueTokens()).willReturn(uniqueTokens);
        given(state.runningHashLeaf()).willReturn(runningHashLeaf);
        given(state.aliases()).willReturn(aliases);
        given(state.stakingInfo()).willReturn(stakingInfo);
    }

    private void assertChildrenAreExpectedMocks() {
        final var children = subject.getChildren();

        assertSame(accountsAdapter, children.accounts());
        assertSame(storage, children.storage());
        assertSame(contractStorage, children.contractStorage());
        assertSame(topics, children.topics());
        assertSame(tokens, children.tokens());
        assertSame(tokenAssociations, children.tokenAssociations());
        assertSame(scheduleTxs, children.schedules());
        assertSame(scheduleById, children.schedules().byId());
        assertSame(scheduleByEquality, children.schedules().byEquality());
        assertSame(scheduleByExpiry, children.schedules().byExpirationSecond());
        assertSame(networkCtx, children.networkCtx());
        assertSame(addressBook, children.addressBook());
        assertSame(specialFiles, children.specialFiles());
        assertSame(uniqueTokens, children.uniqueTokens());
        assertSame(runningHashLeaf, children.runningHashLeaf());
        assertSame(aliases, children.aliases());
        assertSame(stakingInfo, children.stakingInfo());
    }
}
