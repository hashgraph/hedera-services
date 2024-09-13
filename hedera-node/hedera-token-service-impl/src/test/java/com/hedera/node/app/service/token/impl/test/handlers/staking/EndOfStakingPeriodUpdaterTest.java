/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.token.impl.test.handlers.staking;

import static com.hedera.node.app.service.token.Units.HBARS_TO_TINYBARS;
import static com.hedera.node.app.service.token.impl.handlers.BaseCryptoHandler.asAccount;
import static com.hedera.node.app.service.token.impl.handlers.staking.EndOfStakingPeriodUpdater.calculateWeightFromStake;
import static com.hedera.node.app.service.token.impl.handlers.staking.EndOfStakingPeriodUpdater.scaleUpWeightToStake;
import static com.hedera.node.app.service.token.impl.schemas.V0490TokenSchema.STAKING_INFO_KEY;
import static com.hedera.node.app.service.token.impl.schemas.V0490TokenSchema.STAKING_NETWORK_REWARDS_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.NetworkStakingRewards;
import com.hedera.hapi.node.state.token.StakingNodeInfo;
import com.hedera.hapi.node.transaction.ExchangeRateSet;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.service.token.impl.WritableNetworkStakingRewardsStore;
import com.hedera.node.app.service.token.impl.WritableStakingInfoStore;
import com.hedera.node.app.service.token.impl.handlers.staking.EndOfStakingPeriodUpdater;
import com.hedera.node.app.service.token.impl.handlers.staking.StakingRewardsHelper;
import com.hedera.node.app.service.token.impl.test.handlers.util.TestStoreFactory;
import com.hedera.node.app.service.token.records.NodeStakeUpdateStreamBuilder;
import com.hedera.node.app.service.token.records.TokenContext;
import com.hedera.node.app.spi.fixtures.state.MapWritableStates;
import com.hedera.node.app.spi.fixtures.util.LogCaptor;
import com.hedera.node.app.spi.fixtures.util.LogCaptureExtension;
import com.hedera.node.app.spi.fixtures.util.LoggingSubject;
import com.hedera.node.app.spi.fixtures.util.LoggingTarget;
import com.hedera.node.config.ConfigProvider;
import com.hedera.node.config.data.StakingConfig;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.swirlds.config.extensions.test.fixtures.TestConfigBuilder;
import com.swirlds.state.spi.WritableSingletonState;
import com.swirlds.state.spi.WritableSingletonStateBase;
import com.swirlds.state.spi.WritableStates;
import com.swirlds.state.test.fixtures.MapWritableKVState;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link EndOfStakingPeriodUpdater}.
 */
@ExtendWith({MockitoExtension.class, LogCaptureExtension.class})
public class EndOfStakingPeriodUpdaterTest {

    @LoggingTarget
    private LogCaptor logCaptor;

    @Mock
    private TokenContext context;

    @Mock
    private NodeStakeUpdateStreamBuilder nodeStakeUpdateRecordBuilder;

    private ReadableAccountStore accountStore;

    @LoggingSubject
    private EndOfStakingPeriodUpdater subject;

    private WritableStakingInfoStore stakingInfoStore;
    private WritableNetworkStakingRewardsStore stakingRewardsStore;

    private static final ConfigProvider DEFAULT_CONFIG_PROVIDER = HederaTestConfigBuilder.createConfigProvider();

    @BeforeEach
    void setup() {
        accountStore = TestStoreFactory.newReadableStoreWithAccounts(Account.newBuilder()
                .accountId(asAccount(800))
                .tinybarBalance(100_000_000_000L)
                .build());
        subject = new EndOfStakingPeriodUpdater(new StakingRewardsHelper(), DEFAULT_CONFIG_PROVIDER);
    }

    @Test
    void skipsEndOfStakingPeriodUpdatesIfStakingNotEnabled() {
        // Set up the staking config
        final var context = mock(TokenContext.class);
        given(context.configuration())
                .willReturn(
                        newStakingConfig().withValue("staking.isEnabled", false).getOrCreateConfig());
        // Set up the relevant stores (and data)
        final var stakingInfoStore = mock(WritableStakingInfoStore.class);
        final var stakingRewardsStore = mock(WritableNetworkStakingRewardsStore.class);

        subject.updateNodes(context, ExchangeRateSet.DEFAULT);

        verifyNoInteractions(stakingInfoStore, stakingRewardsStore);
    }

