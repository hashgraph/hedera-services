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

package com.hedera.node.app.service.mono.ledger.accounts.staking;

import static com.hedera.node.app.service.mono.context.properties.PropertyNames.ACCOUNTS_STAKING_REWARD_ACCOUNT;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.hedera.node.app.service.mono.context.properties.GlobalDynamicProperties;
import com.hedera.node.app.service.mono.context.properties.PropertySource;
import com.hedera.node.app.service.mono.records.RecordsHistorian;
import com.hedera.node.app.service.mono.state.EntityCreator;
import com.hedera.node.app.service.mono.state.adapters.MerkleMapLike;
import com.hedera.node.app.service.mono.state.merkle.MerkleAccount;
import com.hedera.node.app.service.mono.state.merkle.MerkleNetworkContext;
import com.hedera.node.app.service.mono.state.merkle.MerkleStakingInfo;
import com.hedera.node.app.service.mono.state.migration.AccountStorageAdapter;
import com.hedera.node.app.service.mono.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.swirlds.merkle.map.MerkleMap;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EndOfStakingPeriodCalculatorTest {

    @Mock
    private MerkleMap<EntityNum, MerkleAccount> accounts;

    @Mock
    private MerkleMap<EntityNum, MerkleStakingInfo> stakingInfos;

    @Mock
    private MerkleNetworkContext merkleNetworkContext;

    @Mock
    private SyntheticTxnFactory syntheticTxnFactory;

    @Mock
    private RecordsHistorian recordsHistorian;

    @Mock
    private EntityCreator creator;

    @Mock
    private PropertySource properties;

    @Mock
    private GlobalDynamicProperties dynamicProperties;

    private EndOfStakingPeriodCalculator subject;
    private final int sumOfConsensusWeights = 500;

    @BeforeEach
    void setUp() {
        subject = new EndOfStakingPeriodCalculator(
                () -> AccountStorageAdapter.fromInMemory(MerkleMapLike.from(accounts)),
                () -> MerkleMapLike.from(stakingInfos),
                () -> merkleNetworkContext,
                syntheticTxnFactory,
                recordsHistorian,
                creator,
                properties,
                dynamicProperties);
    }

    @Test
    void skipsEndOfStakingPeriodCalcsIfStakingNotEnabled() {
        final var consensusTime = Instant.now();
        given(dynamicProperties.isStakingEnabled()).willReturn(false);

        subject.updateNodes(consensusTime);

        verify(merkleNetworkContext, never()).setTotalStakedRewardStart(anyLong());
        verify(merkleNetworkContext, never()).setTotalStakedStart(anyLong());
        verify(syntheticTxnFactory, never())
                .nodeStakeUpdate(
                        any(), anyList(), any(), anyLong(), anyLong(), anyLong(), anyLong(), anyLong(), anyLong());
    }

    @Test
    void convertsStakeValueToWeightCorrectly() {
        final var stake1 = 100_000_000L;
        final var stake2 = 123_456_789_123L;
        final var stake3 = 500_000_867_919L;
        final var stake4 = 900_000_789_111L;
        final var stake5 = 0L;
        final var totalStake = stake1 + stake2 + stake3 + stake4;
        final var updatedWeight1 = subject.calculateWeightFromStake(stake1, totalStake, sumOfConsensusWeights);
        final var updatedWeight2 = subject.calculateWeightFromStake(stake2, totalStake, sumOfConsensusWeights);
        final var updatedWeight3 = subject.calculateWeightFromStake(stake3, totalStake, sumOfConsensusWeights);
        final var updatedWeight4 = subject.calculateWeightFromStake(stake4, totalStake, sumOfConsensusWeights);
        final var updatedWeight5 = subject.calculateWeightFromStake(stake5, totalStake, sumOfConsensusWeights);
        final var totalWeight = updatedWeight1 + updatedWeight2 + updatedWeight3 + updatedWeight4 + updatedWeight5;
        assertTrue(totalWeight <= 500);
        assertEquals(1, updatedWeight1);
        assertEquals((stake2 * 500) / totalStake, updatedWeight2);
        assertEquals((stake3 * 500) / totalStake, updatedWeight3);
        assertEquals((stake4 * 500) / totalStake, updatedWeight4);
        assertEquals(0, updatedWeight5);
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
        final var weightForEqualsMin =
                subject.calculateWeightFromStake(equalsMinStake, totalStake, sumOfConsensusWeights);
        final var weightInBetween1 =
                subject.calculateWeightFromStake(stakeInBetween1, totalStake, sumOfConsensusWeights);
        final var weightInBetween2 =
                subject.calculateWeightFromStake(stakeInBetween2, totalStake, sumOfConsensusWeights);
        final var weightForEqualsMax =
                subject.calculateWeightFromStake(stakeEqualsMax, totalStake, sumOfConsensusWeights);
        final var weightForZeroStake = subject.calculateWeightFromStake(zeroStake, totalStake, sumOfConsensusWeights);
        final var totalWeight =
                weightForEqualsMin + weightInBetween1 + weightInBetween2 + weightForEqualsMax + weightForZeroStake;
        // total of all weights should be less than or equal to 500
        assertTrue(totalWeight <= 500);
        assertEquals(1, weightForEqualsMin);
        assertEquals((stakeInBetween1 * 500) / totalStake, weightInBetween1);
        assertEquals((stakeInBetween2 * 500) / totalStake, weightInBetween2);
        assertEquals((stakeEqualsMax * 500) / totalStake, weightForEqualsMax);
        assertEquals(0, weightForZeroStake);

        final var scaledStake1 =
                subject.scaleUpWeightToStake(weightForEqualsMin, minStake, maxStake, totalStake, sumOfConsensusWeights);
        final var scaledStake2 =
                subject.scaleUpWeightToStake(weightInBetween1, minStake, maxStake, totalStake, sumOfConsensusWeights);
        final var scaledStake3 =
                subject.scaleUpWeightToStake(weightInBetween2, minStake, maxStake, totalStake, sumOfConsensusWeights);
        final var scaledStake4 =
                subject.scaleUpWeightToStake(weightForEqualsMax, minStake, maxStake, totalStake, sumOfConsensusWeights);
        final var scaledStake5 =
                subject.scaleUpWeightToStake(weightForZeroStake, minStake, maxStake, totalStake, sumOfConsensusWeights);

        // calculate scaled weight based on the max weight allocated and max stake of all nodes
        final var maxWeight = Math.max(
                weightForEqualsMin, Math.max(weightInBetween1, Math.max(weightInBetween2, weightForEqualsMax)));
        final var expectedEqualScaledStake =
                ((maxStake - minStake) * (weightInBetween2 - 1)) / (maxWeight - 1) + minStake;
        // stake equals min stake
        assertEquals(equalsMinStake, scaledStake1);
        // Both these fall in the same bucket since their weight is the same. So, they get same scaled weight
        assertEquals(expectedEqualScaledStake, scaledStake2);
        assertEquals(expectedEqualScaledStake, scaledStake3);
        // stake equals max stake, will return max stake
        assertEquals(stakeEqualsMax, scaledStake4);
        // stake equals zero, will return zero
        assertEquals(zeroStake, scaledStake5);
    }

    @Test
    void calculatesNewTotalStakesAsExpected() {
        final var consensusTime = Instant.now();
        final var balance_800 = 100_000_000_000L;
        final var account_800 = mock(MerkleAccount.class);

        given(dynamicProperties.isStakingEnabled()).willReturn(true);
        given(dynamicProperties.sumOfConsensusWeights()).willReturn(500);
        given(dynamicProperties.maxStakeRewarded()).willReturn(Long.MAX_VALUE);
        // Total period rewards for this test is 100 tinybars, and there are 10 hbar staked;
        // so the reward rate is 100 / 10 = 10 tinybars per hbar
        given(dynamicProperties.stakingPerHbarRewardRate()).willReturn(10L);
        given(properties.getLongProperty(ACCOUNTS_STAKING_REWARD_ACCOUNT)).willReturn(stakingRewardAccount);
        given(accounts.get(EntityNum.fromInt(800))).willReturn(account_800);
        given(account_800.getBalance()).willReturn(balance_800);
        given(stakingInfos.keySet()).willReturn(Set.of(nodeNum1, nodeNum2, nodeNum3));
        given(stakingInfos.getForModify(nodeNum1)).willReturn(stakingInfo1);
        given(stakingInfos.getForModify(nodeNum2)).willReturn(stakingInfo2);
        given(stakingInfos.getForModify(nodeNum3)).willReturn(stakingInfo3);
        given(merkleNetworkContext.getTotalStakedRewardStart()).willReturn(1_000_000_000L);

        assertEquals(0, stakingInfo1.getWeight());
        assertEquals(0, stakingInfo1.getWeight());
        assertEquals(0, stakingInfo1.getWeight());

        subject.updateNodes(consensusTime);

        verify(merkleNetworkContext).setTotalStakedRewardStart(stakeToReward1 + stakeToReward2 + stakeToReward3);
        verify(merkleNetworkContext).setTotalStakedStart(1300L);
        assertEquals(800L, stakingInfo1.getStake());
        assertEquals(500L, stakingInfo2.getStake());
        assertEquals(0L, stakingInfo3.getStake());
        assertEquals(0L, stakingInfo1.getUnclaimedStakeRewardStart());
        assertEquals(0L, stakingInfo2.getUnclaimedStakeRewardStart());
        assertEquals(0L, stakingInfo3.getUnclaimedStakeRewardStart());
        assertArrayEquals(new long[] {14, 6, 5}, stakingInfo1.getRewardSumHistory());
        assertArrayEquals(new long[] {11, 1, 1}, stakingInfo2.getRewardSumHistory());
        assertArrayEquals(new long[] {3, 3, 1}, stakingInfo3.getRewardSumHistory());
        assertEquals(307, stakingInfo1.getWeight());
        assertEquals(192, stakingInfo2.getWeight());
        assertEquals(0, stakingInfo3.getWeight());
        assertTrue(stakingInfo1.getWeight() + stakingInfo2.getWeight() + stakingInfo3.getWeight() <= 500);
    }

    @Test
    void zeroWholeHbarsStakedCaseWorks() {
        final var consensusTime = Instant.now();
        final var balance_800 = 100_000_000_000L;
        final var account_800 = mock(MerkleAccount.class);

        given(dynamicProperties.isStakingEnabled()).willReturn(true);
        given(dynamicProperties.sumOfConsensusWeights()).willReturn(500);
        given(dynamicProperties.maxStakeRewarded()).willReturn(Long.MAX_VALUE);
        // Total period rewards for this test is 100 tinybars, and there are 10 hbar staked;
        // so the reward rate is 100 / 10 = 10 tinybars per hbar
        given(dynamicProperties.stakingPerHbarRewardRate()).willReturn(10L);
        given(properties.getLongProperty(ACCOUNTS_STAKING_REWARD_ACCOUNT)).willReturn(stakingRewardAccount);
        given(accounts.get(EntityNum.fromInt(800))).willReturn(account_800);
        given(account_800.getBalance()).willReturn(balance_800);
        given(stakingInfos.keySet()).willReturn(Set.of(nodeNum1, nodeNum2, nodeNum3));
        stakingInfo1.setStakeRewardStart(0);
        given(stakingInfos.getForModify(nodeNum1)).willReturn(stakingInfo1);
        stakingInfo2.setStakeRewardStart(0);
        given(stakingInfos.getForModify(nodeNum2)).willReturn(stakingInfo2);
        stakingInfo3.setStakeRewardStart(0);
        given(stakingInfos.getForModify(nodeNum3)).willReturn(stakingInfo3);
        given(merkleNetworkContext.getTotalStakedRewardStart()).willReturn(0L);

        subject.updateNodes(consensusTime);

        assertArrayEquals(new long[] {6, 6, 5}, stakingInfo1.getRewardSumHistory());
        assertArrayEquals(new long[] {1, 1, 1}, stakingInfo2.getRewardSumHistory());
        assertArrayEquals(new long[] {3, 3, 1}, stakingInfo3.getRewardSumHistory());
    }

    @Test
    void calculatesMidnightTimeCorrectly() {
        final var consensusSecs = 1653660350L;
        final var consensusNanos = 12345L;
        final var expectedNanos = 999_999_999;
        final var consensusTime = Instant.ofEpochSecond(consensusSecs, consensusNanos);
        final var expectedMidnightTime = Timestamp.newBuilder()
                .setSeconds(1653609599L)
                .setNanos(expectedNanos)
                .build();

        assertEquals(expectedMidnightTime, subject.lastInstantOfPreviousPeriodFor(consensusTime));
    }

    final long stakingRewardAccount = 800L;
    final long minStake = 100L;
    final long maxStake = 800L;
    final long stakeToReward1 = 700L;
    final long stakeToReward2 = 300L;
    final long stakeToReward3 = 30L;
    final long stakeToNotReward1 = 300L;
    final long stakeToNotReward2 = 200L;
    final long stakeToNotReward3 = 20L;
    final long stakedRewardStart1 = 1_000L;
    final long unclaimedStakedRewardStart1 = stakedRewardStart1 / 10;
    final long stakedRewardStart2 = 700L;
    final long unclaimedStakedRewardStart2 = stakedRewardStart2 / 10;
    final long stakedRewardStart3 = 10_000L;
    final long unclaimedStakedRewardStart3 = stakedRewardStart3 / 10;
    final long stake1 = 2_000L;
    final long stake2 = 750L;
    final long stake3 = 75L;
    final long[] rewardSumHistory1 = new long[] {8, 7, 2};
    final long[] rewardSumHistory2 = new long[] {5, 5, 4};
    final long[] rewardSumHistory3 = new long[] {4, 2, 1};
    final EntityNum nodeNum1 = EntityNum.fromInt(0);
    final EntityNum nodeNum2 = EntityNum.fromInt(1);
    final EntityNum nodeNum3 = EntityNum.fromInt(2);
    final MerkleStakingInfo stakingInfo1 = new MerkleStakingInfo(
            minStake,
            maxStake,
            stakeToReward1,
            stakeToNotReward1,
            stakedRewardStart1,
            unclaimedStakedRewardStart1,
            stake1,
            rewardSumHistory1,
            0);
    final MerkleStakingInfo stakingInfo2 = new MerkleStakingInfo(
            minStake,
            maxStake,
            stakeToReward2,
            stakeToNotReward2,
            stakedRewardStart2,
            unclaimedStakedRewardStart2,
            stake2,
            rewardSumHistory2,
            0);
    final MerkleStakingInfo stakingInfo3 = new MerkleStakingInfo(
            minStake,
            maxStake,
            stakeToReward3,
            stakeToNotReward3,
            stakedRewardStart3,
            unclaimedStakedRewardStart3,
            stake3,
            rewardSumHistory3,
            0);
}
