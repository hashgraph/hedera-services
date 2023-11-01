/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

import static com.hedera.node.app.service.token.impl.TokenServiceImpl.STAKING_INFO_KEY;
import static com.hedera.node.app.service.token.impl.TokenServiceImpl.STAKING_NETWORK_REWARDS_KEY;
import static com.hedera.node.app.service.token.impl.handlers.BaseCryptoHandler.asAccount;
import static com.hedera.node.app.service.token.impl.handlers.staking.EndOfStakingPeriodUpdater.calculateWeightFromStake;
import static com.hedera.node.app.service.token.impl.handlers.staking.EndOfStakingPeriodUpdater.scaleUpWeightToStake;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.NetworkStakingRewards;
import com.hedera.hapi.node.state.token.StakingNodeInfo;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.service.token.impl.WritableNetworkStakingRewardsStore;
import com.hedera.node.app.service.token.impl.WritableStakingInfoStore;
import com.hedera.node.app.service.token.impl.handlers.staking.EndOfStakingPeriodUpdater;
import com.hedera.node.app.service.token.impl.handlers.staking.StakingRewardsHelper;
import com.hedera.node.app.service.token.impl.test.fixtures.FakeNodeStakeUpdateRecordBuilder;
import com.hedera.node.app.service.token.impl.test.handlers.util.TestStoreFactory;
import com.hedera.node.app.service.token.records.NodeStakeUpdateRecordBuilder;
import com.hedera.node.app.service.token.records.TokenContext;
import com.hedera.node.app.spi.fixtures.numbers.FakeHederaNumbers;
import com.hedera.node.app.spi.fixtures.state.MapWritableKVState;
import com.hedera.node.app.spi.fixtures.state.MapWritableStates;
import com.hedera.node.app.spi.state.WritableSingletonState;
import com.hedera.node.app.spi.state.WritableSingletonStateBase;
import com.hedera.node.app.spi.state.WritableStates;
import com.hedera.node.config.data.StakingConfig;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.swirlds.test.framework.config.TestConfigBuilder;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EndOfStakingPeriodUpdaterTest {
    private ReadableAccountStore accountStore;

    private EndOfStakingPeriodUpdater subject;
    private NodeStakeUpdateRecordBuilder nodeStakeUpdateRecordBuilder;

    @BeforeEach
    void setup() {
        accountStore = TestStoreFactory.newReadableStoreWithAccounts(Account.newBuilder()
                .accountId(asAccount(800))
                .tinybarBalance(100_000_000_000L)
                .build());
        subject = new EndOfStakingPeriodUpdater(new FakeHederaNumbers(), new StakingRewardsHelper());
        this.nodeStakeUpdateRecordBuilder = new FakeNodeStakeUpdateRecordBuilder().create();
    }

    @Test
    void skipsEndOfStakingPeriodUpdatesIfStakingNotEnabled() {
        final var consensusTime = Instant.now();

        // Set up the staking config
        final var context = mock(TokenContext.class);
        given(context.configuration())
                .willReturn(
                        newStakingConfig().withValue("staking.isEnabled", false).getOrCreateConfig());
        // Set up the relevant stores (and data)
        final var stakingInfoStore = mock(WritableStakingInfoStore.class);
        final var stakingRewardsStore = mock(WritableNetworkStakingRewardsStore.class);

        subject.updateNodes(context);

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
        Assertions.assertThat(totalWeight).isLessThanOrEqualTo(SUM_OF_CONSENSUS_WEIGHTS);
        Assertions.assertThat(updatedWeight1).isEqualTo(1);
        Assertions.assertThat(updatedWeight2).isEqualTo((stake2 * SUM_OF_CONSENSUS_WEIGHTS) / totalStake);
        Assertions.assertThat(updatedWeight3).isEqualTo((stake3 * SUM_OF_CONSENSUS_WEIGHTS) / totalStake);
        Assertions.assertThat(updatedWeight4).isEqualTo((stake4 * SUM_OF_CONSENSUS_WEIGHTS) / totalStake);
        Assertions.assertThat(updatedWeight5).isZero();
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
        Assertions.assertThat(totalWeight).isLessThanOrEqualTo(SUM_OF_CONSENSUS_WEIGHTS);
        Assertions.assertThat(weightForEqualsMin).isEqualTo(1);
        Assertions.assertThat(weightInBetween1).isEqualTo((stakeInBetween1 * SUM_OF_CONSENSUS_WEIGHTS) / totalStake);
        Assertions.assertThat(weightInBetween2).isEqualTo((stakeInBetween2 * SUM_OF_CONSENSUS_WEIGHTS) / totalStake);
        Assertions.assertThat(weightForEqualsMax).isEqualTo((stakeEqualsMax * SUM_OF_CONSENSUS_WEIGHTS) / totalStake);
        Assertions.assertThat(weightForZeroStake).isZero();

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
        Assertions.assertThat(scaledStake1).isEqualTo(equalsMinStake);
        // Both these fall in the same bucket since their weight is the same. So, they get same scaled weight
        Assertions.assertThat(scaledStake2).isEqualTo(expectedEqualScaledStake);
        Assertions.assertThat(scaledStake3).isEqualTo(expectedEqualScaledStake);
        // stake equals max stake, will return max stake
        Assertions.assertThat(scaledStake4).isEqualTo(stakeEqualsMax);
        // stake equals zero, will return zero
        Assertions.assertThat(scaledStake5).isEqualTo(zeroStake);
    }

    @Test
    void calculatesNewTotalStakesAsExpected() {
        final var context = mock(TokenContext.class);
        given(context.consensusTime()).willReturn(Instant.now());

        // Create staking config
        final var stakingConfig = newStakingConfig().getOrCreateConfig();
        given(context.configuration()).willReturn(stakingConfig);

        // Create account store (with data)
        given(context.readableStore(ReadableAccountStore.class)).willReturn(accountStore);

        // Create staking info store (with data)
        final var stakingInfosState = new MapWritableKVState.Builder<EntityNumber, StakingNodeInfo>(STAKING_INFO_KEY)
                .value(NODE_NUM_1, STAKING_INFO_1)
                .value(NODE_NUM_2, STAKING_INFO_2)
                .value(NODE_NUM_3, STAKING_INFO_3)
                .build();
        final var stakingInfoStore =
                new WritableStakingInfoStore(new MapWritableStates(Map.of(STAKING_INFO_KEY, stakingInfosState)));
        given(context.writableStore(WritableStakingInfoStore.class)).willReturn(stakingInfoStore);

        // Create staking reward store (with data)
        final var backingValue = new AtomicReference<>(new NetworkStakingRewards(true, 1_000_000_000L, 0, 0));
        final var stakingRewardsState =
                new WritableSingletonStateBase<>(STAKING_NETWORK_REWARDS_KEY, backingValue::get, backingValue::set);
        final var states = mock(WritableStates.class);
        given(states.getSingleton(STAKING_NETWORK_REWARDS_KEY))
                .willReturn((WritableSingletonState) stakingRewardsState);
        final var stakingRewardsStore = new WritableNetworkStakingRewardsStore(states);
        given(context.writableStore(WritableNetworkStakingRewardsStore.class)).willReturn(stakingRewardsStore);
        given(context.addPrecedingChildRecordBuilder(NodeStakeUpdateRecordBuilder.class))
                .willReturn(nodeStakeUpdateRecordBuilder);

        // Assert preconditions
        Assertions.assertThat(STAKING_INFO_1.weight()).isZero();
        Assertions.assertThat(STAKING_INFO_2.weight()).isZero();
        Assertions.assertThat(STAKING_INFO_3.weight()).isZero();

        subject.updateNodes(context);

        Assertions.assertThat(stakingRewardsStore.totalStakeRewardStart())
                .isEqualTo(STAKE_TO_REWARD_1 + STAKE_TO_REWARD_2 + STAKE_TO_REWARD_3);
        Assertions.assertThat(stakingRewardsStore.totalStakedStart()).isEqualTo(1300L);
        final var resultStakingInfo1 = stakingInfoStore.get(NODE_NUM_1.number());
        final var resultStakingInfo2 = stakingInfoStore.get(NODE_NUM_2.number());
        final var resultStakingInfo3 = stakingInfoStore.get(NODE_NUM_3.number());
        Assertions.assertThat(resultStakingInfo1.stake()).isEqualTo(800L);
        Assertions.assertThat(resultStakingInfo2.stake()).isEqualTo(500L);
        Assertions.assertThat(resultStakingInfo3.stake()).isZero();
        Assertions.assertThat(resultStakingInfo1.unclaimedStakeRewardStart()).isZero();
        Assertions.assertThat(resultStakingInfo2.unclaimedStakeRewardStart()).isZero();
        Assertions.assertThat(resultStakingInfo3.unclaimedStakeRewardStart()).isZero();
        Assertions.assertThat(resultStakingInfo1.rewardSumHistory()).isEqualTo(List.of(86L, 6L, 5L));
        Assertions.assertThat(resultStakingInfo2.rewardSumHistory()).isEqualTo(List.of(101L, 1L, 1L));
        Assertions.assertThat(resultStakingInfo3.rewardSumHistory()).isEqualTo(List.of(11L, 3L, 1L));
        Assertions.assertThat(resultStakingInfo1.weight()).isEqualTo(307);
        Assertions.assertThat(resultStakingInfo2.weight()).isEqualTo(192);
        Assertions.assertThat(resultStakingInfo3.weight()).isZero();
        Assertions.assertThat(resultStakingInfo1.weight() + resultStakingInfo2.weight() + resultStakingInfo3.weight())
                .isLessThanOrEqualTo(SUM_OF_CONSENSUS_WEIGHTS);
    }

    @Test
    void calculatesMidnightTimeCorrectly() {
        final var consensusSecs = 1653660350L;
        final var consensusNanos = 12345L;
        final var expectedNanos = 999_999_999;
        final var consensusTime = Instant.ofEpochSecond(consensusSecs, consensusNanos);
        final var expectedMidnightTime =
                Timestamp.newBuilder().seconds(1653609599L).nanos(expectedNanos).build();

        Assertions.assertThat(subject.lastInstantOfPreviousPeriodFor(consensusTime))
                .isEqualTo(expectedMidnightTime);
    }

    private static final int SUM_OF_CONSENSUS_WEIGHTS = 500;
    private static final long MIN_STAKE = 100L;
    private static final long MAX_STAKE = 800L;
    private static final long STAKE_TO_REWARD_1 = 700L;
    private static final long STAKE_TO_REWARD_2 = 300L;
    private static final long STAKE_TO_REWARD_3 = 30L;
    private static final long STAKE_TO_NOT_REWARD_1 = 300L;
    private static final long STAKE_TO_NOT_REWARD_2 = 200L;
    private static final long STAKE_TO_NOT_REWARD_3 = 20L;
    private static final long STAKED_REWARD_START_1 = 1_000L;
    private static final long UNCLAIMED_STAKED_REWARD_START_1 = STAKED_REWARD_START_1 / 10;
    private static final long STAKED_REWARD_START_2 = 700L;
    private static final long UNCLAIMED_STAKED_REWARD_START_2 = STAKED_REWARD_START_2 / 10;
    private static final long STAKED_REWARD_START_3 = 10_000L;
    private static final long UNCLAIMED_STAKED_REWARD_START_3 = STAKED_REWARD_START_3 / 10;
    private static final long STAKE_1 = 2_000L;
    private static final long STAKE_2 = 750L;
    private static final long STAKE_3 = 75L;
    private static final List<Long> REWARD_SUM_HISTORY_1 = List.of(8L, 7L, 2L);
    private static final List<Long> REWARD_SUM_HISTORY_2 = List.of(5L, 5L, 4L);
    private static final List<Long> REWARD_SUM_HISTORY_3 = List.of(4L, 2L, 1L);
    private static final EntityNumber NODE_NUM_1 =
            EntityNumber.newBuilder().number(1).build();
    private static final EntityNumber NODE_NUM_2 =
            EntityNumber.newBuilder().number(2).build();
    private static final EntityNumber NODE_NUM_3 =
            EntityNumber.newBuilder().number(3).build();
    private static final StakingNodeInfo STAKING_INFO_1 = StakingNodeInfo.newBuilder()
            .nodeNumber(NODE_NUM_1.number())
            .minStake(MIN_STAKE)
            .maxStake(MAX_STAKE)
            .stakeToReward(STAKE_TO_REWARD_1)
            .stakeToNotReward(STAKE_TO_NOT_REWARD_1)
            .stakeRewardStart(STAKED_REWARD_START_1)
            .unclaimedStakeRewardStart(UNCLAIMED_STAKED_REWARD_START_1)
            .stake(STAKE_1)
            .rewardSumHistory(REWARD_SUM_HISTORY_1)
            .weight(0)
            .build();
    private static final StakingNodeInfo STAKING_INFO_2 = StakingNodeInfo.newBuilder()
            .nodeNumber(NODE_NUM_2.number())
            .minStake(MIN_STAKE)
            .maxStake(MAX_STAKE)
            .stakeToReward(STAKE_TO_REWARD_2)
            .stakeToNotReward(STAKE_TO_NOT_REWARD_2)
            .stakeRewardStart(STAKED_REWARD_START_2)
            .unclaimedStakeRewardStart(UNCLAIMED_STAKED_REWARD_START_2)
            .stake(STAKE_2)
            .rewardSumHistory(REWARD_SUM_HISTORY_2)
            .weight(0)
            .build();
    private static final StakingNodeInfo STAKING_INFO_3 = StakingNodeInfo.newBuilder()
            .nodeNumber(NODE_NUM_3.number())
            .minStake(MIN_STAKE)
            .maxStake(MAX_STAKE)
            .stakeToReward(STAKE_TO_REWARD_3)
            .stakeToNotReward(STAKE_TO_NOT_REWARD_3)
            .stakeRewardStart(STAKED_REWARD_START_3)
            .unclaimedStakeRewardStart(UNCLAIMED_STAKED_REWARD_START_3)
            .stake(STAKE_3)
            .rewardSumHistory(REWARD_SUM_HISTORY_3)
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