    @Test
    void convertsStakeValueToWeightCorrectly() {
        final var stake1 = 100_000_000L;
        final var stake2 = 123_456_789_123L;
        final var stake3 = 500_000_867_919L;
        final var stake4 = 900_000_789_111L;
        final var stake5 = 0L;
        final var totalStake = stake1 + stake2 + stake3 + stake4;
        final var updatedWeight1 = calculateWeightFromStake(stake1, totalStake, SUM_OF_CONSENSUS_WEIGHTS);
        final var updatedWeight2 = calculateWeightFromStake(stake2, totalStake, SUM_OF_CONSENSUS_WEIGHTS);
        final var updatedWeight3 = calculateWeightFromStake(stake3, totalStake, SUM_OF_CONSENSUS_WEIGHTS);
        final var updatedWeight4 = calculateWeightFromStake(stake4, totalStake, SUM_OF_CONSENSUS_WEIGHTS);
        final var updatedWeight5 = calculateWeightFromStake(stake5, totalStake, SUM_OF_CONSENSUS_WEIGHTS);
        final var totalWeight = updatedWeight1 + updatedWeight2 + updatedWeight3 + updatedWeight4 + updatedWeight5;
        assertThat(totalWeight).isLessThanOrEqualTo(SUM_OF_CONSENSUS_WEIGHTS);
        assertThat(updatedWeight1).isEqualTo(1);
        assertThat(updatedWeight2).isEqualTo((stake2 * SUM_OF_CONSENSUS_WEIGHTS) / totalStake);
        assertThat(updatedWeight3).isEqualTo((stake3 * SUM_OF_CONSENSUS_WEIGHTS) / totalStake);
        assertThat(updatedWeight4).isEqualTo((stake4 * SUM_OF_CONSENSUS_WEIGHTS) / totalStake);
        assertThat(updatedWeight5).isZero();
    }

