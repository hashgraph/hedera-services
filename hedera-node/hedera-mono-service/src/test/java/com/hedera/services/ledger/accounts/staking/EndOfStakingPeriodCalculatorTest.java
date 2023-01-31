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
package com.hedera.services.ledger.accounts.staking;

import static com.hedera.services.context.properties.PropertyNames.ACCOUNTS_STAKING_REWARD_ACCOUNT;
import static com.hedera.services.context.properties.PropertyNames.STAKING_REWARD_RATE;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.context.properties.PropertySource;
import com.hedera.services.records.RecordsHistorian;
import com.hedera.services.state.EntityCreator;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleNetworkContext;
import com.hedera.services.state.merkle.MerkleStakingInfo;
import com.hedera.services.state.migration.AccountStorageAdapter;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.utils.EntityNum;
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

    @Mock private MerkleMap<EntityNum, MerkleAccount> accounts;
    @Mock private MerkleMap<EntityNum, MerkleStakingInfo> stakingInfos;
    @Mock private MerkleNetworkContext merkleNetworkContext;
    @Mock private SyntheticTxnFactory syntheticTxnFactory;
    @Mock private RecordsHistorian recordsHistorian;
    @Mock private EntityCreator creator;
    @Mock private PropertySource properties;
    @Mock private GlobalDynamicProperties dynamicProperties;

    private EndOfStakingPeriodCalculator subject;

    @BeforeEach
    void setup() {
        subject =
                new EndOfStakingPeriodCalculator(
                        () -> AccountStorageAdapter.fromInMemory(accounts),
                        () -> stakingInfos,
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
        verify(syntheticTxnFactory, never()).nodeStakeUpdate(any(), anyList(), any());
    }

    @Test
    void calculatesNewTotalStakesAsExpected() {
        final var consensusTime = Instant.now();
        final var balance_800 = 100_000_000_000L;
        final var account_800 = mock(MerkleAccount.class);

        given(dynamicProperties.isStakingEnabled()).willReturn(true);
        given(dynamicProperties.maxDailyStakeRewardThPerH()).willReturn(Long.MAX_VALUE);
        given(properties.getLongProperty(STAKING_REWARD_RATE)).willReturn(100L);
        given(properties.getLongProperty(ACCOUNTS_STAKING_REWARD_ACCOUNT))
                .willReturn(stakingRewardAccount);
        given(accounts.get(EntityNum.fromInt(800))).willReturn(account_800);
        given(account_800.getBalance()).willReturn(balance_800);
        given(stakingInfos.keySet()).willReturn(Set.of(nodeNum1, nodeNum2, nodeNum3));
        given(stakingInfos.getForModify(nodeNum1)).willReturn(stakingInfo1);
        given(stakingInfos.getForModify(nodeNum2)).willReturn(stakingInfo2);
        given(stakingInfos.getForModify(nodeNum3)).willReturn(stakingInfo3);
        given(merkleNetworkContext.getTotalStakedRewardStart()).willReturn(1_000_000_000L);

        subject.updateNodes(consensusTime);

        verify(merkleNetworkContext)
                .setTotalStakedRewardStart(stakeToReward1 + stakeToReward2 + stakeToReward3);
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
    }

    @Test
    void calculatesMidnightTimeCorrectly() {
        final var consensusSecs = 1653660350L;
        final var consensusNanos = 12345L;
        final var expectedNanos = 999_999_999;
        final var consensusTime = Instant.ofEpochSecond(consensusSecs, consensusNanos);
        final var expectedMidnightTime =
                Timestamp.newBuilder().setSeconds(1653609599L).setNanos(expectedNanos).build();

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
    final MerkleStakingInfo stakingInfo1 =
            new MerkleStakingInfo(
                    minStake,
                    maxStake,
                    stakeToReward1,
                    stakeToNotReward1,
                    stakedRewardStart1,
                    unclaimedStakedRewardStart1,
                    stake1,
                    rewardSumHistory1);
    final MerkleStakingInfo stakingInfo2 =
            new MerkleStakingInfo(
                    minStake,
                    maxStake,
                    stakeToReward2,
                    stakeToNotReward2,
                    stakedRewardStart2,
                    unclaimedStakedRewardStart2,
                    stake2,
                    rewardSumHistory2);
    final MerkleStakingInfo stakingInfo3 =
            new MerkleStakingInfo(
                    minStake,
                    maxStake,
                    stakeToReward3,
                    stakeToNotReward3,
                    stakedRewardStart3,
                    unclaimedStakedRewardStart3,
                    stake3,
                    rewardSumHistory3);
}
