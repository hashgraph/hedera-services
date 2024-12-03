/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.workflows.handle.steps;

import static com.hedera.node.app.fixtures.AppTestBase.DEFAULT_CONFIG;
import static com.hedera.node.app.service.addressbook.impl.schemas.V053AddressBookSchema.NODES_KEY;
import static com.hedera.node.app.service.token.impl.handlers.staking.StakePeriodManager.DEFAULT_STAKING_PERIOD_MINS;
import static com.hedera.node.app.tss.schemas.V0560TssBaseSchema.TSS_MESSAGE_MAP_KEY;
import static com.hedera.node.app.tss.schemas.V0560TssBaseSchema.TSS_VOTE_MAP_KEY;
import static com.hedera.node.app.tss.schemas.V0570TssBaseSchema.TSS_ENCRYPTION_KEY_MAP_KEY;
import static com.hedera.node.app.tss.schemas.V0570TssBaseSchema.TSS_STATUS_KEY;
import static com.hedera.node.config.types.StreamMode.BLOCKS;
import static com.hedera.node.config.types.StreamMode.RECORDS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.hedera.hapi.node.base.ServiceEndpoint;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.state.addressbook.Node;
import com.hedera.hapi.node.state.blockrecords.BlockInfo;
import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.hapi.node.state.primitives.ProtoBytes;
import com.hedera.hapi.node.state.roster.Roster;
import com.hedera.hapi.node.state.roster.RosterEntry;
import com.hedera.hapi.node.state.roster.RosterState;
import com.hedera.hapi.node.state.roster.RoundRosterPair;
import com.hedera.hapi.node.state.tss.TssMessageMapKey;
import com.hedera.hapi.node.state.tss.TssStatus;
import com.hedera.hapi.node.state.tss.TssVoteMapKey;
import com.hedera.hapi.node.transaction.ExchangeRateSet;
import com.hedera.hapi.services.auxiliary.tss.TssEncryptionKeyTransactionBody;
import com.hedera.hapi.services.auxiliary.tss.TssMessageTransactionBody;
import com.hedera.hapi.services.auxiliary.tss.TssVoteTransactionBody;
import com.hedera.node.app.fees.ExchangeRateManager;
import com.hedera.node.app.records.ReadableBlockRecordStore;
import com.hedera.node.app.roster.schemas.V0540RosterSchema;
import com.hedera.node.app.service.addressbook.AddressBookService;
import com.hedera.node.app.service.addressbook.ReadableNodeStore;
import com.hedera.node.app.service.addressbook.impl.ReadableNodeStoreImpl;
import com.hedera.node.app.service.token.impl.handlers.staking.EndOfStakingPeriodUpdater;
import com.hedera.node.app.service.token.records.TokenContext;
import com.hedera.node.app.spi.metrics.StoreMetricsService;
import com.hedera.node.app.spi.store.StoreFactory;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.tss.TssBaseService;
import com.hedera.node.app.workflows.handle.Dispatch;
import com.hedera.node.app.workflows.handle.stack.SavepointStackImpl;
import com.hedera.node.config.data.StakingConfig;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.hedera.node.config.types.StreamMode;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.config.api.Configuration;
import com.swirlds.platform.roster.RosterUtils;
import com.swirlds.state.spi.WritableKVState;
import com.swirlds.state.spi.WritableSingletonState;
import com.swirlds.state.spi.WritableStates;
import com.swirlds.state.test.fixtures.MapWritableKVState;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class NodeStakeUpdatesTest {
    private static final Instant CONSENSUS_TIME_1234567 = Instant.ofEpochSecond(1_234_5670L, 1357);

    @Mock
    private EndOfStakingPeriodUpdater stakingPeriodCalculator;

    @Mock
    private ExchangeRateManager exchangeRateManager;

    @Mock(strictness = Mock.Strictness.LENIENT)
    private TokenContext context;

    @Mock
    private ReadableBlockRecordStore blockStore;

    @Mock
    private TssBaseService tssBaseService;

    @Mock
    private SavepointStackImpl stack;

    @Mock
    private Dispatch dispatch;

    @Mock
    private WritableStates writableStates;

    @Mock
    private HandleContext handleContext;

    @Mock
    private StoreFactory storeFactory;

    @Mock
    private StoreMetricsService storeMetricsService;

    @Mock
    private WritableSingletonState<RosterState> rosterState;

    @Mock
    private WritableKVState<EntityNumber, Node> nodesState;

    @Mock
    private WritableKVState<TssMessageMapKey, TssMessageTransactionBody> tssMessageState;

    @Mock
    private WritableKVState<TssVoteMapKey, TssVoteTransactionBody> tssVoteState;

    @Mock
    private WritableKVState<EntityNumber, TssEncryptionKeyTransactionBody> tssEncryptionKeyState;

    @Mock
    private WritableSingletonState<TssStatus> tssStatusState;

    private StakePeriodChanges subject;

    @BeforeEach
    void setUp() {
        given(context.readableStore(ReadableBlockRecordStore.class)).willReturn(blockStore);

        subject = new StakePeriodChanges(
                stakingPeriodCalculator, exchangeRateManager, tssBaseService, storeMetricsService);
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void nullArgConstructor() {
        Assertions.assertThatThrownBy(
                        () -> new StakePeriodChanges(null, exchangeRateManager, tssBaseService, storeMetricsService))
                .isInstanceOf(NullPointerException.class);
        Assertions.assertThatThrownBy(() ->
                        new StakePeriodChanges(stakingPeriodCalculator, null, tssBaseService, storeMetricsService))
                .isInstanceOf(NullPointerException.class);
        Assertions.assertThatThrownBy(() ->
                        new StakePeriodChanges(stakingPeriodCalculator, exchangeRateManager, null, storeMetricsService))
                .isInstanceOf(NullPointerException.class);
        Assertions.assertThatThrownBy(() ->
                        new StakePeriodChanges(stakingPeriodCalculator, exchangeRateManager, tssBaseService, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void processUpdateSkippedForPreviousPeriod() {
        verifyNoInteractions(stakingPeriodCalculator);
        verifyNoInteractions(exchangeRateManager);
    }

    @Test
    @SuppressWarnings("unchecked")
    void processUpdateCalledForGenesisTxn() {
        given(exchangeRateManager.exchangeRates()).willReturn(ExchangeRateSet.DEFAULT);
        given(context.configuration()).willReturn(DEFAULT_CONFIG);
        given(stack.getWritableStates(AddressBookService.NAME)).willReturn(writableStates);
        given(writableStates.<EntityNumber, Node>get(NODES_KEY)).willReturn(nodesState);

        subject.process(dispatch, stack, context, RECORDS, true, Instant.EPOCH);

        verify(stakingPeriodCalculator).updateNodes(eq(context), eq(ExchangeRateSet.DEFAULT), any(BiConsumer.class));
        verify(exchangeRateManager).updateMidnightRates(stack);
    }

    @Test
    void processUpdateSkippedForPreviousConsensusTime() {
        final var beforeLastConsensusTime = CONSENSUS_TIME_1234567.minusSeconds(1);
        given(context.consensusTime()).willReturn(beforeLastConsensusTime);
        given(blockStore.getLastBlockInfo())
                .willReturn(BlockInfo.newBuilder()
                        .consTimeOfLastHandledTxn(Timestamp.newBuilder()
                                .seconds(CONSENSUS_TIME_1234567.getEpochSecond())
                                .nanos(CONSENSUS_TIME_1234567.getNano()))
                        .build());

        subject.process(dispatch, stack, context, RECORDS, false, Instant.EPOCH);

        verifyNoInteractions(stakingPeriodCalculator);
        verifyNoInteractions(exchangeRateManager);
    }

    @Test
    @SuppressWarnings("unchecked")
    void processUpdateCalledForNextPeriodWithRecordsStreamMode() {
        given(context.configuration()).willReturn(newPeriodMinsConfig());
        // Use any number of seconds that gets isNextPeriod(...) to return true
        final var currentConsensusTime = CONSENSUS_TIME_1234567.plusSeconds(500_000);
        given(blockStore.getLastBlockInfo())
                .willReturn(BlockInfo.newBuilder()
                        .consTimeOfLastHandledTxn(Timestamp.newBuilder()
                                .seconds(CONSENSUS_TIME_1234567.getEpochSecond())
                                .nanos(CONSENSUS_TIME_1234567.getNano()))
                        .build());
        given(context.consensusTime()).willReturn(currentConsensusTime);
        given(stack.getWritableStates(AddressBookService.NAME)).willReturn(writableStates);
        given(writableStates.<EntityNumber, Node>get(NODES_KEY)).willReturn(nodesState);

        // Pre-condition check
        Assertions.assertThat(
                        StakePeriodChanges.isNextStakingPeriod(currentConsensusTime, CONSENSUS_TIME_1234567, context))
                .isTrue();
        given(exchangeRateManager.exchangeRates()).willReturn(ExchangeRateSet.DEFAULT);

        subject.process(dispatch, stack, context, RECORDS, false, Instant.EPOCH);

        verify(stakingPeriodCalculator)
                .updateNodes(
                        argThat(stakingContext -> currentConsensusTime.equals(stakingContext.consensusTime())),
                        eq(ExchangeRateSet.DEFAULT),
                        any(BiConsumer.class));
        verify(exchangeRateManager).updateMidnightRates(stack);
    }

    @Test
    @SuppressWarnings("unchecked")
    void processUpdateCalledForNextPeriodWithBlocksStreamMode() {
        given(context.configuration()).willReturn(newPeriodMinsConfig());
        // Use any number of seconds that gets isNextPeriod(...) to return true
        final var currentConsensusTime = CONSENSUS_TIME_1234567.plusSeconds(500_000);
        given(context.consensusTime()).willReturn(currentConsensusTime);

        // Pre-condition check
        Assertions.assertThat(
                        StakePeriodChanges.isNextStakingPeriod(currentConsensusTime, CONSENSUS_TIME_1234567, context))
                .isTrue();
        given(exchangeRateManager.exchangeRates()).willReturn(ExchangeRateSet.DEFAULT);
        given(stack.getWritableStates(AddressBookService.NAME)).willReturn(writableStates);
        given(writableStates.<EntityNumber, Node>get(NODES_KEY)).willReturn(nodesState);

        subject.process(dispatch, stack, context, BLOCKS, false, CONSENSUS_TIME_1234567);

        verify(stakingPeriodCalculator)
                .updateNodes(
                        argThat(stakingContext -> currentConsensusTime.equals(stakingContext.consensusTime())),
                        eq(ExchangeRateSet.DEFAULT),
                        any(BiConsumer.class));
        verify(exchangeRateManager).updateMidnightRates(stack);
    }

    @Test
    @SuppressWarnings("unchecked")
    void processUpdateExceptionIsCaught() {
        given(exchangeRateManager.exchangeRates()).willReturn(ExchangeRateSet.DEFAULT);
        doThrow(new RuntimeException("test exception"))
                .when(stakingPeriodCalculator)
                .updateNodes(any(), eq(ExchangeRateSet.DEFAULT), any(BiConsumer.class));
        given(blockStore.getLastBlockInfo())
                .willReturn(BlockInfo.newBuilder()
                        .consTimeOfLastHandledTxn(new Timestamp(CONSENSUS_TIME_1234567.getEpochSecond(), 0))
                        .build());
        given(context.consensusTime()).willReturn(CONSENSUS_TIME_1234567.plus(Duration.ofDays(2)));
        given(context.configuration()).willReturn(DEFAULT_CONFIG);
        given(stack.getWritableStates(AddressBookService.NAME)).willReturn(writableStates);
        given(writableStates.<EntityNumber, Node>get(NODES_KEY)).willReturn(nodesState);

        Assertions.assertThatNoException()
                .isThrownBy(() -> subject.process(dispatch, stack, context, RECORDS, false, Instant.EPOCH));
        verify(stakingPeriodCalculator).updateNodes(eq(context), eq(ExchangeRateSet.DEFAULT), any(BiConsumer.class));
        verify(exchangeRateManager).updateMidnightRates(stack);
    }

    @Test
    void isNextStakingPeriodNowConsensusTimeBeforeThenConsensusTimeUtcDay() {
        given(context.configuration()).willReturn(newPeriodMinsConfig());

        final var earlierNowConsensus =
                CONSENSUS_TIME_1234567.minusSeconds(Duration.ofDays(1).toSeconds());
        final var result = StakePeriodChanges.isNextStakingPeriod(earlierNowConsensus, CONSENSUS_TIME_1234567, context);

        Assertions.assertThat(result).isFalse();
    }

    @Test
    void isNextStakingPeriodNowConsensusTimeInSameThenConsensusTimeUtcDay() {
        given(context.configuration()).willReturn(newPeriodMinsConfig());

        final var result =
                StakePeriodChanges.isNextStakingPeriod(CONSENSUS_TIME_1234567, CONSENSUS_TIME_1234567, context);

        Assertions.assertThat(result).isFalse();
    }

    @Test
    void isNextStakingPeriodNowConsensusTimeAfterThenConsensusTimeUtcDay() {
        given(context.configuration()).willReturn(newPeriodMinsConfig());

        final var laterNowConsensus =
                CONSENSUS_TIME_1234567.plusSeconds(Duration.ofDays(1).toSeconds());
        final var result = StakePeriodChanges.isNextStakingPeriod(laterNowConsensus, CONSENSUS_TIME_1234567, context);

        Assertions.assertThat(result).isTrue();
    }

    @Test
    void isNextStakingPeriodNowCustomStakingPeriodIsEarlier() {
        final var periodMins = 990;
        given(context.configuration()).willReturn(newPeriodMinsConfig(periodMins));

        final var earlierStakingPeriodTime = CONSENSUS_TIME_1234567.minusSeconds(
                // 1000 min * 60 seconds/min
                1000 * 60);
        final var result =
                StakePeriodChanges.isNextStakingPeriod(earlierStakingPeriodTime, CONSENSUS_TIME_1234567, context);
        Assertions.assertThat(result).isFalse();
    }

    @Test
    void isNextStakingPeriodNowCustomStakingPeriodIsLater() {
        final var periodMins = 990;
        given(context.configuration()).willReturn(newPeriodMinsConfig(periodMins));

        final var laterStakingPeriodTime = CONSENSUS_TIME_1234567.plusSeconds(
                // 1000 min * 60 seconds/min
                1000 * 60);
        final var result =
                StakePeriodChanges.isNextStakingPeriod(laterStakingPeriodTime, CONSENSUS_TIME_1234567, context);
        Assertions.assertThat(result).isTrue();
    }

    @Test
    void stakingPeriodDoesntSetCandidateRosterForDisabledFlag() {
        // Simulate staking information
        given(context.configuration()).willReturn(newConfig(990, false));
        given(blockStore.getLastBlockInfo())
                .willReturn(BlockInfo.newBuilder()
                        .consTimeOfLastHandledTxn(new Timestamp(CONSENSUS_TIME_1234567.getEpochSecond(), 0))
                        .build());
        given(context.consensusTime()).willReturn(CONSENSUS_TIME_1234567.plus(Duration.ofDays(2)));

        subject.process(dispatch, stack, context, StreamMode.RECORDS, false, Instant.EPOCH);
        verifyNoInteractions(tssBaseService);
    }

    @Test
    @DisplayName("Service won't set the current candidate roster as the new candidate roster")
    void doesntSetSameCandidateRoster() {
        // Simulate staking information,
        given(blockStore.getLastBlockInfo())
                .willReturn(BlockInfo.newBuilder()
                        .consTimeOfLastHandledTxn(new Timestamp(CONSENSUS_TIME_1234567.getEpochSecond(), 0))
                        .build());
        given(context.consensusTime()).willReturn(CONSENSUS_TIME_1234567.plus(Duration.ofDays(2)));

        // Simulate disabled `keyCandidateRoster` property
        given(context.configuration()).willReturn(newConfig(990, false));

        subject.process(dispatch, stack, context, StreamMode.RECORDS, false, Instant.EPOCH);
        verify(tssBaseService, never()).setCandidateRoster(any(), any());
    }

    @Test
    @DisplayName("Service won't set the active roster as the new candidate roster")
    void doesntSetActiveRosterAsCandidateRoster() {
        // Simulate staking information
        given(blockStore.getLastBlockInfo())
                .willReturn(BlockInfo.newBuilder()
                        .consTimeOfLastHandledTxn(new Timestamp(CONSENSUS_TIME_1234567.getEpochSecond(), 0))
                        .build());
        given(context.consensusTime()).willReturn(CONSENSUS_TIME_1234567.plus(Duration.ofDays(2)));

        // Enable keyCandidateRoster
        given(context.configuration()).willReturn(newConfig(DEFAULT_STAKING_PERIOD_MINS, true));

        // Simulate the same address book input as the current candidate and active rosters
        final var nodeStore = simulateNodes(RosterCase.NODE_1, RosterCase.NODE_2, RosterCase.NODE_3, RosterCase.NODE_4);
        given(dispatch.handleContext()).willReturn(handleContext);
        given(handleContext.storeFactory()).willReturn(storeFactory);
        given(storeFactory.readableStore(ReadableNodeStore.class)).willReturn(nodeStore);
        given(stack.getWritableStates(notNull())).willReturn(writableStates);
        simulateCandidateAndActiveRosters();

        // Attempt to set the (equivalent) active roster as the new candidate roster
        subject.process(dispatch, stack, context, StreamMode.RECORDS, false, Instant.EPOCH);
        verify(tssBaseService, never()).setCandidateRoster(any(), any());
    }

    @Test
    void stakingPeriodSetsCandidateRosterForEnabledFlag() {
        // Simulate staking information
        given(blockStore.getLastBlockInfo())
                .willReturn(BlockInfo.newBuilder()
                        .consTimeOfLastHandledTxn(new Timestamp(CONSENSUS_TIME_1234567.getEpochSecond(), 0))
                        .build());
        given(context.consensusTime()).willReturn(CONSENSUS_TIME_1234567.plus(Duration.ofDays(2)));

        // Enable keyCandidateRoster
        given(context.configuration()).willReturn(newConfig(DEFAULT_STAKING_PERIOD_MINS, true));

        // Simulate an updated address book
        final var nodeStore = simulateNodes(RosterCase.NODE_1, RosterCase.NODE_2, RosterCase.NODE_3);
        given(dispatch.handleContext()).willReturn(handleContext);
        given(handleContext.storeFactory()).willReturn(storeFactory);
        given(storeFactory.readableStore(ReadableNodeStore.class)).willReturn(nodeStore);
        given(stack.getWritableStates(notNull())).willReturn(writableStates);
        given(writableStates.<TssMessageMapKey, TssMessageTransactionBody>get(TSS_MESSAGE_MAP_KEY))
                .willReturn(tssMessageState);
        given(writableStates.<TssVoteMapKey, TssVoteTransactionBody>get(TSS_VOTE_MAP_KEY))
                .willReturn(tssVoteState);
        given(writableStates.<EntityNumber, TssEncryptionKeyTransactionBody>get(TSS_ENCRYPTION_KEY_MAP_KEY))
                .willReturn(tssEncryptionKeyState);
        given(writableStates.<TssStatus>getSingleton(TSS_STATUS_KEY)).willReturn(tssStatusState);
        given(tssEncryptionKeyState.keys())
                .willReturn(List.of(new EntityNumber(0)).iterator());
        simulateCandidateAndActiveRosters();

        subject.process(dispatch, stack, context, StreamMode.RECORDS, false, Instant.EPOCH);
        verify(tssBaseService).setCandidateRoster(notNull(), notNull());
    }

    private ReadableNodeStore simulateNodes(Node... nodes) {
        final Map<EntityNumber, Node> translated = Arrays.stream(nodes)
                .collect(Collectors.toMap(
                        n -> EntityNumber.newBuilder().number(n.nodeId()).build(), node -> node));
        final WritableKVState<EntityNumber, Node> nodeWritableKVState = new MapWritableKVState<>(NODES_KEY, translated);
        given(writableStates.<EntityNumber, Node>get(NODES_KEY)).willReturn(nodeWritableKVState);
        final ReadableNodeStore nodeStore = new ReadableNodeStoreImpl(writableStates);
        given(context.readableStore(ReadableNodeStore.class)).willReturn(nodeStore);

        return nodeStore;
    }

    private void simulateCandidateAndActiveRosters() {
        given(rosterState.get())
                .willReturn(new RosterState(
                        RosterCase.CANDIDATE_ROSTER_HASH.value(),
                        List.of(RoundRosterPair.newBuilder()
                                .roundNumber(12345)
                                .activeRosterHash(RosterCase.ACTIVE_ROSTER_HASH.value())
                                .build())));
        given(writableStates.<RosterState>getSingleton(V0540RosterSchema.ROSTER_STATES_KEY))
                .willReturn(rosterState);
        given(writableStates.<ProtoBytes, Roster>get(V0540RosterSchema.ROSTER_KEY))
                .willReturn(new MapWritableKVState<>(
                        V0540RosterSchema.ROSTER_KEY,
                        Map.of(
                                RosterCase.CANDIDATE_ROSTER_HASH,
                                RosterCase.CURRENT_CANDIDATE_ROSTER,
                                RosterCase.ACTIVE_ROSTER_HASH,
                                RosterCase.ACTIVE_ROSTER)));
    }

    private Configuration newPeriodMinsConfig() {
        return newPeriodMinsConfig(DEFAULT_STAKING_PERIOD_MINS);
    }

    private Configuration newPeriodMinsConfig(final long periodMins) {
        return newConfig(periodMins, false);
    }

    private Configuration newConfig(final long periodMins, final boolean keyCandidateRoster) {
        return HederaTestConfigBuilder.create()
                .withConfigDataType(StakingConfig.class)
                .withValue("staking.periodMins", periodMins)
                .withValue("tss.keyCandidateRoster", keyCandidateRoster)
                .getOrCreateConfig();
    }

    public static class RosterCase {
        static final Bytes BYTES_1_2_3 = Bytes.wrap("1, 2, 3");
        static final Node NODE_1 = Node.newBuilder()
                .nodeId(1)
                .weight(10)
                .gossipCaCertificate(BYTES_1_2_3)
                .gossipEndpoint(ServiceEndpoint.newBuilder()
                        .ipAddressV4(Bytes.wrap("1, 1"))
                        .port(11)
                        .build())
                .build();
        public static final RosterEntry ROSTER_NODE_1 = RosterEntry.newBuilder()
                .nodeId(NODE_1.nodeId())
                .weight(NODE_1.weight())
                .gossipCaCertificate(NODE_1.gossipCaCertificate())
                .gossipEndpoint(NODE_1.gossipEndpoint())
                .build();
        static final Node NODE_2 = Node.newBuilder()
                .nodeId(2)
                .weight(20)
                .gossipCaCertificate(BYTES_1_2_3)
                .gossipEndpoint(ServiceEndpoint.newBuilder()
                        .ipAddressV4(Bytes.wrap("2, 2"))
                        .port(22)
                        .build())
                .build();
        public static final RosterEntry ROSTER_NODE_2 = RosterEntry.newBuilder()
                .nodeId(NODE_2.nodeId())
                .weight(NODE_2.weight())
                .gossipCaCertificate(NODE_2.gossipCaCertificate())
                .gossipEndpoint((ServiceEndpoint.newBuilder()
                        .ipAddressV4(Bytes.wrap("2, 2"))
                        .port(22)
                        .build()))
                .build();
        static final Node NODE_3 = Node.newBuilder()
                .nodeId(3)
                .weight(30)
                .gossipCaCertificate(BYTES_1_2_3)
                .gossipEndpoint(ServiceEndpoint.newBuilder()
                        .ipAddressV4(Bytes.wrap("3, 3"))
                        .port(33)
                        .build())
                .build();
        public static final RosterEntry ROSTER_NODE_3 = RosterEntry.newBuilder()
                .nodeId(NODE_3.nodeId())
                .weight(NODE_3.weight())
                .gossipCaCertificate(NODE_3.gossipCaCertificate())
                .gossipEndpoint(NODE_3.gossipEndpoint())
                .build();
        static final Node NODE_4 = Node.newBuilder()
                .nodeId(4)
                .weight(40)
                .gossipCaCertificate(BYTES_1_2_3)
                .gossipEndpoint(ServiceEndpoint.newBuilder()
                        .ipAddressV4(Bytes.wrap("4, 4"))
                        .port(44)
                        .build())
                .build();
        static final RosterEntry ROSTER_NODE_4 = RosterEntry.newBuilder()
                .nodeId(NODE_4.nodeId())
                .weight(NODE_4.weight())
                .gossipCaCertificate(NODE_4.gossipCaCertificate())
                .gossipEndpoint(NODE_4.gossipEndpoint())
                .build();

        public static final Roster CURRENT_CANDIDATE_ROSTER = Roster.newBuilder()
                .rosterEntries(List.of(ROSTER_NODE_1, ROSTER_NODE_2))
                .build();
        public static final Roster ACTIVE_ROSTER = Roster.newBuilder()
                .rosterEntries(ROSTER_NODE_1, ROSTER_NODE_2, ROSTER_NODE_3, ROSTER_NODE_4)
                .build();

        static final ProtoBytes CANDIDATE_ROSTER_HASH = ProtoBytes.newBuilder()
                .value(RosterUtils.hash(CURRENT_CANDIDATE_ROSTER).getBytes())
                .build();
        static final ProtoBytes ACTIVE_ROSTER_HASH = ProtoBytes.newBuilder()
                .value(RosterUtils.hash(ACTIVE_ROSTER).getBytes())
                .build();
    }
}