    @Test
    void scalesBackWeightToStake() {
        final var minStake = 100_000_000L;
        final var maxStake = 900_000_789_000L;

        final var equalsMinStake = 100_000_000L;
        final var stakeInBetween1 = 123_456_789_123L;
        final var stakeInBetween2 = 123_456_000_000L;
        final var stakeEqualsMax = 900_000_789_000L;
        final var zeroStake = 0L;
        // calculate weights
        final var totalStake = equalsMinStake + stakeInBetween1 + stakeInBetween2 + stakeEqualsMax + zeroStake;
        final var weightForEqualsMin = calculateWeightFromStake(equalsMinStake, totalStake, SUM_OF_CONSENSUS_WEIGHTS);
        final var weightInBetween1 = calculateWeightFromStake(stakeInBetween1, totalStake, SUM_OF_CONSENSUS_WEIGHTS);
        final var weightInBetween2 = calculateWeightFromStake(stakeInBetween2, totalStake, SUM_OF_CONSENSUS_WEIGHTS);
        final var weightForEqualsMax = calculateWeightFromStake(stakeEqualsMax, totalStake, SUM_OF_CONSENSUS_WEIGHTS);
        final var weightForZeroStake = calculateWeightFromStake(zeroStake, totalStake, SUM_OF_CONSENSUS_WEIGHTS);
        final var totalWeight =
                weightForEqualsMin + weightInBetween1 + weightInBetween2 + weightForEqualsMax + weightForZeroStake;
        // total of all weights should be less than or equal to SUM_OF_CONSENSUS_WEIGHTS
        assertThat(totalWeight).isLessThanOrEqualTo(SUM_OF_CONSENSUS_WEIGHTS);
        assertThat(weightForEqualsMin).isEqualTo(1);
        assertThat(weightInBetween1).isEqualTo((stakeInBetween1 * SUM_OF_CONSENSUS_WEIGHTS) / totalStake);
        assertThat(weightInBetween2).isEqualTo((stakeInBetween2 * SUM_OF_CONSENSUS_WEIGHTS) / totalStake);
        assertThat(weightForEqualsMax).isEqualTo((stakeEqualsMax * SUM_OF_CONSENSUS_WEIGHTS) / totalStake);
        assertThat(weightForZeroStake).isZero();

        final var scaledStake1 =
                scaleUpWeightToStake(weightForEqualsMin, minStake, maxStake, totalStake, SUM_OF_CONSENSUS_WEIGHTS);
        final var scaledStake2 =
                scaleUpWeightToStake(weightInBetween1, minStake, maxStake, totalStake, SUM_OF_CONSENSUS_WEIGHTS);
        final var scaledStake3 =
                scaleUpWeightToStake(weightInBetween2, minStake, maxStake, totalStake, SUM_OF_CONSENSUS_WEIGHTS);
        final var scaledStake4 =
                scaleUpWeightToStake(weightForEqualsMax, minStake, maxStake, totalStake, SUM_OF_CONSENSUS_WEIGHTS);
        final var scaledStake5 =
                scaleUpWeightToStake(weightForZeroStake, minStake, maxStake, totalStake, SUM_OF_CONSENSUS_WEIGHTS);

        // calculate scaled weight based on the max weight allocated and max stake of all nodes
        final var maxWeight = Math.max(
                weightForEqualsMin, Math.max(weightInBetween1, Math.max(weightInBetween2, weightForEqualsMax)));
        final var expectedEqualScaledStake =
                ((maxStake - minStake) * (weightInBetween2 - 1)) / (maxWeight - 1) + minStake;
        // stake equals min stake
        assertThat(scaledStake1).isEqualTo(equalsMinStake);
        // Both these fall in the same bucket since their weight is the same. So, they get same scaled weight
        assertThat(scaledStake2).isEqualTo(expectedEqualScaledStake);
        assertThat(scaledStake3).isEqualTo(expectedEqualScaledStake);
        // stake equals max stake, will return max stake
        assertThat(scaledStake4).isEqualTo(stakeEqualsMax);
        // stake equals zero, will return zero
        assertThat(scaledStake5).isEqualTo(zeroStake);
    }

    @Test
    void deletedNodesGetsZeroPendingRewards() {
        commonSetup(
                1_000_000_000L,
                STAKING_INFO_1.copyBuilder().deleted(true).build(),
                STAKING_INFO_2,
                STAKING_INFO_3.copyBuilder().deleted(true).build());
        // Assert preconditions
        assertThat(STAKING_INFO_1.weight()).isZero();
        assertThat(STAKING_INFO_2.weight()).isZero();
        assertThat(STAKING_INFO_3.weight()).isZero();
        assertThat(STAKING_INFO_1.pendingRewards()).isZero();
        assertThat(STAKING_INFO_2.pendingRewards()).isZero();
        assertThat(STAKING_INFO_3.pendingRewards()).isZero();
        given(nodeStakeUpdateRecordBuilder.transaction(any())).willReturn(nodeStakeUpdateRecordBuilder);
        given(nodeStakeUpdateRecordBuilder.memo(any())).willReturn(nodeStakeUpdateRecordBuilder);
        given(nodeStakeUpdateRecordBuilder.exchangeRate(ExchangeRateSet.DEFAULT))
                .willReturn(nodeStakeUpdateRecordBuilder);

        subject.updateNodes(context, ExchangeRateSet.DEFAULT);

        assertThat(stakingRewardsStore.totalStakeRewardStart())
                .isEqualTo(STAKE_TO_REWARD_1 + STAKE_TO_REWARD_2 + STAKE_TO_REWARD_3);
        assertThat(stakingRewardsStore.totalStakedStart()).isEqualTo(130000000000L);
        final var resultStakingInfo1 = stakingInfoStore.get(NODE_NUM_1.number());
        final var resultStakingInfo2 = stakingInfoStore.get(NODE_NUM_2.number());
        final var resultStakingInfo3 = stakingInfoStore.get(NODE_NUM_3.number());
        assertThat(resultStakingInfo1.stake()).isEqualTo(80000000000L);
        assertThat(resultStakingInfo2.stake()).isEqualTo(50000000000L);
        assertThat(resultStakingInfo3.stake()).isZero();
        assertThat(resultStakingInfo1.unclaimedStakeRewardStart()).isZero();
        assertThat(resultStakingInfo2.unclaimedStakeRewardStart()).isZero();
        assertThat(resultStakingInfo3.unclaimedStakeRewardStart()).isZero();
        assertThat(resultStakingInfo1.rewardSumHistory()).isEqualTo(List.of(86L, 6L, 5L));
        assertThat(resultStakingInfo2.rewardSumHistory()).isEqualTo(List.of(101L, 1L, 1L));
        assertThat(resultStakingInfo3.rewardSumHistory()).isEqualTo(List.of(11L, 3L, 1L));
        assertThat(resultStakingInfo1.weight()).isZero();
        assertThat(resultStakingInfo2.weight()).isEqualTo(192);
        assertThat(resultStakingInfo3.weight()).isZero();
        assertThat(resultStakingInfo1.pendingRewards()).isZero();
        assertThat(resultStakingInfo2.pendingRewards()).isEqualTo(63000L);
        assertThat(resultStakingInfo3.pendingRewards()).isZero();
        assertThat(resultStakingInfo1.weight() + resultStakingInfo2.weight() + resultStakingInfo3.weight())
                .isLessThanOrEqualTo(SUM_OF_CONSENSUS_WEIGHTS);

        assertThat(logCaptor.infoLogs()).contains("Non-zero reward sum history for node number 1 is now [86, 6, 5]");
        assertThat(logCaptor.infoLogs()).contains("Non-zero reward sum history for node number 2 is now [101, 1, 1]");
        assertThat(logCaptor.infoLogs()).contains("Non-zero reward sum history for node number 3 is now [11, 3, 1]");
    }

