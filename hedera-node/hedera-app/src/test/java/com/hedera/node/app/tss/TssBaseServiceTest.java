/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.tss;

import static com.hedera.node.app.tss.handlers.TssShareSignatureHandlerTest.TSS_KEYS;
import static com.hedera.node.app.workflows.handle.steps.NodeStakeUpdatesTest.RosterCase.ACTIVE_ROSTER;
import static com.hedera.node.app.workflows.handle.steps.NodeStakeUpdatesTest.RosterCase.CURRENT_CANDIDATE_ROSTER;
import static com.hedera.node.app.workflows.handle.steps.NodeStakeUpdatesTest.RosterCase.ROSTER_NODE_1;
import static com.hedera.node.app.workflows.handle.steps.NodeStakeUpdatesTest.RosterCase.ROSTER_NODE_2;
import static com.hedera.node.app.workflows.handle.steps.NodeStakeUpdatesTest.RosterCase.ROSTER_NODE_3;
import static com.hedera.node.app.workflows.standalone.TransactionExecutors.DEFAULT_NODE_INFO;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.state.roster.Roster;
import com.hedera.node.app.info.NodeInfoImpl;
import com.hedera.node.app.spi.AppContext;
import com.hedera.node.app.spi.store.StoreFactory;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.tss.api.TssLibrary;
import com.hedera.node.app.tss.stores.ReadableTssStore;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.swirlds.metrics.api.Metrics;
import com.swirlds.platform.state.service.ReadableRosterStore;
import com.swirlds.platform.state.service.WritableRosterStore;
import com.swirlds.state.lifecycle.info.NetworkInfo;
import java.time.Instant;
import java.time.InstantSource;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class TssBaseServiceTest {
    @Mock(strictness = Mock.Strictness.LENIENT)
    private HandleContext handleContext;

    @Mock(strictness = Mock.Strictness.LENIENT)
    private AppContext appContext;

    @Mock(strictness = Mock.Strictness.LENIENT)
    private StoreFactory storeFactory;

    @Mock
    private WritableRosterStore rosterStore;

    @Mock(strictness = Mock.Strictness.LENIENT)
    private ReadableRosterStore readableRosterStore;

    @Mock
    private ReadableTssStore readableTssStore;

    @Mock(strictness = Mock.Strictness.LENIENT)
    private TssLibrary tssLibrary;

    @Mock(strictness = Mock.Strictness.LENIENT)
    private NetworkInfo networkInfo;
    @Mock
    private com.hedera.node.app.tss.cryptography.tss.api.TssMessage tssMessage;

    @Mock
    private Executor executor;

    private TssBaseServiceImpl subject;

    @BeforeEach
    void setUp() {
        given(appContext.gossip()).willReturn(mock(AppContext.Gossip.class));
        given(appContext.instantSource()).willReturn(InstantSource.system());
        given(appContext.selfNodeInfoSupplier()).willReturn(() -> DEFAULT_NODE_INFO);
        given(appContext.configSupplier()).willReturn(() -> HederaTestConfigBuilder.createConfig());

        subject = new TssBaseServiceImpl(
                appContext,
                mock(ExecutorService.class),
                mock(Executor.class),
                tssLibrary,
                executor,
                mock(Metrics.class));
        given(handleContext.configuration()).willReturn(HederaTestConfigBuilder.createConfig());
        given(handleContext.networkInfo()).willReturn(networkInfo);
        given(networkInfo.selfNodeInfo())
                .willReturn(
                        new NodeInfoImpl(1, AccountID.newBuilder().accountNum(3).build(), 0, null, null));
        given(storeFactory.readableStore(ReadableRosterStore.class)).willReturn(readableRosterStore);
        given(readableRosterStore.getActiveRoster()).willReturn(Roster.DEFAULT);
        subject.getTssKeysAccessor().setTssKeys(TSS_KEYS);
    }

    @Test
    void nameIsAsExpected() {
        final var subject = mock(TssBaseService.class);
        doCallRealMethod().when(subject).getServiceName();
        assertEquals(TssBaseService.NAME, subject.getServiceName());
    }

    @Test
    @DisplayName("Service won't set the current candidate roster as the new candidate roster")
    void doesntSetSameCandidateRoster() {
        given(storeFactory.readableStore(ReadableTssStore.class)).willReturn(readableTssStore);
        // Simulate CURRENT_CANDIDATE_ROSTER and ACTIVE_ROSTER
        mockWritableRosterStore();
        given(tssLibrary.decryptPrivateShares(any(), any())).willReturn(List.of());

        // Attempt to set the same candidate roster
        subject.setCandidateRoster(CURRENT_CANDIDATE_ROSTER, handleContext);
        verify(rosterStore, never()).putCandidateRoster(any());
    }

    @Test
    @DisplayName("Service won't set the active roster as the new candidate roster")
    void doesntSetActiveRosterAsCandidateRoster() {
        given(storeFactory.readableStore(ReadableTssStore.class)).willReturn(readableTssStore);

        final var captor = ArgumentCaptor.forClass(Runnable.class);
        final var rosterStore = mockWritableRosterStore();
        given(handleContext.storeFactory()).willReturn(storeFactory);
        given(storeFactory.writableStore(WritableRosterStore.class)).willReturn(rosterStore);
        given(tssLibrary.decryptPrivateShares(any(), any())).willReturn(List.of());
        given(tssLibrary.generateTssMessage(any(), any()))
                .willReturn(tssMessage);
        given(handleContext.consensusNow()).willReturn(Instant.ofEpochSecond(1_234_567L));

        subject.setCandidateRoster(ACTIVE_ROSTER, handleContext);

        verify(executor).execute(captor.capture());
        final var task = captor.getValue();
        task.run();
        verify(tssLibrary).generateTssMessage(any(), any());
    }

    @Test
    @DisplayName("Service appropriately sets a new roster as the new candidate roster")
    void setsCandidateRoster() {
        final var captor = ArgumentCaptor.forClass(Runnable.class);
        given(storeFactory.readableStore(ReadableTssStore.class)).willReturn(readableTssStore);
        // Simulate the _current_ candidate roster and active roster
        final var rosterStore = mockWritableRosterStore();
        given(tssLibrary.decryptPrivateShares(any(), any())).willReturn(List.of());
        given(tssLibrary.generateTssMessage(any(), any()))
                .willReturn(tssMessage);
        given(handleContext.consensusNow()).willReturn(Instant.ofEpochSecond(1_234_567L));
        final var inputRoster = Roster.newBuilder()
                .rosterEntries(List.of(ROSTER_NODE_1, ROSTER_NODE_2, ROSTER_NODE_3))
                .build();

        subject.setCandidateRoster(inputRoster, handleContext);

        verify(executor).execute(captor.capture());
        final var task = captor.getValue();
        task.run();
        verify(tssLibrary).generateTssMessage(any(), any());
    }

    private WritableRosterStore mockWritableRosterStore() {
        // Mock retrieval of the roster store
        given(handleContext.storeFactory()).willReturn(storeFactory);
        given(storeFactory.readableStore(ReadableRosterStore.class)).willReturn(rosterStore);
        given(storeFactory.writableStore(WritableRosterStore.class)).willReturn(rosterStore);

        // Mock the candidate and active rosters
        lenient().when(rosterStore.getCandidateRoster()).thenReturn(CURRENT_CANDIDATE_ROSTER);
        lenient().when(rosterStore.getActiveRoster()).thenReturn(ACTIVE_ROSTER);

        return rosterStore;
    }
}
