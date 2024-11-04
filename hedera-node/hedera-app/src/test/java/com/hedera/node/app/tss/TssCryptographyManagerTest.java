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

import static com.hedera.node.app.tss.handlers.TssUtils.computeNodeShares;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.state.roster.RosterEntry;
import com.hedera.hapi.services.auxiliary.tss.TssMessageTransactionBody;
import com.hedera.hapi.services.auxiliary.tss.TssVoteTransactionBody;
import com.hedera.node.app.info.NodeInfoImpl;
import com.hedera.node.app.spi.AppContext;
import com.hedera.node.app.spi.store.StoreFactory;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.tss.api.TssLibrary;
import com.hedera.node.app.tss.api.TssParticipantDirectory;
import com.hedera.node.app.tss.api.TssPublicShare;
import com.hedera.node.app.tss.api.TssShareId;
import com.hedera.node.app.tss.pairings.FakeGroupElement;
import com.hedera.node.app.tss.pairings.PairingPublicKey;
import com.hedera.node.app.tss.stores.WritableTssStore;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.crypto.Signature;
import com.swirlds.state.spi.info.NetworkInfo;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ForkJoinPool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class TssCryptographyManagerTest {
    private TssCryptographyManager subject;

    @Mock
    private TssLibrary tssLibrary;

    @Mock
    private TssParticipantDirectory tssParticipantDirectory;

    @Mock
    private AppContext.Gossip gossip;

    @Mock(strictness = Mock.Strictness.LENIENT)
    private HandleContext handleContext;

    @Mock
    private StoreFactory storeFactory;

    @Mock
    private WritableTssStore tssStore;

    @Mock
    private TssMetrics tssMetrics;

    @Mock(strictness = Mock.Strictness.LENIENT)
    private NetworkInfo networkInfo;

    @BeforeEach
    void setUp() {
        subject = new TssCryptographyManager(tssLibrary, gossip, ForkJoinPool.commonPool(), tssMetrics);
        when(handleContext.networkInfo()).thenReturn(networkInfo);
        when(networkInfo.selfNodeInfo()).thenReturn(new NodeInfoImpl(0, AccountID.DEFAULT, 0, null, null));
    }

    @Test
    void testWhenVoteAlreadySubmitted() {
        final var body = getTssBody();
        when(handleContext.storeFactory()).thenReturn(storeFactory);
        when(storeFactory.writableStore(WritableTssStore.class)).thenReturn(tssStore);
        when(tssStore.getVote(any())).thenReturn(mock(TssVoteTransactionBody.class)); // Simulate vote already submitted

        final var result = subject.handleTssMessageTransaction(body, tssParticipantDirectory, handleContext);

        assertNull(result.join());
    }

    @Test
    void testWhenVoteNoVoteSubmittedAndThresholdNotMet() {
        final var body = getTssBody();
        when(handleContext.storeFactory()).thenReturn(storeFactory);
        when(storeFactory.writableStore(WritableTssStore.class)).thenReturn(tssStore);
        when(tssStore.getVote(any())).thenReturn(null);

        final var result = subject.handleTssMessageTransaction(body, tssParticipantDirectory, handleContext);

        assertNull(result.join());
    }

    @Test
    void testWhenVoteNoVoteSubmittedAndThresholdMet() {
        final var ledgerId = mock(PairingPublicKey.class);
        final var mockPublicShares = List.of(new TssPublicShare(new TssShareId(10), mock(PairingPublicKey.class)));
        final var mockSignature = mock(Signature.class);

        final var body = getTssBody();
        when(handleContext.storeFactory()).thenReturn(storeFactory);
        when(storeFactory.writableStore(WritableTssStore.class)).thenReturn(tssStore);
        when(tssStore.getVote(any())).thenReturn(null);
        when(tssStore.getTssMessages(any())).thenReturn(List.of(body));
        when(tssLibrary.verifyTssMessage(any(), any())).thenReturn(true);

        when(tssLibrary.computePublicShares(any(), any())).thenReturn(mockPublicShares);
        when(tssLibrary.aggregatePublicShares(any())).thenReturn(ledgerId);
        when(gossip.sign(any())).thenReturn(mockSignature);
        when(ledgerId.publicKey()).thenReturn(new FakeGroupElement(BigInteger.valueOf(5L)));

        final var result = subject.handleTssMessageTransaction(body, tssParticipantDirectory, handleContext);

        assertNotNull(result.join());
        verify(gossip).sign(ledgerId.publicKey().toBytes());
    }

    @Test
    void testWhenMetException() {
        final var body = getTssBody();
        when(handleContext.storeFactory()).thenReturn(storeFactory);
        when(storeFactory.writableStore(WritableTssStore.class)).thenReturn(tssStore);
        when(tssStore.getVote(any())).thenReturn(null);
        when(tssStore.getTssMessages(any())).thenReturn(List.of(body));
        when(tssLibrary.verifyTssMessage(any(), any())).thenReturn(true);

        when(tssLibrary.computePublicShares(any(), any())).thenThrow(new RuntimeException());

        final var result = subject.handleTssMessageTransaction(body, tssParticipantDirectory, handleContext);

        assertNull(result.join());
        verify(gossip, never()).sign(any());
    }

    @Test
    void testComputeNodeShares() {
        RosterEntry entry1 = new RosterEntry(1L, 100L, null, null, null);
        RosterEntry entry2 = new RosterEntry(2L, 50L, null, null, null);

        Map<Long, Long> result = computeNodeShares(List.of(entry1, entry2), 10L);

        assertEquals(2, result.size());
        assertEquals(10L, result.get(1L));
        assertEquals(5L, result.get(2L));
    }

    private TssMessageTransactionBody getTssBody() {
        final Bytes targetRosterHash = Bytes.wrap("targetRoster".getBytes());
        final Bytes sourceRosterHash = Bytes.wrap("sourceRoster".getBytes());
        return TssMessageTransactionBody.newBuilder()
                .tssMessage(Bytes.wrap("tssMessage".getBytes()))
                .shareIndex(1)
                .sourceRosterHash(sourceRosterHash)
                .targetRosterHash(targetRosterHash)
                .build();
    }
}