    @Test
    void doesNothingWhenStakingConfigIsNotEnabled() {
        given(context.configuration())
                .willReturn(
                        newStakingConfig().withValue("staking.isEnabled", false).getOrCreateConfig());
        // Set up the relevant stores (and data)
        final var stakingInfoStore = mock(WritableStakingInfoStore.class);
        final var stakingRewardsStore = mock(WritableNetworkStakingRewardsStore.class);

        subject.updateNodes(context, ExchangeRateSet.DEFAULT);

        verifyNoInteractions(stakingInfoStore, stakingRewardsStore);
        assertThat(logCaptor.infoLogs()).contains("Staking not enabled, nothing to do");
    }

    @Test
    void calculatesNewEndOfPeriodStakingFieldsAsExpected() {
        commonSetup(1_000_000_000L, STAKING_INFO_1, STAKING_INFO_2, STAKING_INFO_3);

        // Assert preconditions
        assertThat(STAKING_INFO_1.weight()).isZero();
        assertThat(STAKING_INFO_2.weight()).isZero();
        assertThat(STAKING_INFO_3.weight()).isZero();
        assertThat(STAKING_INFO_1.pendingRewards()).isZero();
        assertThat(STAKING_INFO_2.pendingRewards()).isZero();
        assertThat(STAKING_INFO_3.pendingRewards()).isZero();
        given(nodeStakeUpdateRecordBuilder.transaction(any())).willReturn(nodeStakeUpdateRecordBuilder);
        given(nodeStakeUpdateRecordBuilder.memo(any())).willReturn(nodeStakeUpdateRecordBuilder);
        given(nodeStakeUpdateRecordBuilder.exchangeRate(ExchangeRateSet.DEFAULT))
                .willReturn(nodeStakeUpdateRecordBuilder);

        subject.updateNodes(context, ExchangeRateSet.DEFAULT);

        assertThat(stakingRewardsStore.totalStakeRewardStart())
                .isEqualTo(STAKE_TO_REWARD_1 + STAKE_TO_REWARD_2 + STAKE_TO_REWARD_3);
        assertThat(stakingRewardsStore.totalStakedStart()).isEqualTo(130000000000L);
        final var resultStakingInfo1 = stakingInfoStore.get(NODE_NUM_1.number());
        final var resultStakingInfo2 = stakingInfoStore.get(NODE_NUM_2.number());
        final var resultStakingInfo3 = stakingInfoStore.get(NODE_NUM_3.number());
        assertThat(resultStakingInfo1.stake()).isEqualTo(80000000000L);
        assertThat(resultStakingInfo2.stake()).isEqualTo(50000000000L);
        assertThat(resultStakingInfo3.stake()).isZero();
        assertThat(resultStakingInfo1.unclaimedStakeRewardStart()).isZero();
        assertThat(resultStakingInfo2.unclaimedStakeRewardStart()).isZero();
        assertThat(resultStakingInfo3.unclaimedStakeRewardStart()).isZero();
        assertThat(resultStakingInfo1.rewardSumHistory()).isEqualTo(List.of(86L, 6L, 5L));
        assertThat(resultStakingInfo2.rewardSumHistory()).isEqualTo(List.of(101L, 1L, 1L));
        assertThat(resultStakingInfo3.rewardSumHistory()).isEqualTo(List.of(11L, 3L, 1L));
        assertThat(resultStakingInfo1.weight()).isEqualTo(307);
        assertThat(resultStakingInfo2.weight()).isEqualTo(192);
        assertThat(resultStakingInfo3.weight()).isZero();
        assertThat(resultStakingInfo1.pendingRewards()).isEqualTo(72000);
        assertThat(resultStakingInfo2.pendingRewards()).isEqualTo(63000L);
        assertThat(resultStakingInfo3.pendingRewards()).isEqualTo(72000L);
        assertThat(resultStakingInfo1.weight() + resultStakingInfo2.weight() + resultStakingInfo3.weight())
                .isLessThanOrEqualTo(SUM_OF_CONSENSUS_WEIGHTS);
    }

