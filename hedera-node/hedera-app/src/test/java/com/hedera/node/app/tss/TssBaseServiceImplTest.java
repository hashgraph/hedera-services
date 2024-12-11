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

import static com.hedera.node.app.tss.TssKeyingStatus.KEYING_COMPLETE;
import static com.hedera.node.app.tss.TssKeyingStatus.WAITING_FOR_THRESHOLD_TSS_MESSAGES;
import static com.hedera.node.app.tss.TssKeyingStatus.WAITING_FOR_THRESHOLD_TSS_VOTES;
import static com.hedera.node.app.workflows.handle.steps.PlatformStateUpdatesTest.ROSTER_STATE;
import static com.hedera.node.app.workflows.standalone.TransactionExecutors.DEFAULT_NODE_INFO;
import static com.swirlds.platform.state.service.schemas.V0540RosterSchema.ROSTER_KEY;
import static com.swirlds.platform.state.service.schemas.V0540RosterSchema.ROSTER_STATES_KEY;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.hedera.hapi.node.state.primitives.ProtoBytes;
import com.hedera.hapi.node.state.roster.Roster;
import com.hedera.hapi.node.state.roster.RosterEntry;
import com.hedera.hapi.node.state.roster.RosterState;
import com.hedera.hapi.node.state.roster.RoundRosterPair;
import com.hedera.hapi.services.auxiliary.tss.TssMessageTransactionBody;
import com.hedera.hapi.services.auxiliary.tss.TssVoteTransactionBody;
import com.hedera.node.app.fixtures.state.FakeState;
import com.hedera.node.app.roster.RosterService;
import com.hedera.node.app.spi.AppContext;
import com.hedera.node.app.tss.api.TssLibrary;
import com.hedera.node.app.tss.schemas.V0560TssBaseSchema;
import com.hedera.node.app.tss.schemas.V0570TssBaseSchema;
import com.hedera.node.app.tss.stores.WritableTssStore;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.metrics.api.Metrics;
import com.swirlds.platform.state.service.ReadableRosterStore;
import com.swirlds.state.State;
import com.swirlds.state.lifecycle.SchemaRegistry;
import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TssBaseServiceImplTest {
    private static final Bytes SOURCE_HASH = Bytes.wrap("SOURCE");
    private static final Bytes TARGET_HASH = Bytes.wrap("TARGET");
    private static final Roster SOURCE_ROSTER = Roster.newBuilder()
            .rosterEntries(
                    new RosterEntry(0L, 4L, Bytes.EMPTY, List.of()),
                    new RosterEntry(1L, 3L, Bytes.EMPTY, List.of()),
                    new RosterEntry(2L, 2L, Bytes.EMPTY, List.of()))
            .build();
    private static final long SOURCE_WEIGHT = 9L;
    private static final Roster TARGET_ROSTER = Roster.newBuilder()
            .rosterEntries(
                    new RosterEntry(0L, 1L, Bytes.EMPTY, List.of()),
                    new RosterEntry(1L, 2L, Bytes.EMPTY, List.of()),
                    new RosterEntry(2L, 3L, Bytes.EMPTY, List.of()),
                    new RosterEntry(3L, 4L, Bytes.EMPTY, List.of()))
            .build();

    private CountDownLatch latch;
    private final List<byte[]> receivedMessageHashes = new ArrayList<>();
    private final List<byte[]> receivedSignatures = new ArrayList<>();
    private final BiConsumer<byte[], byte[]> trackingConsumer = (a, b) -> {
        receivedMessageHashes.add(a);
        receivedSignatures.add(b);
        latch.countDown();
    };
    private final AtomicInteger numCalls = new AtomicInteger();
    private final BiConsumer<byte[], byte[]> secondConsumer = (a, b) -> numCalls.incrementAndGet();

    @Mock
    private SchemaRegistry registry;

    @Mock
    private AppContext.Gossip gossip;

    @Mock(strictness = Mock.Strictness.LENIENT)
    private AppContext appContext;

    @Mock
    private Metrics metrics;

    @Mock
    private WritableTssStore tssStore;

    @Mock(strictness = Mock.Strictness.LENIENT)
    private ReadableRosterStore rosterStore;

    @Mock
    private TssLibrary tssLibrary;

    private State state;
    private TssBaseServiceImpl subject;

    @BeforeEach
    void setUp() {
        final ConcurrentHashMap<ProtoBytes, Roster> rosters = new ConcurrentHashMap<>();
        final AtomicReference<RosterState> rosterStateBackingStore = new AtomicReference<>(ROSTER_STATE);
        rosterStateBackingStore.set(RosterState.newBuilder()
                .roundRosterPairs(List.of(RoundRosterPair.newBuilder()
                        .activeRosterHash(SOURCE_HASH)
                        .roundNumber(1)
                        .build()))
                .candidateRosterHash(TARGET_HASH)
                .build());
        rosters.put(ProtoBytes.newBuilder().value(SOURCE_HASH).build(), SOURCE_ROSTER);
        rosters.put(ProtoBytes.newBuilder().value(TARGET_HASH).build(), TARGET_ROSTER);
        state = new FakeState()
                .addService(
                        RosterService.NAME,
                        Map.of(
                                ROSTER_STATES_KEY, rosterStateBackingStore,
                                ROSTER_KEY, rosters))
                .addService(
                        TssBaseService.NAME,
                        Map.of(
                                V0570TssBaseSchema.TSS_ENCRYPTION_KEY_MAP_KEY,
                                new HashMap<>(),
                                V0560TssBaseSchema.TSS_MESSAGE_MAP_KEY,
                                new HashMap<>(),
                                V0560TssBaseSchema.TSS_VOTE_MAP_KEY,
                                new HashMap<>()));

        given(appContext.gossip()).willReturn(gossip);
        given(appContext.instantSource()).willReturn(InstantSource.system());
        given(appContext.configSupplier()).willReturn(HederaTestConfigBuilder::createConfig);
        given(appContext.selfNodeInfoSupplier()).willReturn(() -> DEFAULT_NODE_INFO);

        subject = new TssBaseServiceImpl(
                appContext,
                ForkJoinPool.commonPool(),
                ForkJoinPool.commonPool(),
                tssLibrary,
                ForkJoinPool.commonPool(),
                metrics);
    }

    @Test
    void onlyRegisteredConsumerReceiveCallbacks() throws InterruptedException {
        final var firstMessage = new byte[] {(byte) 0x01};
        final var secondMessage = new byte[] {(byte) 0x02};
        latch = new CountDownLatch(1);

        subject.registerLedgerSignatureConsumer(trackingConsumer);
        subject.registerLedgerSignatureConsumer(secondConsumer);

        subject.requestLedgerSignature(firstMessage, Instant.ofEpochSecond(1_234_567L));
        assertTrue(latch.await(1, TimeUnit.SECONDS));
        subject.unregisterLedgerSignatureConsumer(secondConsumer);
        latch = new CountDownLatch(1);
        subject.requestLedgerSignature(secondMessage, Instant.ofEpochSecond(1_234_567L));
        assertTrue(latch.await(1, TimeUnit.SECONDS));

        assertEquals(2, receivedMessageHashes.size());
        assertEquals(2, receivedSignatures.size());
        assertArrayEquals(firstMessage, receivedMessageHashes.getFirst());
        assertArrayEquals(secondMessage, receivedMessageHashes.getLast());
        assertEquals(1, numCalls.get());
    }

    @Test
    void placeholderRegistersSchemas() {
        subject.registerSchemas(registry);
        verify(registry).register(argThat(s -> s instanceof V0560TssBaseSchema));
    }

    @Test
    void managesTssStatusWhenRosterToKeyIsNone() {
        final var oldStatus = new TssStatus(WAITING_FOR_THRESHOLD_TSS_MESSAGES, RosterToKey.NONE, Bytes.EMPTY);
        final var expectedTssStatus =
                new TssStatus(WAITING_FOR_THRESHOLD_TSS_MESSAGES, RosterToKey.CANDIDATE_ROSTER, Bytes.EMPTY);
        subject.setTssStatus(oldStatus);
        subject.manageTssStatus(
                true,
                Instant.ofEpochSecond(1_234_567L),
                new TssBaseServiceImpl.RosterAndTssInfo(
                        SOURCE_ROSTER,
                        SOURCE_HASH,
                        SOURCE_ROSTER,
                        SOURCE_HASH,
                        List.of(),
                        Optional.empty(),
                        List.of(),
                        null));
        assertEquals(expectedTssStatus, subject.getTssStatus());
    }

    @Test
    void managesTssStatusWhenMessagesReachedThreshold() {
        final var messages = IntStream.range(0, 8)
                .mapToObj(i ->
                        new TssMessageTransactionBody(SOURCE_HASH, TARGET_HASH, i * 2L + 1, Bytes.wrap("MESSAGE" + i)))
                .toList();
        final var oldStatus = new TssStatus(WAITING_FOR_THRESHOLD_TSS_MESSAGES, RosterToKey.ACTIVE_ROSTER, Bytes.EMPTY);
        final var expectedTssStatus =
                new TssStatus(WAITING_FOR_THRESHOLD_TSS_VOTES, RosterToKey.ACTIVE_ROSTER, Bytes.EMPTY);
        subject.setTssStatus(oldStatus);

        given(rosterStore.getCurrentRosterHash()).willReturn(SOURCE_HASH);
        given(tssLibrary.verifyTssMessage(any(), any())).willReturn(true);

        subject.generateParticipantDirectory(state);
        subject.manageTssStatus(
                true,
                Instant.ofEpochSecond(1_234_567L),
                new TssBaseServiceImpl.RosterAndTssInfo(
                        SOURCE_ROSTER,
                        SOURCE_HASH,
                        SOURCE_ROSTER,
                        SOURCE_HASH,
                        messages,
                        Optional.empty(),
                        List.of(),
                        null));
        assertEquals(expectedTssStatus, subject.getTssStatus());
    }

    @ParameterizedTest
    @EnumSource(
            value = RosterToKey.class,
            names = {"ACTIVE_ROSTER", "CANDIDATE_ROSTER"})
    void managesTssStatusWhenKeyingComplete(RosterToKey rosterToKey) {
        final var oldStatus = new TssStatus(KEYING_COMPLETE, rosterToKey, Bytes.EMPTY);
        final var expectedTssStatus = new TssStatus(KEYING_COMPLETE, RosterToKey.NONE, Bytes.EMPTY);
        subject.setTssStatus(oldStatus);

        given(rosterStore.getCurrentRosterHash()).willReturn(SOURCE_HASH);
        given(rosterStore.getCandidateRoster()).willReturn(TARGET_ROSTER);

        subject.generateParticipantDirectory(state);
        subject.manageTssStatus(
                true,
                Instant.ofEpochSecond(1_234_567L),
                new TssBaseServiceImpl.RosterAndTssInfo(
                        SOURCE_ROSTER,
                        SOURCE_HASH,
                        SOURCE_ROSTER,
                        SOURCE_HASH,
                        List.of(),
                        Optional.empty(),
                        List.of(),
                        null));
        assertEquals(expectedTssStatus, subject.getTssStatus());
    }

    @ParameterizedTest
    @EnumSource(
            value = RosterToKey.class,
            names = {"ACTIVE_ROSTER", "CANDIDATE_ROSTER"})
    void managesTssStatusWhenVotesReachedThreshold(RosterToKey rosterToKey) {
        final var oldStatus = new TssStatus(WAITING_FOR_THRESHOLD_TSS_VOTES, rosterToKey, Bytes.EMPTY);
        final var expectedTssStatus = new TssStatus(KEYING_COMPLETE, rosterToKey, Bytes.wrap("ledger"));
        subject.setTssStatus(oldStatus);

        given(rosterStore.getCurrentRosterHash()).willReturn(SOURCE_HASH);
        given(rosterStore.getCandidateRoster()).willReturn(TARGET_ROSTER);

        subject.generateParticipantDirectory(state);
        subject.manageTssStatus(
                true,
                Instant.ofEpochSecond(1_234_567L),
                new TssBaseServiceImpl.RosterAndTssInfo(
                        SOURCE_ROSTER,
                        SOURCE_HASH,
                        TARGET_ROSTER,
                        TARGET_HASH,
                        List.of(),
                        Optional.of(TssVoteTransactionBody.newBuilder()
                                .ledgerId(Bytes.wrap("ledger"))
                                .tssVote(Bytes.wrap(
                                        BitSet.valueOf(new long[] {1L, 2L}).toByteArray()))
                                .build()),
                        List.of(),
                        null));
        assertEquals(expectedTssStatus, subject.getTssStatus());
    }
}