    @Test
    void calculatesNewEndOfPeriodStakingFieldsAsExpectedWhenMaxStakeIsLessThanTotalStake() {
        commonSetup(1_000_000_000L, STAKING_INFO_1, STAKING_INFO_2, STAKING_INFO_3);
        given(context.configuration())
                .willReturn(newStakingConfig()
                        .withValue("staking.rewardBalanceThreshold", 100000)
                        .withValue("staking.maxStakeRewarded", 0L)
                        .getOrCreateConfig());
        given(nodeStakeUpdateRecordBuilder.transaction(any())).willReturn(nodeStakeUpdateRecordBuilder);
        given(nodeStakeUpdateRecordBuilder.memo(any())).willReturn(nodeStakeUpdateRecordBuilder);
        given(nodeStakeUpdateRecordBuilder.exchangeRate(ExchangeRateSet.DEFAULT))
                .willReturn(nodeStakeUpdateRecordBuilder);

        subject.updateNodes(context, ExchangeRateSet.DEFAULT);

        assertThat(stakingRewardsStore.totalStakeRewardStart())
                .isEqualTo(STAKE_TO_REWARD_1 + STAKE_TO_REWARD_2 + STAKE_TO_REWARD_3);
        assertThat(stakingRewardsStore.totalStakedStart()).isEqualTo(130000000000L);
        final var resultStakingInfo1 = stakingInfoStore.get(NODE_NUM_1.number());
        final var resultStakingInfo2 = stakingInfoStore.get(NODE_NUM_2.number());
        final var resultStakingInfo3 = stakingInfoStore.get(NODE_NUM_3.number());
        assertThat(resultStakingInfo1.stake()).isEqualTo(80000000000L);
        assertThat(resultStakingInfo2.stake()).isEqualTo(50000000000L);
        assertThat(resultStakingInfo3.stake()).isZero();
        assertThat(resultStakingInfo1.unclaimedStakeRewardStart()).isZero();
        assertThat(resultStakingInfo2.unclaimedStakeRewardStart()).isZero();
        assertThat(resultStakingInfo3.unclaimedStakeRewardStart()).isZero();
        assertThat(resultStakingInfo1.rewardSumHistory()).isEqualTo(List.of(6L, 6L, 5L));
        assertThat(resultStakingInfo2.rewardSumHistory()).isEqualTo(List.of(1L, 1L, 1L));
        assertThat(resultStakingInfo3.rewardSumHistory()).isEqualTo(List.of(3L, 3L, 1L));
        assertThat(resultStakingInfo1.weight()).isEqualTo(307);
        assertThat(resultStakingInfo2.weight()).isEqualTo(192);
        assertThat(resultStakingInfo3.weight()).isZero();
        // Since max stake rewarded is 0, all pending rewards should be 0
        assertThat(resultStakingInfo1.pendingRewards()).isEqualTo(0L);
        assertThat(resultStakingInfo2.pendingRewards()).isEqualTo(0L);
        assertThat(resultStakingInfo3.pendingRewards()).isEqualTo(0L);
        assertThat(resultStakingInfo1.weight() + resultStakingInfo2.weight() + resultStakingInfo3.weight())
                .isLessThanOrEqualTo(SUM_OF_CONSENSUS_WEIGHTS);
    }

    @Test
    void zeroWholeHbarsStakedCaseWorks() {
        commonSetup(
                0L,
                STAKING_INFO_1.copyBuilder().stakeRewardStart(0).build(),
                STAKING_INFO_2.copyBuilder().stakeRewardStart(0).build(),
                STAKING_INFO_3.copyBuilder().stakeRewardStart(0).build());
        assertThat(stakingRewardsStore.totalStakeRewardStart()).isZero();
        given(nodeStakeUpdateRecordBuilder.transaction(any())).willReturn(nodeStakeUpdateRecordBuilder);
        given(nodeStakeUpdateRecordBuilder.memo(any())).willReturn(nodeStakeUpdateRecordBuilder);
        given(nodeStakeUpdateRecordBuilder.exchangeRate(ExchangeRateSet.DEFAULT))
                .willReturn(nodeStakeUpdateRecordBuilder);

        subject.updateNodes(context, ExchangeRateSet.DEFAULT);

        assertThat(stakingRewardsStore.totalStakeRewardStart())
                .isEqualTo(STAKE_TO_REWARD_1 + STAKE_TO_REWARD_2 + STAKE_TO_REWARD_3);
        assertThat(stakingRewardsStore.totalStakedStart()).isEqualTo(130000000000L);
        final var resultStakingInfo1 = stakingInfoStore.get(NODE_NUM_1.number());
        final var resultStakingInfo2 = stakingInfoStore.get(NODE_NUM_2.number());
        final var resultStakingInfo3 = stakingInfoStore.get(NODE_NUM_3.number());
        assertThat(resultStakingInfo1.rewardSumHistory()).isEqualTo(List.of(6L, 6L, 5L));
        assertThat(resultStakingInfo2.rewardSumHistory()).isEqualTo(List.of(1L, 1L, 1L));
        assertThat(resultStakingInfo3.rewardSumHistory()).isEqualTo(List.of(3L, 3L, 1L));
    }

    @Test
    void returnsZeroWeightIfTotalStakeOfAllNodeIsZero() {
        final var weight = calculateWeightFromStake(10, 0, 500);
        assertThat(weight).isEqualTo(0);
        assertThat(logCaptor.warnLogs()).contains("Total stake of all nodes should be greater than 0. But got 0");
    }

    @Test
    void returnsZeroScaledUpWeightIfTotalStakeOfAllNodeIsZero() {
        final var weight = scaleUpWeightToStake(10, 1000, 1000, 0, 500);
        assertThat(weight).isEqualTo(0);
        assertThat(logCaptor.warnLogs())
                .contains(
                        "Total stake of all nodes is 0, "
                                + "which shouldn't happen (weight=10, minStake=1000, maxStake=1000, sumOfConsensusWeights=500)");
    }

    @Test
    void calculatesMidnightTimeCorrectly() {
        final var consensusSecs = 1653660350L;
        final var consensusNanos = 12345L;
        final var expectedNanos = 999_999_999;
        final var consensusTime = Instant.ofEpochSecond(consensusSecs, consensusNanos);
        final var expectedMidnightTime =
                Timestamp.newBuilder().seconds(1653609599L).nanos(expectedNanos).build();

        assertThat(subject.lastInstantOfPreviousPeriodFor(consensusTime)).isEqualTo(expectedMidnightTime);
    }

    private void commonSetup(
            final long totalStakeRewardStart,
            @NonNull final StakingNodeInfo info1,
            @NonNull final StakingNodeInfo info2,
            @NonNull final StakingNodeInfo info3) {
        given(context.consensusTime()).willReturn(Instant.now());

        // Create staking config
        final var stakingConfig = newStakingConfig().getOrCreateConfig();
        given(context.configuration()).willReturn(stakingConfig);

        // Create account store (with data)
        given(context.readableStore(ReadableAccountStore.class)).willReturn(accountStore);

        // Create staking info store (with data)
        MapWritableKVState<EntityNumber, StakingNodeInfo> stakingInfosState = new MapWritableKVState.Builder<
                        EntityNumber, StakingNodeInfo>(STAKING_INFO_KEY)
                .value(NODE_NUM_1, info1)
                .value(NODE_NUM_2, info2)
                .value(NODE_NUM_3, info3)
                .build();
        stakingInfoStore =
                new WritableStakingInfoStore(new MapWritableStates(Map.of(STAKING_INFO_KEY, stakingInfosState)));
        given(context.writableStore(WritableStakingInfoStore.class)).willReturn(stakingInfoStore);

        // Create staking reward store (with data)
        final var backingValue = new AtomicReference<>(new NetworkStakingRewards(true, totalStakeRewardStart, 0, 0));
        WritableSingletonState<NetworkStakingRewards> stakingRewardsState =
                new WritableSingletonStateBase<>(STAKING_NETWORK_REWARDS_KEY, backingValue::get, backingValue::set);
        final var states = mock(WritableStates.class);
        given(states.getSingleton(STAKING_NETWORK_REWARDS_KEY))
                .willReturn((WritableSingletonState) stakingRewardsState);
        stakingRewardsStore = new WritableNetworkStakingRewardsStore(states);
        given(context.writableStore(WritableNetworkStakingRewardsStore.class)).willReturn(stakingRewardsStore);
        given(context.addPrecedingChildRecordBuilder(NodeStakeUpdateStreamBuilder.class))
                .willReturn(nodeStakeUpdateRecordBuilder);
        given(context.knownNodeIds()).willReturn(Set.of(NODE_NUM_1.number(), NODE_NUM_2.number(), NODE_NUM_3.number()));
    }

    private static final int SUM_OF_CONSENSUS_WEIGHTS = 500;
    private static final long MIN_STAKE = 100L * HBARS_TO_TINYBARS;
    private static final long MAX_STAKE = 800L * HBARS_TO_TINYBARS;
    private static final long STAKE_TO_REWARD_1 = 700L * HBARS_TO_TINYBARS;
    private static final long STAKE_TO_REWARD_2 = 300L * HBARS_TO_TINYBARS;
    private static final long STAKE_TO_REWARD_3 = 30L * HBARS_TO_TINYBARS;
    private static final long STAKE_TO_NOT_REWARD_1 = 300L * HBARS_TO_TINYBARS;
    private static final long STAKE_TO_NOT_REWARD_2 = 200L * HBARS_TO_TINYBARS;
    private static final long STAKE_TO_NOT_REWARD_3 = 20L * HBARS_TO_TINYBARS;
    private static final long STAKED_REWARD_START_1 = 1_000L * HBARS_TO_TINYBARS;
    private static final long UNCLAIMED_STAKED_REWARD_START_1 = STAKED_REWARD_START_1 / 10;
    private static final long STAKED_REWARD_START_2 = 700L * HBARS_TO_TINYBARS;
    private static final long UNCLAIMED_STAKED_REWARD_START_2 = STAKED_REWARD_START_2 / 10;
    private static final long STAKED_REWARD_START_3 = 10_000L * HBARS_TO_TINYBARS;
    private static final long UNCLAIMED_STAKED_REWARD_START_3 = STAKED_REWARD_START_3 / 10;
    private static final long STAKE_1 = 2_000L * HBARS_TO_TINYBARS;
    private static final long STAKE_2 = 750L * HBARS_TO_TINYBARS;
    private static final long STAKE_3 = 75L * HBARS_TO_TINYBARS;
    private static final List<Long> REWARD_SUM_HISTORY_1 = List.of(8L, 7L, 2L);
    private static final List<Long> REWARD_SUM_HISTORY_2 = List.of(5L, 5L, 4L);
    private static final List<Long> REWARD_SUM_HISTORY_3 = List.of(4L, 2L, 1L);
    /**
     * Node number 1 for the test nodes.
     */
    public static final EntityNumber NODE_NUM_1 =
            EntityNumber.newBuilder().number(1).build();
    /**
     * Node number 2 for the test nodes.
     */
    public static final EntityNumber NODE_NUM_2 =
            EntityNumber.newBuilder().number(2).build();
    /**
     * Node number 3 for the test nodes.
     */
    public static final EntityNumber NODE_NUM_3 =
            EntityNumber.newBuilder().number(3).build();
    /**
     * Node number 4 for the test nodes.
     */
    public static final EntityNumber NODE_NUM_4 =
            EntityNumber.newBuilder().number(4).build();
    /**
     * Node number 8 for the test nodes.
     */
    public static final EntityNumber NODE_NUM_8 =
            EntityNumber.newBuilder().number(8).build();
    /** Staking info for node 1. */
    public static final StakingNodeInfo STAKING_INFO_1 = StakingNodeInfo.newBuilder()
            .nodeNumber(NODE_NUM_1.number())
            .minStake(MIN_STAKE)
            .maxStake(MAX_STAKE)
            .stakeToReward(STAKE_TO_REWARD_1)
            .stakeToNotReward(STAKE_TO_NOT_REWARD_1)
            .stakeRewardStart(STAKED_REWARD_START_1)
            .unclaimedStakeRewardStart(UNCLAIMED_STAKED_REWARD_START_1)
            .stake(STAKE_1)
            .rewardSumHistory(REWARD_SUM_HISTORY_1)
            .deleted(false)
            .weight(0)
            .build();
    /** Staking info for node 2. */
    public static final StakingNodeInfo STAKING_INFO_2 = StakingNodeInfo.newBuilder()
            .nodeNumber(NODE_NUM_2.number())
            .minStake(MIN_STAKE)
            .maxStake(MAX_STAKE)
            .stakeToReward(STAKE_TO_REWARD_2)
            .stakeToNotReward(STAKE_TO_NOT_REWARD_2)
            .stakeRewardStart(STAKED_REWARD_START_2)
            .unclaimedStakeRewardStart(UNCLAIMED_STAKED_REWARD_START_2)
            .stake(STAKE_2)
            .rewardSumHistory(REWARD_SUM_HISTORY_2)
            .deleted(false)
            .weight(0)
            .build();
    /** Staking info for node 3. */
    public static final StakingNodeInfo STAKING_INFO_3 = StakingNodeInfo.newBuilder()
            .nodeNumber(NODE_NUM_3.number())
            .minStake(MIN_STAKE)
            .maxStake(MAX_STAKE)
            .stakeToReward(STAKE_TO_REWARD_3)
            .stakeToNotReward(STAKE_TO_NOT_REWARD_3)
            .stakeRewardStart(STAKED_REWARD_START_3)
            .unclaimedStakeRewardStart(UNCLAIMED_STAKED_REWARD_START_3)
            .stake(STAKE_3)
            .rewardSumHistory(REWARD_SUM_HISTORY_3)
            .deleted(false)
            .weight(0)
            .build();

    private static TestConfigBuilder newStakingConfig() {
        return HederaTestConfigBuilder.create()
                .withConfigDataType(StakingConfig.class)
                .withValue("staking.isEnabled", true)
                .withValue("staking.rewardRate", 100L)
                .withValue("staking.sumOfConsensusWeights", SUM_OF_CONSENSUS_WEIGHTS)
                .withValue("staking.maxStakeRewarded", Long.MAX_VALUE)
                .withValue("staking.perHbarRewardRate", 100L)
                .withValue("staking.rewardBalanceThreshold", 0);
    }
}
